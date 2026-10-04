/**
 * scripts/fixtures/core_cases.json: inputs and expected outputs of the pure functions, checked here
 * and by the Android port (android/core: CoreCasesTest), so the two stay in step.
 * After changing one of these functions: UPDATE_FIXTURES=1 npx vitest run scripts/core_cases.test.ts
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, it } from 'vitest';
import { deinflect, normalizeEnglish } from '../src/lib/english';
import { commonness, frequencyBonus } from '../src/lib/frequency';
import { emphasisRuns, labelName } from '../src/lib/glossary';
import { ankiTsv, type SavedWord } from '../src/lib/saved.svelte';
import { cleanQuery, partialKey, rankPartial } from '../src/lib/search';
import { candidates } from '../src/lib/stemmer';
import { describe, isBaseForm } from '../src/lib/tags';
import type { HeadRow } from '../src/lib/types';

const ROOT = join(import.meta.dirname, '..');
const FILE = join(ROOT, 'scripts/fixtures/core_cases.json');

const STEMMER = ['könyveimben', 'házzal', 'busszal', 'házzá', 'almát', 'kefében', 'megláttam', 'tudod-e', 'barátaimmal',
  'szépségét', 'otthontalan', 'városbeli', 'kanálnyi', 'Kovácsék', 'Péteré', 'budapesti', 'szemüveges', 'elmentünk',
  'visszajöttek', 'meggyszörp', 'asszonnyal', 'hosszabbá', 'ab', 'a', 'ő'];
const ENGLISH = ['went', 'children', 'running', 'stopped', 'cities', 'knives', 'wives', 'bigger', 'happiest', 'quickly',
  "dog's", 'running shoes', 'houses', 'boxes', 'tried', 'baked', 'making', 'larger', 'ox', 'glass', 'hopping', 'is'];
const NORMALIZE = ['To See', '  the   House ', 'a book', 'an apple', '"hello!"', '...', 'TO', 'to', 'theatre', 'Café'];
const CLEAN = ['  ház  ', '?ház!', '„alma"', 'Látom   a\tházat', ' ház ', '﻿ház', '-ság', '', '...',
  'x — y', 'ház.\n', 'é-é'];
const PARTIAL_KEYS = ['h', 'ha', 'ház', ' -h ', 'é', 'éé', '12', 'a1', 'Ő'];
const RANKS = [0, 1, 2, 10, 999, 1000, 1001, 4999, 5000, 5001, 12345, 1234567];
const EMPHASIS = ['Plain text.', 'The verb *lát* "to see".', '*a* and *b*', '**', 'x *y'];
const LABELS = ['not-comparable', 'transitive', 'object-second-person'];

const head = (w: string, id: number, rank = 0): HeadRow => [w, id, 'noun', rank, ''];
const PARTIAL = [
  { query: 'ház', starts: [head('háztartás', 2, 3000), head('házas', 1, 900), head('házikó', 6), head('ház', 9, 245)],
    ends: [head('bérház', 3, 2000), head('tűzoltóház', 4), head('családi ház', 5, 400)], exclude: [] },
  { query: 'kar', starts: [head('karó', 1), head('kárpit', 2), head('Karcag', 3), head('karácsony', 4, 2000)],
    ends: [head('akar', 5, 50), head('kár', 6, 3000)], exclude: [5] },
  { query: 'kár', starts: [head('karó', 1), head('kárpit', 2)], ends: [head('akar', 3), head('lekár', 4)], exclude: [] },
  { query: 'ha', starts: [head('haha', 1), head('hab', 2), head('hä', 3), head('Hab', 4)], ends: [head('haha', 1)], exclude: [] },
  { query: 'os', starts: [head('ós', 1), head('oszt', 2), head('ősz', 3), head('öszvér', 4), head('osztály', 5)], ends: [], exclude: [] },
];

const SAVED: SavedWord[] = [
  { lemmaId: 1, word: 'ház', pos: 'noun', meaning: 'house; home', example: ['Látom a házat.', 'I see the house.'], fr: 245, added: 1 },
  { lemmaId: 2, word: 'kar\tóra', pos: 'noun', meaning: 'arm\nclock', lookedUp: 'karórát', fr: 4999, added: 2 },
  { lemmaId: 3, word: 'ritka', pos: 'adj', meaning: 'rare', added: 3 },
];

const parts = (ps: { label: string; hint?: string }[]) => ps.map((p) => [p.label, p.hint ?? null]);

function compute(describeInputs: string[]) {
  return {
    stemmer: STEMMER.map((input) => ({
      input,
      candidates: candidates(input).slice(0, 80).map((c) => ({
        stem: c.stem,
        steps: c.steps.map((s) => [s.piece, s.part.label, s.part.hint ?? null, !!s.prefix]),
      })),
    })),
    deinflect: ENGLISH.map((input) => ({ input, output: deinflect(input) })),
    normalizeEnglish: NORMALIZE.map((input) => ({ input, output: normalizeEnglish(input) })),
    cleanQuery: CLEAN.map((input) => ({ input, output: cleanQuery(input) })),
    partialKey: PARTIAL_KEYS.map((input) => ({ input, output: partialKey(input) })),
    frequency: RANKS.map((rank) => ({ rank, bonus: frequencyBonus(rank), commonness: commonness(rank) })),
    describe: describeInputs.map((tags) => {
      const list = tags ? tags.split(' ') : [];
      return { tags, parts: parts(describe(list)), base: isBaseForm(list) };
    }),
    emphasisRuns: EMPHASIS.map((input) => ({ input, output: emphasisRuns(input) })),
    labelName: LABELS.map((input) => ({ input, output: labelName(input) })),
    rankPartial: PARTIAL.map((c) => ({
      ...c,
      output: rankPartial(c.query, c.starts, c.ends, new Set(c.exclude)).map((m) => [m.lemmaId, m.start, m.end]),
    })),
    ankiTsv: { saved: SAVED, output: ankiTsv(SAVED) },
  };
}

it('core_cases.json matches the TypeScript functions', () => {
  if (process.env.UPDATE_FIXTURES) {
    // Tag sets come from the built data when present, so every real combination is covered.
    const tagsFile = (() => {
      const m = join(ROOT, 'public/data/manifest.json');
      if (!existsSync(m)) return null;
      return join(ROOT, 'public/data', JSON.parse(readFileSync(m, 'utf8')).version, 'tags.json');
    })();
    const inputs: string[] = tagsFile ? JSON.parse(readFileSync(tagsFile, 'utf8')) : ['accusative singular'];
    writeFileSync(FILE, JSON.stringify(compute(inputs), null, 1) + '\n');
  }
  const committed = JSON.parse(readFileSync(FILE, 'utf8'));
  expect(compute(committed.describe.map((d: { tags: string }) => d.tags))).toEqual(committed);
});
