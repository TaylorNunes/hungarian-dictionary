import { describe, expect, it } from 'vitest';
import { ankiTsv } from './saved.svelte';

describe('Anki export', () => {
  it('writes Anki header lines and one tab-separated row per word', () => {
    const tsv = ankiTsv([
      { lemmaId: 1, word: 'ház', pos: 'noun', meaning: 'house', example: ['Ez a ház\tnagy.', 'This house is big.'], fr: 245, added: 0 },
      { lemmaId: 2, word: 'lát', pos: 'verb', meaning: 'to see', added: 0 },
    ]);
    const lines = tsv.trimEnd().split('\n');
    expect(lines[0]).toBe('#separator:tab');
    expect(lines[1]).toBe('#html:false');
    expect(lines).toContain('ház\thouse (noun)\tEz a ház nagy. — This house is big.\thungarian top1000');
    expect(lines).toContain('lát\tto see (verb)\t\thungarian');
    expect(lines.filter((l) => !l.startsWith('#')).every((l) => l.split('\t').length === 4)).toBe(true);
  });
});
