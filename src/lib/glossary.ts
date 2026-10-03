/**
 * Descriptions for parts of speech and Wiktionary usage labels, shown on the Guide page and as
 * tooltips. The text lives in glossary.json so the data build can warn about undescribed labels.
 */
import glossary from './glossary.json';

export interface LabelInfo {
  group: string;
  text: string;
}

export interface PosInfo {
  /** Shown in entry headers, e.g. "adjective" for "adj". */
  label: string;
  /** Hungarian grammatical term, e.g. "melléknév". */
  hu: string;
  text: string;
}

export const LABEL_GROUPS: string[] = glossary.labelGroups;
const LABELS: Record<string, LabelInfo> = glossary.labels;
const POS: Record<string, PosInfo> = glossary.pos;

/** A label as displayed: "not-comparable" → "not comparable". */
export function labelName(tag: string): string {
  return tag.replace(/-/g, ' ');
}

export function describeLabel(tag: string): LabelInfo | undefined {
  return LABELS[tag];
}

export function describePos(pos: string): PosInfo {
  return POS[pos] ?? { label: labelName(pos), hu: '', text: '' };
}

/** Labels of one group, alphabetically by displayed name. */
export function labelsInGroup(group: string): [tag: string, info: LabelInfo][] {
  return Object.entries(LABELS)
    .filter(([, info]) => info.group === group)
    .sort(([a], [b]) => labelName(a).localeCompare(labelName(b), 'en', { sensitivity: 'base' }));
}

export function allPos(): [pos: string, info: PosInfo][] {
  return Object.entries(POS);
}

/** Text with *asterisks* marking Hungarian words, split into plain and emphasised runs. */
export function emphasisRuns(text: string): { text: string; em: boolean }[] {
  return text.split(/\*([^*]+)\*/).map((t, i) => ({ text: t, em: i % 2 === 1 })).filter((r) => r.text);
}

/** Guide page anchors: #/guide/<anchor>. */
export const guideHref = {
  label: (tag: string) => `#/guide/${encodeURIComponent(tag)}`,
  pos: (pos: string) => `#/guide/pos-${encodeURIComponent(pos)}`,
  commonness: '#/guide/commonness',
};
