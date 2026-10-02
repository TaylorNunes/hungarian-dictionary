import { getLemma, getTags, lookupFolded, suggest } from './data';
import { fold, hasAccents } from './fold';
import { candidates, type Step } from './stemmer';
import { describe, isBaseForm, type Part } from './tags';
import type { FormRow, Lemma } from './types';

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
  /** Found through the suffix stripper rather than the tables. */
  guessed: boolean;
}

export interface SearchResponse {
  query: string;
  results: Result[];
  /** Words of a multi-word query, so each can be looked up. */
  tokens: string[];
  suggestions: string[];
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
  if (!query) return { query, results: [], tokens, suggestions: [] };

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
      if (g.head) score += 20;
      score -= g.steps * 30;
      score += Math.min(lemma.s.length, 6) + (lemma.t ? 3 : 0) + (lemma.ex ? 2 : 0);
      if (lemma.pos === 'name') score -= 6;
      if (lemma.pos === 'character') score -= 15;
      if (['suffix', 'prefix', 'infix'].includes(lemma.pos) && !query.startsWith('-')) score -= 8;
      if (lemma.s.every((s) => /\b(form|spelling|misspelling) of\b/.test(s.g))) score -= 4;
      results.push({ lemmaId, lemma, analyses, exact: g.exact, guessed, score });
    }),
  );
  results.sort((a, b) => b.score - a.score || a.lemmaId - b.lemmaId);

  const suggestions = results.length ? [] : await suggest(fold(query));
  return { query, results: results.slice(0, MAX_RESULTS), tokens, suggestions };
}
