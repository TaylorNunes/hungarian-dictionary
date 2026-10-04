import { describe, expect, it } from 'vitest';
import { partialKey, rankPartial, rankSections, type EnglishResponse, type Result, type SearchResponse } from './search';
import type { HeadRow } from './types';

function result(word: string, gloss: string, opts: Partial<Result> = {}): Result {
  return { lemmaId: word.length, lemma: { w: word, pos: 'noun', s: [{ g: gloss }] }, analyses: [], exact: true, guessed: false, ...opts };
}
const hu = (query: string, results: Result[]): SearchResponse => ({ query, results, tokens: [] });
const en = (term: string, results: Result[], exact = true, strong = true): EnglishResponse => ({ term, results, exact, strong });
const order = (h: SearchResponse, e: EnglishResponse | null) => rankSections(h, e).map((s) => s.lang);

describe('rankSections', () => {
  it('puts an exact Hungarian word first when English also matches', () => {
    expect(order(hu('fog', [result('fog', 'tooth')]), en('fog', [result('köd', 'fog, mist')]))).toEqual(['hu', 'en']);
  });

  it('puts English first when the Hungarian match is a loan of the same English word', () => {
    expect(order(hu('house', [result('house', 'house music, house')]), en('house', [result('ház', 'house, building')]))).toEqual(['en', 'hu']);
  });

  it('drops Hungarian stemmer guesses when English finds the word', () => {
    const guess = result('house', 'house music', { exact: false, guessed: true });
    expect(order(hu('houses', [guess]), en('house', [result('ház', 'house')], false, false))).toEqual(['en']);
  });

  it('keeps Hungarian stemmer guesses when English has nothing', () => {
    const guess = result('könyv', 'book', { exact: false, guessed: true });
    expect(order(hu('konyveimben', [guess]), en('konyveimben', []))).toEqual(['hu']);
  });

  it('puts a leading English term ahead of an accent-folded Hungarian match', () => {
    const folded = result('kár', 'damage', { exact: false });
    expect(order(hu('kar', [folded]), en('kar', [result('autó', 'car')]))).toEqual(['en', 'hu']);
  });

  it('puts an exact English term ahead of an accent-folded Hungarian match even when it is not leading', () => {
    const folded = result('cár', 'tsar', { exact: false });
    expect(order(hu('car', [folded]), en('car', [result('autó', 'automobile, car')], true, false))).toEqual(['en', 'hu']);
  });

  it('shows only Hungarian for accented queries (no English search)', () => {
    expect(order(hu('házat', [result('ház', 'house')]), null)).toEqual(['hu']);
  });
});

describe('partial matches', () => {
  const row = (word: string, id: number, rank = 0): HeadRow => [word, id, 'noun', rank, ''];
  const words = (list: { word: string }[]) => list.map((m) => m.word);

  it('needs at least two letters', () => {
    expect(partialKey('h')).toBeNull();
    expect(partialKey(' -h ')).toBeNull();
    expect(partialKey('Ha')).toBe('ha');
    expect(partialKey('ház')).toBe('haz');
  });

  it('weights starts and ends the same: common first, then unranked by length', () => {
    const starts = [row('háztartás', 2, 3000), row('házas', 1, 900), row('házikó', 6)];
    const ends = [row('bérház', 3, 2000), row('tűzoltóház', 4), row('családi ház', 5, 400)];
    expect(words(rankPartial('ház', starts, ends))).toEqual(['családi ház', 'házas', 'bérház', 'háztartás', 'házikó', 'tűzoltóház']);
  });

  it('marks where the query matched and lists a word once', () => {
    const [m] = rankPartial('ha', [row('haha', 1)], [row('haha', 1)]);
    expect(m).toMatchObject({ word: 'haha', start: true, end: true });
    expect(rankPartial('ha', [row('haha', 1)], [row('haha', 1)])).toHaveLength(1);
  });

  it('drops the query itself and words already in the results', () => {
    const starts = [row('ház', 1, 245), row('házas', 2, 900), row('háziasszony', 3)];
    expect(words(rankPartial('haz', starts, [row('ház', 1, 245)], new Set([3])))).toEqual(['házas']);
  });

  it('ignores accents unless they are typed', () => {
    const starts = [row('karó', 1), row('kárpit', 2)];
    expect(words(rankPartial('kar', starts, []))).toEqual(['karó', 'kárpit']);
    expect(words(rankPartial('kár', starts, []))).toEqual(['kárpit']);
    expect(words(rankPartial('kár', [], [row('akar', 3), row('lekár', 4)]))).toEqual(['lekár']);
  });
});
