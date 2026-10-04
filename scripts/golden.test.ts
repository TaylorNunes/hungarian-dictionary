/**
 * Golden results for the Android port: what the web search returns for each parity query, written
 * to $GOLDEN_OUT (default android/build/golden.json). fetch() is stubbed to read public/data.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { expect, it, vi } from 'vitest';
import { getManifest, getTags } from '../src/lib/data';
import { searchBoth, type Result } from '../src/lib/search';
import { buildSections } from '../src/lib/tags';

const ROOT = join(import.meta.dirname, '..');
const OUT = process.env.GOLDEN_OUT ?? join(ROOT, 'android/build/golden.json');

vi.stubGlobal('fetch', async (url: string) => {
  const path = join(ROOT, 'public', decodeURIComponent(new URL(url, 'http://local/').pathname));
  try {
    const body = readFileSync(path);
    return new Response(body, { status: 200 });
  } catch {
    return new Response('not found', { status: 404 });
  }
});

const result = (r: Result) => ({
  lemmaId: r.lemmaId,
  word: r.lemma.w,
  exact: r.exact,
  guessed: r.guessed,
  sense: r.sense ?? null,
  analyses: r.analyses.map((a) => ({ form: a.form, parts: a.parts.map((p) => [p.label, p.hint ?? null]) })),
});

it('writes golden results', async () => {
  const manifest = await getManifest();
  const tagSets = await getTags();
  const queries: string[] = JSON.parse(readFileSync(join(ROOT, 'scripts/fixtures/parity_queries.json'), 'utf8'));
  const cases = [];
  for (const query of queries) {
    const r = await searchBoth(query);
    const first = r.sections.find((s) => s.lang === 'hu')?.results[0];
    cases.push({
      query,
      cleaned: r.query,
      tokens: r.tokens,
      sections: r.sections.map((s) => ({ lang: s.lang, term: s.term, results: s.results.map(result) })),
      partial: r.partial.map((p) => [p.lemmaId, p.start, p.end]),
      // The word page's inflection tables, for the first Hungarian result.
      tables: first?.lemma.t ? { lemmaId: first.lemmaId, sections: buildSections(first.lemma.t, tagSets) } : null,
    });
  }
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, JSON.stringify({ version: manifest.version, cases }));
  expect(cases).toHaveLength(queries.length);
});
