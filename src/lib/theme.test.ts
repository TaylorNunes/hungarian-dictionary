import { describe, expect, it } from 'vitest';
import { applyTheme, saveTheme, storedTheme, THEME_COLORS, THEME_KEY } from './theme';

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial };
  return { data, getItem: (k: string) => data[k] ?? null, setItem: (k: string, v: string) => void (data[k] = v) };
}

const throwing = {
  getItem: () => {
    throw new Error('blocked');
  },
  setItem: () => {
    throw new Error('blocked');
  },
};

describe('theme', () => {
  it('defaults to dark', () => {
    expect(storedTheme(memoryStorage())).toBe('dark');
    expect(storedTheme(undefined)).toBe('dark');
    expect(storedTheme(memoryStorage({ [THEME_KEY]: 'nonsense' }))).toBe('dark');
  });

  it('remembers the light choice', () => {
    const s = memoryStorage();
    saveTheme('light', s);
    expect(s.data[THEME_KEY]).toBe('light');
    expect(storedTheme(s)).toBe('light');
  });

  it('survives storage that throws', () => {
    expect(storedTheme(throwing)).toBe('dark');
    expect(() => saveTheme('light', throwing)).not.toThrow();
  });

  it('sets data-theme and the browser-bar colour', () => {
    let meta = '';
    const doc = {
      documentElement: { dataset: {} as Record<string, string> },
      querySelector: () => ({ setAttribute: (_: string, v: string) => (meta = v) }),
    };
    applyTheme('light', doc as unknown as Document);
    expect(doc.documentElement.dataset.theme).toBe('light');
    expect(meta).toBe(THEME_COLORS.light);
  });
});
