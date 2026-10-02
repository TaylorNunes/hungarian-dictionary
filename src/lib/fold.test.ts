import { describe, expect, it } from 'vitest';
import cases from '../../scripts/fixtures/fold_cases.json';
import { fold, hasAccents, shardChars } from './fold';

describe('fold', () => {
  it.each(cases as [string, string][])('folds %s like the Python build', (input, expected) => {
    expect(fold(input)).toBe(expected);
  });

  it('makes shard keys filename-safe', () => {
    expect(shardChars('jo reggelt')).toBe('jo_reggelt');
    expect(shardChars('a-t')).toBe('a_t');
  });

  it('detects accents', () => {
    expect(hasAccents('házat')).toBe(true);
    expect(hasAccents('Hazat')).toBe(false);
  });
});
