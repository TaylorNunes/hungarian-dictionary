import { describe as group, expect, it } from 'vitest';
import { buildSections, describe, isBaseForm } from './tags';

const labels = (tags: string) => describe(tags.split(' ')).map((p) => p.label);

group('describe', () => {
  it('describes noun cases and number', () => {
    expect(labels('accusative singular')).toEqual(['accusative']);
    expect(labels('inessive plural')).toEqual(['plural', 'inessive']);
  });

  it('describes possessives', () => {
    expect(labels('first-person possessed-many possessive singular')).toEqual(['my (several)']);
    expect(labels('plural possessed-single possessive third-person')).toEqual(['their']);
  });

  it('describes verb forms', () => {
    expect(labels('first-person indefinite indicative present singular')).toEqual(['present', 'I', 'indefinite']);
    expect(labels('conditional definite plural present third-person')).toEqual(['conditional', 'they / formal you all', 'definite']);
    expect(labels('first-person indicative object-second-person past singular')).toEqual(['past', 'I → you']);
    expect(labels('first-person indefinite indicative potential present singular')).toEqual(['can', 'present', 'I', 'indefinite']);
    expect(labels('infinitive')).toEqual(['infinitive']);
    expect(labels('noun-from-verb')).toEqual(['verbal noun']);
  });

  it('keeps usage labels', () => {
    expect(labels('archaic plural')).toEqual(['plural', 'archaic']);
  });

  it('recognises dictionary forms', () => {
    expect(isBaseForm(['nominative', 'singular'])).toBe(true);
    expect(isBaseForm([])).toBe(true);
    expect(isBaseForm(['accusative', 'singular'])).toBe(false);
  });
});

group('buildSections', () => {
  it('builds a declension grid for nouns', () => {
    const tags = [[], ['nominative', 'singular'], ['nominative', 'plural'], ['accusative', 'singular'], ['first-person', 'possessed-single', 'possessive', 'singular']];
    const sections = buildSections([['ház', 1], ['házak', 2], ['házat', 3], ['házam', 4]], tags);
    const decl = sections.find((s) => s.title === 'Declension');
    expect(decl?.kind).toBe('grid');
    if (decl?.kind !== 'grid') return;
    expect(decl.rows[0]).toMatchObject({ head: 'nominative', cells: [['ház'], ['házak']] });
    expect(decl.rows[1]).toMatchObject({ head: 'accusative', cells: [['házat'], []] });
    const poss = sections.find((s) => s.title === 'Possessive');
    expect(poss?.kind === 'grid' && poss.rows[0].cells[0]).toEqual(['házam']);
  });

  it('builds conjugation grids with definite columns and the -lak row', () => {
    const tags = [
      [],
      'indicative present indefinite first-person singular'.split(' '),
      'indicative present definite first-person singular'.split(' '),
      'indicative present first-person singular object-second-person'.split(' '),
    ];
    const [present] = buildSections([['látok', 1], ['látom', 2], ['látlak', 3]], tags);
    expect(present.kind === 'grid' && present.title).toBe('Present');
    if (present.kind !== 'grid') return;
    expect(present.cols).toEqual(['indefinite', 'definite']);
    expect(present.rows[0].cells).toEqual([['látok'], ['látom']]);
    expect(present.rows.at(-1)).toMatchObject({ head: 'én → téged', cells: [[], ['látlak']] });
  });

  it('collapses the definite column for intransitive verbs', () => {
    const tags = [[], 'indicative present indefinite first-person singular'.split(' ')];
    const [present] = buildSections([['megyek', 1]], tags);
    expect(present.kind === 'grid' && present.cols).toEqual(['form']);
  });
});
