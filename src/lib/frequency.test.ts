import { describe, expect, it } from 'vitest';
import { commonness, frequencyBonus } from './frequency';

describe('commonness', () => {
  it.each([
    [1, 'very common', 'top1000'],
    [1000, 'very common', 'top1000'],
    [1001, 'common', 'top5000'],
    [5000, 'common', 'top5000'],
  ])('rank %i → %s', (rank, label, tag) => {
    expect(commonness(rank)).toMatchObject({ label, tag });
  });

  it('has no badge for rarer or unranked words', () => {
    expect(commonness(5001)).toBeNull();
    expect(commonness(undefined)).toBeNull();
  });

  it('explains the rank', () => {
    expect(commonness(245)?.title).toContain('#245');
  });
});

describe('frequencyBonus', () => {
  it('decreases with rank and bottoms out at zero', () => {
    expect(frequencyBonus(1)).toBe(12);
    expect(frequencyBonus(100)).toBeGreaterThan(frequencyBonus(1000));
    expect(frequencyBonus(10_000)).toBe(0);
    expect(frequencyBonus(50_000)).toBe(0);
    expect(frequencyBonus(undefined)).toBe(0);
  });
});
