import { describe, expect, it } from 'vitest';
import { candidates } from './stemmer';

/** Candidate stems with the removed pieces, e.g. "könyveim+ben". */
function analyses(word: string): string[] {
  return candidates(word).map((c) =>
    [c.steps.filter((s) => s.prefix).map((s) => s.piece + '-').join(''), c.stem, ...c.steps.filter((s) => !s.prefix).map((s) => '+' + s.piece)].join(''),
  );
}

describe('suffix stripper', () => {
  it.each([
    ['könyveimben', 'könyveim+ben'],
    ['házaimban', 'házaim+ban'],
    ['almámban', 'almám+ban'],
    ['kertünkben', 'kertünk+ben'],
  ])('finds %s → %s', (word, expected) => {
    expect(analyses(word)).toContain(expected);
  });

  it.each([
    ['házzal', 'ház+val'],
    ['busszal', 'busz+val'],
    ['kulccsal', 'kulcs+val'],
    ['ággyal', 'ágy+val'],
    ['házzá', 'ház+vá'],
    ['barátaiddal', 'barátaid+val'],
  ])('undoes -val/-vá assimilation: %s → %s', (word, expected) => {
    expect(analyses(word)).toContain(expected);
  });

  it.each([
    ['almát', 'alma+t'],
    ['kefében', 'kefe+ben'],
    ['almáért', 'alma+ért'],
  ])('undoes final vowel lengthening: %s → %s', (word, expected) => {
    expect(analyses(word)).toContain(expected);
  });

  it.each([
    ['megláttam', 'meg-láttam'],
    ['elmentünk', 'el-mentünk'],
    ['visszajöttek', 'vissza-jöttek'],
    ['kimentem', 'ki-mentem'],
  ])('strips verbal prefixes: %s → %s', (word, expected) => {
    expect(analyses(word)).toContain(expected);
  });

  it.each([
    ['szabadság', 'szabad+ság'],
    ['boldogtalan', 'boldog+talan'],
    ['Péteré', 'péter+é'],
    ['budapesti', 'budapest+i'],
    ['magyarul', 'magyar+ul'],
  ])('strips derivational suffixes: %s → %s', (word, expected) => {
    expect(analyses(word)).toContain(expected);
  });

  it.each([
    ['házban', 'ház+ban'],
    ['házból', 'ház+ból'],
    ['házhoz', 'ház+hoz'],
    ['háztól', 'ház+tól'],
    ['háznál', 'ház+nál'],
    ['házra', 'ház+ra'],
    ['házról', 'ház+ról'],
    ['háznak', 'ház+nak'],
    ['házig', 'ház+ig'],
    ['házként', 'ház+ként'],
    ['házat', 'ház+at'],
    ['éjfélkor', 'éjfél+kor'],
  ])('strips case endings: %s → %s', (word, expected) => {
    expect(analyses(word)).toContain(expected);
  });

  it('handles the question particle', () => {
    expect(analyses('tudod-e')).toContain('tudod+e');
  });

  it('orders simpler analyses first and never proposes one-letter stems', () => {
    const list = candidates('könyveimben');
    expect(list[0].steps.length).toBe(1);
    expect(list.every((c) => [...c.stem].length >= 2)).toBe(true);
  });
});
