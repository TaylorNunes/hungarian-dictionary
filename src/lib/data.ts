import { fold, shardChars } from './fold';
import type { EnglishRow, FormRow, Lemma, Manifest } from './types';

const DATA_URL = `${import.meta.env.BASE_URL}data/`;
const SHARD_CACHE = 'data-shards'; // must match the runtime cache name in vite.config.ts

/** The two prefix-sharded indexes: Hungarian forms and English gloss terms. */
type IndexName = 'forms' | 'en';

interface ShardIndex {
  keys: Set<string>;
  maxKeyLength: number;
  shards: Map<string, Promise<Record<string, unknown[]>>>;
}

let manifestPromise: Promise<Manifest> | null = null;
const indexes: Record<IndexName, ShardIndex> = {
  forms: { keys: new Set(), maxKeyLength: 0, shards: new Map() },
  en: { keys: new Set(), maxKeyLength: 0, shards: new Map() },
};
const lemmaShards = new Map<number, Promise<Record<string, Lemma>>>();
let tagsPromise: Promise<string[][]> | null = null;

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} loading ${url}`);
  return res.json() as Promise<T>;
}

export function getManifest(): Promise<Manifest> {
  manifestPromise ??= fetchJson<Manifest>(`${DATA_URL}manifest.json`).then((m) => {
    const lists: Record<IndexName, string[]> = { forms: m.formShards, en: m.enShards ?? [] };
    for (const name of Object.keys(lists) as IndexName[]) {
      indexes[name].keys = new Set(lists[name]);
      indexes[name].maxKeyLength = Math.max(0, ...lists[name].map((k) => k.length));
    }
    return m;
  });
  manifestPromise.catch(() => (manifestPromise = null));
  return manifestPromise;
}

function versionUrl(m: Manifest, path: string): string {
  return `${DATA_URL}${m.version}/${path}`;
}

/** The longest shard key of an index that is a prefix of the folded word, if any. */
function shardKeyFor(name: IndexName, folded: string): string | null {
  const { keys, maxKeyLength } = indexes[name];
  const chars = shardChars(folded);
  for (let n = Math.min(chars.length, maxKeyLength); n > 0; n--) {
    const key = chars.slice(0, n);
    if (keys.has(key)) return key;
  }
  return null;
}

async function loadShard<Row>(name: IndexName, key: string): Promise<Record<string, Row[]>> {
  const m = await getManifest();
  const { shards } = indexes[name];
  let p = shards.get(key);
  if (!p) {
    p = fetchJson<Record<string, unknown[]>>(versionUrl(m, `${name}/${key}.json`));
    p.catch(() => shards.delete(key));
    shards.set(key, p);
  }
  return p as Promise<Record<string, Row[]>>;
}

async function lookup<Row>(name: IndexName, folded: string): Promise<Row[]> {
  await getManifest();
  const key = shardKeyFor(name, folded);
  if (!key) return [];
  return (await loadShard<Row>(name, key))[folded] ?? [];
}

/** All Hungarian form rows whose folded spelling equals `folded`. */
export function lookupFolded(folded: string): Promise<FormRow[]> {
  return lookup<FormRow>('forms', folded);
}

/** Hungarian lemmas whose glosses contain the folded English term, best first. */
export function lookupEnglish(folded: string): Promise<EnglishRow[]> {
  return lookup<EnglishRow>('en', folded);
}

/** Headwords in the same shard that start with `folded` (for suggestions). */
export async function suggest(folded: string, limit = 8): Promise<string[]> {
  await getManifest();
  const key = shardKeyFor('forms', folded);
  if (!key || folded.length < key.length) return [];
  const shard = await loadShard<FormRow>('forms', key);
  const out: string[] = [];
  for (const [k, rows] of Object.entries(shard)) {
    if (k === folded || !k.startsWith(folded)) continue;
    const head = rows.find((r) => r[2] === 0);
    if (head) out.push(head[0]);
  }
  return out.sort((a, b) => a.length - b.length || a.localeCompare(b, 'hu')).slice(0, limit);
}

export async function getLemma(id: number): Promise<Lemma | undefined> {
  const m = await getManifest();
  const n = Math.floor(id / m.lemmasPerShard);
  let p = lemmaShards.get(n);
  if (!p) {
    p = fetchJson<Record<string, Lemma>>(versionUrl(m, `lemmas/${n}.json`));
    p.catch(() => lemmaShards.delete(n));
    lemmaShards.set(n, p);
  }
  return (await p)[String(id)];
}

/**
 * The entry for a word page. Tries the lemma id from the link first; ids can shift when the data is
 * rebuilt, so if that entry no longer matches, falls back to the word itself (same part of speech first).
 */
export async function findEntry(word: string, pos: string, id?: number): Promise<{ id: number; lemma: Lemma } | null> {
  if (id !== undefined) {
    const lemma = await getLemma(id).catch(() => undefined);
    if (lemma && lemma.w === word && lemma.pos === pos) return { id, lemma };
  }
  const rows = await lookupFolded(fold(word));
  const ids = [...new Set(rows.filter((r) => r[0] === word && r[2] === 0).map((r) => r[1]))].sort((a, b) => a - b);
  const lemmas = await Promise.all(ids.map((i) => getLemma(i)));
  const index = lemmas.findIndex((l) => l?.pos === pos);
  const pick = index >= 0 ? index : lemmas.findIndex(Boolean);
  return pick >= 0 ? { id: ids[pick], lemma: lemmas[pick]! } : null;
}

/** Tag sets, indexed by the numbers in form rows and tables; each is a list of tags. */
export function getTags(): Promise<string[][]> {
  tagsPromise ??= getManifest()
    .then((m) => fetchJson<string[]>(versionUrl(m, 'tags.json')))
    .then((list) => list.map((t) => (t ? t.split(' ') : [])));
  tagsPromise.catch(() => (tagsPromise = null));
  return tagsPromise;
}

/** Every data file for the current version, for the offline download. */
export async function allDataUrls(): Promise<string[]> {
  const m = await getManifest();
  const urls = [versionUrl(m, 'tags.json')];
  for (const k of m.formShards) urls.push(versionUrl(m, `forms/${k}.json`));
  for (const k of m.enShards ?? []) urls.push(versionUrl(m, `en/${k}.json`));
  for (let i = 0; i < m.lemmaShards; i++) urls.push(versionUrl(m, `lemmas/${i}.json`));
  return urls;
}

/**
 * Store every data file in the service worker's shard cache.
 * Files already cached are skipped, so an interrupted download can resume.
 */
export async function downloadAll(onProgress: (done: number, total: number) => void, signal?: AbortSignal): Promise<void> {
  const urls = await allDataUrls();
  const cache = await caches.open(SHARD_CACHE);
  const absolute = urls.map((u) => new URL(u, location.href).href);
  const cached = new Set((await cache.keys()).map((r) => r.url));
  const todo = absolute.filter((u) => !cached.has(u));
  let done = urls.length - todo.length;
  onProgress(done, urls.length);
  const worker = async () => {
    while (todo.length) {
      if (signal?.aborted) throw new DOMException('Cancelled', 'AbortError');
      const url = todo.shift()!;
      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error(`${res.status} loading ${url}`);
      await cache.put(url, res);
      onProgress(++done, urls.length);
    }
  };
  await Promise.all(Array.from({ length: 6 }, worker));
}

/** How many of the current version's files are cached for offline use. */
export async function offlineStatus(): Promise<{ cached: number; total: number }> {
  if (!('caches' in window)) return { cached: 0, total: 0 };
  const urls = (await allDataUrls()).map((u) => new URL(u, location.href).href);
  const cache = await caches.open(SHARD_CACHE);
  const cached = new Set((await cache.keys()).map((r) => r.url));
  return { cached: urls.filter((u) => cached.has(u)).length, total: urls.length };
}

/** Drop cached shards from older data versions. */
export async function pruneOldShards(): Promise<void> {
  if (!('caches' in window)) return;
  const m = await getManifest();
  const cache = await caches.open(SHARD_CACHE);
  const current = new URL(`${DATA_URL}${m.version}/`, location.href).href;
  for (const req of await cache.keys()) {
    if (!req.url.startsWith(current)) await cache.delete(req);
  }
}
