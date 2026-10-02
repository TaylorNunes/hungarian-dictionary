import { shardChars } from './fold';
import type { FormRow, Lemma, Manifest } from './types';

const DATA_URL = `${import.meta.env.BASE_URL}data/`;
const SHARD_CACHE = 'data-shards'; // must match the runtime cache name in vite.config.ts

let manifestPromise: Promise<Manifest> | null = null;
let shardKeys: Set<string> | null = null;
let maxKeyLength = 0;
const formShards = new Map<string, Promise<Record<string, FormRow[]>>>();
const lemmaShards = new Map<number, Promise<Record<string, Lemma>>>();
let tagsPromise: Promise<string[][]> | null = null;

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} loading ${url}`);
  return res.json() as Promise<T>;
}

export function getManifest(): Promise<Manifest> {
  manifestPromise ??= fetchJson<Manifest>(`${DATA_URL}manifest.json`).then((m) => {
    shardKeys = new Set(m.formShards);
    maxKeyLength = Math.max(...m.formShards.map((k) => k.length));
    return m;
  });
  manifestPromise.catch(() => (manifestPromise = null));
  return manifestPromise;
}

function versionUrl(m: Manifest, path: string): string {
  return `${DATA_URL}${m.version}/${path}`;
}

/** The longest shard key that is a prefix of the folded word, if any. */
function shardKeyFor(folded: string): string | null {
  const chars = shardChars(folded);
  for (let n = Math.min(chars.length, maxKeyLength); n > 0; n--) {
    const key = chars.slice(0, n);
    if (shardKeys?.has(key)) return key;
  }
  return null;
}

async function loadFormShard(key: string): Promise<Record<string, FormRow[]>> {
  const m = await getManifest();
  let p = formShards.get(key);
  if (!p) {
    p = fetchJson<Record<string, FormRow[]>>(versionUrl(m, `forms/${key}.json`));
    p.catch(() => formShards.delete(key));
    formShards.set(key, p);
  }
  return p;
}

/** All index rows whose folded spelling equals `folded`. */
export async function lookupFolded(folded: string): Promise<FormRow[]> {
  await getManifest();
  const key = shardKeyFor(folded);
  if (!key) return [];
  const shard = await loadFormShard(key);
  return shard[folded] ?? [];
}

/** Headwords in the same shard that start with `folded` (for suggestions). */
export async function suggest(folded: string, limit = 8): Promise<string[]> {
  await getManifest();
  const key = shardKeyFor(folded);
  if (!key || folded.length < key.length) return [];
  const shard = await loadFormShard(key);
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
