import { describe, expect, it } from 'vitest';
import { deinflect, normalizeEnglish } from './english';

describe('normalizeEnglish', () => {
  it.each([
    ['To See', 'see'],
    ['  the   House ', 'house'],
    ['an apple', 'apple'],
    ['good morning!', 'good morning'],
    ['"beautiful"', 'beautiful'],
    ['toast', 'toast'],
  ])('%s → %s', (q, expected) => {
    expect(normalizeEnglish(q)).toBe(expected);
  });
});

describe('deinflect', () => {
  it.each([
    ['houses', 'house'],
    ['cities', 'city'],
    ['watches', 'watch'],
    ['watched', 'watch'],
    ['liked', 'like'],
    ['stopped', 'stop'],
    ['running', 'run'],
    ['making', 'make'],
    ['studied', 'study'],
    ['knives', 'knife'],
    ['bigger', 'big'],
    ['happiest', 'happy'],
    ['went', 'go'],
    ['children', 'child'],
    ["dog's", 'dog'],
    ['running shoes', 'running shoe'],
  ])('%s → %s', (word, base) => {
    expect(deinflect(word)).toContain(base);
  });

  it('never returns the word itself or one-letter stems', () => {
    for (const w of ['is', 'house', 'sing', 'bed']) {
      const out = deinflect(w);
      expect(out).not.toContain(w);
      expect(out.every((x) => x.length >= 2)).toBe(true);
    }
  });
});
