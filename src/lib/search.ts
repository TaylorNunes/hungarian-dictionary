import { getLemma, getTags, lookupEnglish, lookupFolded, lookupPrefix } from './data';
import { deinflect, normalizeEnglish } from './english';
import { fold, hasAccents } from './fold';
import { frequencyBonus } from './frequency';
import { candidates, type Step } from './stemmer';
import { describe, isBaseForm, type Part } from './tags';
import type { FormRow, HeadRow, Lemma } from './types';

export interface Analysis {
  /** The form as found in the dictionary. */
  form: string;
  parts: Part[];
}

export interface Result {
  lemmaId: number;
  lemma: Lemma;
  analyses: Analysis[];
  /** Matched with the accents exactly as typed. */
  exact: boolean;
  /** Found through the suffix stripper (or English de-inflection) rather than directly. */
  guessed: boolean;
  /** For English lookups: index of the sense whose gloss matched. */
  sense?: number;
}

export interface SearchResponse {
  query: string;
  results: Result[];
  /** Words of a multi-word query, so each can be looked up. */
  tokens: string[];
}

const MAX_RESULTS = 12;
const MAX_STEM_CANDIDATES = 80;
const TOKEN_RE = /[\p{L}]+(?:-[\p{L}]+)*/gu;

interface Hit {
  row: FormRow;
  steps: Step[];
}

export function cleanQuery(q: string): string {
  return q.normalize('NFC').trim().replace(/^[\p{P}\s]+|[\p{P}\s]+$/gu, '').replace(/\s+/g, ' ');
}

async function hitsFor(word: string, accentsTyped: boolean, steps: Step[] = []): Promise<Hit[]> {
  const lower = word.toLowerCase();
  const rows = await lookupFolded(fold(word));
  const exact = rows.filter((r) => r[0].toLowerCase() === lower);
  const chosen = accentsTyped && exact.length ? exact : rows;
  return chosen.map((row) => ({ row, steps }));
}

async function stemmedHits(word: string, accentsTyped: boolean): Promise<Hit[]> {
  let bestLevel = Infinity;
  const hits: Hit[] = [];
  for (const c of candidates(word).slice(0, MAX_STEM_CANDIDATES)) {
    if (c.steps.length > bestLevel) break;
    const found = await hitsFor(c.stem, accentsTyped, c.steps);
    if (found.length) {
      hits.push(...found);
      bestLevel = c.steps.length;
    }
  }
  return hits;
}

export async function search(raw: string): Promise<SearchResponse> {
  const query = cleanQuery(raw);
  const tokens = query.includes(' ') ? [...new Set(query.match(TOKEN_RE) ?? [])] : [];
  if (!query) return { query, results: [], tokens };

  const accentsTyped = hasAccents(query);
  let hits = await hitsFor(query, accentsTyped);
  let guessed = false;
  if (!hits.length && !tokens.length) {
    hits = await stemmedHits(query, accentsTyped);
    guessed = hits.length > 0;
  }

  const tagSets = await getTags();
  const lower = query.toLowerCase();

  // Group rows by lemma; rank before fetching lemma records.
  const byLemma = new Map<number, { hits: Hit[]; exact: boolean; head: boolean; steps: number }>();
  for (const h of hits) {
    const [form, lemmaId, tagIdx] = h.row;
    const g = byLemma.get(lemmaId) ?? { hits: [], exact: false, head: false, steps: h.steps.length };
    g.hits.push(h);
    if (form.toLowerCase() === lower) g.exact = true;
    if (tagIdx === 0) g.head = true;
    g.steps = Math.min(g.steps, h.steps.length);
    byLemma.set(lemmaId, g);
  }
  const preRanked = [...byLemma.entries()]
    .sort(([, a], [, b]) => a.steps - b.steps || Number(b.exact) - Number(a.exact) || Number(b.head) - Number(a.head))
    .slice(0, MAX_RESULTS * 2);

  const results: (Result & { score: number })[] = [];
  await Promise.all(
    preRanked.map(async ([lemmaId, g]) => {
      const lemma = await getLemma(lemmaId);
      if (!lemma) return;
      const analyses: Analysis[] = [];
      const seen = new Set<string>();
      for (const { row, steps } of g.hits) {
        const [form, , tagIdx] = row;
        const tags = tagSets[tagIdx] ?? [];
        const prefix = steps.filter((s) => s.prefix).map((s) => s.part);
        const suffix = steps.filter((s) => !s.prefix).map((s) => s.part);
        // For the dictionary form itself keep only usage labels (rare, dialectal…), which have no hint.
        const own = isBaseForm(tags) ? describe(tags).filter((p) => !p.hint) : describe(tags);
        const parts = [...prefix, ...own, ...suffix];
        if (!parts.length && form === lemma.w) continue;
        const key = form + '|' + parts.map((p) => p.label).join('+');
        if (seen.has(key)) continue;
        seen.add(key);
        analyses.push({ form, parts });
      }
      let score = 0;
      if (g.exact) score += 100;
      if (g.head) score += 10;
      score -= g.steps * 30;
      score += Math.min(lemma.s.length, 6) + (lemma.t ? 3 : 0) + (lemma.ex ? 2 : 0) + frequencyBonus(lemma.fr);
      if (lemma.pos === 'name') score -= 6;
      if (lemma.pos === 'character') score -= 15;
      if (['suffix', 'prefix', 'infix'].includes(lemma.pos) && !query.startsWith('-')) score -= 8;
      if (lemma.s.every((s) => /\b(form|spelling|misspelling) of\b/.test(s.g))) score -= 4;
      results.push({ lemmaId, lemma, analyses, exact: g.exact, guessed, score });
    }),
  );
  results.sort((a, b) => b.score - a.score || a.lemmaId - b.lemmaId);

  return { query, results: results.slice(0, MAX_RESULTS), tokens };
}

// ----------------------------------------------------------------------------- English → Hungarian

export interface EnglishResponse {
  /** The English term that matched (after de-inflection), or the normalised query. */
  term: string;
  results: Result[];
  /** The query itself was in the index (not reached by de-inflection). */
  exact: boolean;
  /** The best match has the term leading its gloss ("house" in "house, building"). */
  strong: boolean;
}

export async function searchEnglish(raw: string): Promise<EnglishResponse> {
  const query = normalizeEnglish(cleanQuery(raw));
  const empty = { term: query, results: [], exact: false, strong: false };
  if (!query) return empty;

  let term = query;
  let rows = await lookupEnglish(fold(term));
  if (!rows.length) {
    for (const base of deinflect(query)) {
      rows = await lookupEnglish(fold(base));
      if (rows.length) {
        term = base;
        break;
      }
    }
  }
  if (!rows.length) return empty;

  const exact = term === query;
  const top = rows.slice(0, MAX_RESULTS);
  const lemmas = await Promise.all(top.map(([id]) => getLemma(id)));
  const results: Result[] = [];
  top.forEach(([lemmaId, sense], i) => {
    const lemma = lemmas[i];
    if (lemma) results.push({ lemmaId, lemma, analyses: [], exact, guessed: !exact, sense });
  });
  return { term, results, exact, strong: exact && rows[0][2] === 0 };
}

// ----------------------------------------------------------------------------- both directions

export interface Section {
  lang: 'hu' | 'en';
  /** For 'en': the English term that matched. */
  term: string;
  results: Result[];
}

export interface CombinedResponse {
  query: string;
  sections: Section[];
  tokens: string[];
  /** Other headwords starting or ending with the query, most common first. */
  partial: PartialMatch[];
}

function glossMentions(lemma: Lemma, term: string): boolean {
  const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(`(^|[^\\p{L}])${escaped}($|[^\\p{L}])`, 'iu');
  return lemma.s.some((s) => re.test(s.g));
}

/**
 * Order the Hungarian and English sections: the stronger match goes first, Hungarian on a tie.
 * Hungarian is strong when the query is a dictionary form as typed, English when the query
 * leads a gloss. Pure, so the rules are unit-tested in search.test.ts.
 */
export function rankSections(hu: SearchResponse, en: EnglishResponse | null): Section[] {
  const huGuessed = hu.results.length > 0 && hu.results[0].guessed;
  const huExact = hu.results.some((r) => r.exact && !r.guessed);
  // A Hungarian stemmer guess is noise when the word is English ("houses").
  const keepHu = !(huGuessed && en?.results.length);
  // A Hungarian word glossed with the same English word is a loan ("house" = house music): English first.
  const loan = huExact && !!en?.strong && glossMentions(hu.results[0].lemma, normalizeEnglish(hu.query));
  // Exact Hungarian 3; accent-folded only (car → cár) 1.5, below an exact English term; stemmer guess 1.
  const huScore = !keepHu || !hu.results.length ? 0 : huExact && !loan ? 3 : huGuessed ? 1 : 1.5;
  const enScore = !en?.results.length ? 0 : en.strong ? 3 : en.exact ? 2 : 1;

  const huSection: Section = { lang: 'hu', term: hu.query, results: keepHu ? hu.results : [] };
  const enSection: Section = { lang: 'en', term: en?.term ?? '', results: en?.results ?? [] };
  const ordered = huScore >= enScore ? [huSection, enSection] : [enSection, huSection];
  return ordered.filter((sec) => sec.results.length);
}

/**
 * Look the query up as Hungarian and as English, like Takoboto does for Latin-script input.
 * Accented letters mean Hungarian, so English is only tried for unaccented queries.
 */
export async function searchBoth(raw: string): Promise<CombinedResponse> {
  const [hu, en, partial] = await Promise.all([
    search(raw),
    hasAccents(cleanQuery(raw)) ? Promise.resolve(null) : searchEnglish(raw),
    partialRows(raw),
  ]);
  const sections = rankSections(hu, en);
  const shown = new Set(sections.flatMap((sec) => sec.results.map((r) => r.lemmaId)));
  return { query: hu.query, sections, tokens: hu.tokens, partial: rankPartial(hu.query, ...partial, shown) };
}

// ----------------------------------------------------------------------------- partial matches

export interface PartialMatch {
  word: string;
  lemmaId: number;
  pos: string;
  /** Frequency rank, 0 when unranked. */
  rank: number;
  gloss: string;
  /** Where the query matched, for highlighting. */
  start: boolean;
  end: boolean;
}

const MAX_PARTIAL = 200;
const MIN_PARTIAL_LETTERS = 2;

/** The folded query to match headwords against, or null when it is too short for partial matches. */
export function partialKey(raw: string): string | null {
  const folded = fold(cleanQuery(raw));
  return (folded.match(/\p{L}/gu) ?? []).length >= MIN_PARTIAL_LETTERS ? folded : null;
}

/** Headwords starting with the query and headwords ending with it; empty for short queries or on a load error. */
async function partialRows(raw: string): Promise<[starts: HeadRow[], ends: HeadRow[]]> {
  const folded = partialKey(raw);
  if (!folded) return [[], []];
  // An extra: never let it hide the main results (e.g. offline with these shards not downloaded).
  return Promise.all([lookupPrefix('starts', folded), lookupPrefix('ends', [...folded].reverse().join(''))]).catch(
    () => [[], []] as [HeadRow[], HeadRow[]],
  );
}

/**
 * Merge starts-with and ends-with rows into one list, weighting both sides the same: common words
 * first by frequency rank, then unranked ones by length and alphabet. Drops the query's own
 * headword and lemmas listed in `exclude`. With accents typed, the accents must match as well.
 */
export function rankPartial(query: string, starts: HeadRow[], ends: HeadRow[], exclude: Set<number> = new Set()): PartialMatch[] {
  const lower = query.toLowerCase();
  const folded = fold(query);
  const accents = hasAccents(query);
  const byId = new Map<number, PartialMatch>();
  const add = (rows: HeadRow[], side: 'start' | 'end') => {
    for (const [word, lemmaId, pos, rank, gloss] of rows) {
      if (exclude.has(lemmaId) || fold(word) === folded) continue;
      const w = word.toLowerCase();
      if (accents && !(side === 'start' ? w.startsWith(lower) : w.endsWith(lower))) continue;
      const m = byId.get(lemmaId) ?? { word, lemmaId, pos, rank, gloss, start: false, end: false };
      m[side] = true;
      byId.set(lemmaId, m);
    }
  };
  add(starts, 'start');
  add(ends, 'end');
  return [...byId.values()]
    .sort(
      (a, b) =>
        (a.rank || Infinity) - (b.rank || Infinity) ||
        a.word.length - b.word.length ||
        a.word.localeCompare(b.word, 'hu') ||
        a.lemmaId - b.lemmaId,
    )
    .slice(0, MAX_PARTIAL);
}
