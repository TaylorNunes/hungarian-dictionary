import { describe, expect, it } from 'vitest';
import glossary from './glossary.json';
import { describeLabel, describePos, emphasisRuns, guideHref, labelName, labelsInGroup, LABEL_GROUPS } from './glossary';

describe('glossary', () => {
  it('puts every label in a known group, with text', () => {
    for (const [tag, info] of Object.entries(glossary.labels)) {
      expect(LABEL_GROUPS, tag).toContain(info.group);
      expect(info.text.length, tag).toBeGreaterThan(5);
    }
  });

  it('lists every label exactly once across the groups', () => {
    const listed = LABEL_GROUPS.flatMap((g) => labelsInGroup(g).map(([tag]) => tag));
    expect(listed.sort()).toEqual(Object.keys(glossary.labels).sort());
  });

  it('describes labels and parts of speech, with fallbacks', () => {
    expect(describeLabel('proscribed')?.group).toBe('Correctness');
    expect(describeLabel('no-such-label')).toBeUndefined();
    expect(describePos('postp')).toMatchObject({ label: 'postposition', hu: 'névutó' });
    expect(describePos('mystery-pos')).toEqual({ label: 'mystery pos', hu: '', text: '' });
    expect(labelName('not-comparable')).toBe('not comparable');
  });

  it('splits *emphasised* Hungarian words out of descriptions', () => {
    expect(emphasisRuns('A noun: *ház* "house".')).toEqual([
      { text: 'A noun: ', em: false },
      { text: 'ház', em: true },
      { text: ' "house".', em: false },
    ]);
  });

  it('builds guide links', () => {
    expect(guideHref.label('not-comparable')).toBe('#/guide/not-comparable');
    expect(guideHref.pos('adj')).toBe('#/guide/pos-adj');
  });
});
