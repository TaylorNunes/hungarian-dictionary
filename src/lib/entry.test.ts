import { describe, expect, it } from 'vitest';
import { entryHref, parseEntryHash } from './entry';

describe('entry routes', () => {
  it('round-trips accented words, phrases and the searched form', () => {
    for (const [w, pos, id, from] of [
      ['ház', 'noun', 4512, 'házat'],
      ['jó reggelt', 'phrase', 7, undefined],
      ['tud', 'verb', 0, 'tudod-e'],
    ] as const) {
      const href = entryHref({ w, pos }, id, from);
      expect(parseEntryHash(href)).toEqual({ word: w, pos, id, from });
    }
  });

  it('leaves out "from" when it is just the headword', () => {
    expect(entryHref({ w: 'ház', pos: 'noun' }, 1, 'Ház')).not.toContain('from');
  });

  it('treats a missing or bad id as absent', () => {
    expect(parseEntryHash('#/entry/h%C3%A1z/noun')).toEqual({ word: 'ház', pos: 'noun', id: undefined, from: undefined });
    expect(parseEntryHash('#/entry/h%C3%A1z/noun?id=abc')?.id).toBeUndefined();
  });

  it('ignores other routes', () => {
    expect(parseEntryHash('#/w/house')).toBeNull();
    expect(parseEntryHash('#/saved')).toBeNull();
  });
});
