import { describe, expect, it } from 'vitest';
import { rankSections, type EnglishResponse, type Result, type SearchResponse } from './search';

function result(word: string, gloss: string, opts: Partial<Result> = {}): Result {
  return { lemmaId: word.length, lemma: { w: word, pos: 'noun', s: [{ g: gloss }] }, analyses: [], exact: true, guessed: false, ...opts };
}
const hu = (query: string, results: Result[]): SearchResponse => ({ query, results, tokens: [], suggestions: [] });
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
