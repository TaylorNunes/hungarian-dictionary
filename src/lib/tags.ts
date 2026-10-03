/**
 * Turns Kaikki-style tag sets into readable word breakdowns and inflection tables.
 * Verb tags are rebuilt by scripts/build_data.py into the same vocabulary as Kaikki's
 * form-of entries: mood, tense, definiteness, person and number.
 */

export interface Part {
  label: string;
  hint?: string;
}

export const CASES: [tag: string, label: string, hint: string][] = [
  ['nominative', 'nominative', 'subject'],
  ['accusative', 'accusative', 'direct object'],
  ['dative', 'dative', 'to, for'],
  ['instrumental', 'instrumental', 'with'],
  ['causal-final', 'causal-final', 'for, because of'],
  ['translative', 'translative', 'into (becoming)'],
  ['terminative', 'terminative', 'as far as, until'],
  ['essive-formal', 'essive-formal', 'as, in the role of'],
  ['essive-modal', 'essive-modal', 'as, -ly'],
  ['inessive', 'inessive', 'in'],
  ['superessive', 'superessive', 'on'],
  ['adessive', 'adessive', 'at, by'],
  ['illative', 'illative', 'into'],
  ['sublative', 'sublative', 'onto'],
  ['allative', 'allative', 'to (towards)'],
  ['elative', 'elative', 'out of'],
  ['delative', 'delative', 'off, about'],
  ['ablative', 'ablative', 'from'],
  ['temporal', 'temporal', 'at (time)'],
  ['locative', 'locative', 'in (place names)'],
];
const CASE_INFO = new Map(CASES.map(([tag, label, hint]) => [tag, { label, hint }]));

export const PERSONS: [key: string, pronoun: string, english: string][] = [
  ['first-person singular', 'én', 'I'],
  ['second-person singular', 'te', 'you'],
  ['third-person singular', 'ő / Ön', 'he, she, it / formal you'],
  ['first-person plural', 'mi', 'we'],
  ['second-person plural', 'ti', 'you all'],
  ['third-person plural', 'ők / Önök', 'they / formal you all'],
];
const POSSESSORS = ['my', 'your', 'his/her/its', 'our', 'your (pl.)', 'their'];

export const MOODS: Record<string, { label: string; hint?: string }> = {
  'indicative present': { label: 'present' },
  'indicative past': { label: 'past' },
  'indicative future': { label: 'future' },
  'conditional present': { label: 'conditional', hint: 'would …' },
  'conditional past': { label: 'conditional past', hint: 'would have …' },
  'subjunctive present': { label: 'subjunctive', hint: 'imperative; let …, that … should' },
};

const NONFINITE: Record<string, Part> = {
  'noun-from-verb': { label: 'verbal noun', hint: '-ás/-és, the act of …' },
  causative: { label: 'causative', hint: 'make/let someone …' },
  'adverbial participle': { label: 'adverbial participle', hint: '-va/-ve, …ing, having …' },
  'present participle': { label: 'present participle', hint: '-ó/-ő, …ing' },
  'past participle': { label: 'past participle', hint: '-t/-tt, …ed' },
  'future participle': { label: 'future participle', hint: '-andó/-endő, to be …ed' },
  'privative participle': { label: 'privative', hint: 'un…able' },
};

const GRAMMAR_TAGS = new Set([
  ...CASES.map(([t]) => t),
  'singular', 'plural', 'first-person', 'second-person', 'third-person', 'second-person-semantically',
  'indicative', 'conditional', 'subjunctive', 'present', 'past', 'future', 'definite', 'indefinite',
  'possessive', 'possessed-single', 'possessed-many', 'potential', 'infinitive', 'personal',
  'object-second-person', 'participle', 'adverbial', 'noun-from-verb', 'causative', 'privative',
  'formal', 'informal', 'canonical',
]);

function has(tags: string[], ...want: string[]): boolean {
  return want.every((t) => tags.includes(t));
}

function personIndex(tags: string[]): number {
  const p = has(tags, 'first-person') ? 0 : has(tags, 'second-person') ? 1 : has(tags, 'third-person') ? 2 : -1;
  if (p < 0) return -1;
  if (has(tags, 'plural')) return p + 3;
  if (has(tags, 'singular')) return p;
  return -1;
}

function humanize(tag: string): string {
  return tag.replace(/-/g, ' ');
}

function moodKey(tags: string[]): string | null {
  const mood = ['indicative', 'conditional', 'subjunctive'].find((m) => tags.includes(m));
  const tense = ['present', 'past', 'future'].find((t) => tags.includes(t));
  return mood && tense ? `${mood} ${tense}` : null;
}

function nonfiniteKey(tags: string[]): string | null {
  if (has(tags, 'noun-from-verb')) return 'noun-from-verb';
  if (has(tags, 'causative')) return 'causative';
  if (has(tags, 'adverbial', 'participle')) return 'adverbial participle';
  if (has(tags, 'privative')) return 'privative participle';
  for (const t of ['present', 'past', 'future']) if (has(tags, t, 'participle')) return `${t} participle`;
  return null;
}

/** Describe one tag set as a list of short labels with hints. */
export function describe(tags: string[]): Part[] {
  const parts: Part[] = [];
  const person = personIndex(tags);

  if (has(tags, 'potential')) parts.push({ label: 'can', hint: 'potential -hat/-het' });

  const mood = moodKey(tags);
  const nonfinite = nonfiniteKey(tags);
  if (mood) {
    parts.push(MOODS[mood] ?? { label: mood });
    if (has(tags, 'object-second-person')) {
      parts.push({ label: 'I → you', hint: '-lak/-lek: I do it to you' });
    } else {
      if (person >= 0) parts.push({ label: PERSONS[person][2], hint: PERSONS[person][1] });
      if (has(tags, 'definite')) parts.push({ label: 'definite', hint: 'with a specific object' });
      else if (has(tags, 'indefinite')) parts.push({ label: 'indefinite', hint: 'no specific object' });
    }
  } else if (has(tags, 'infinitive')) {
    if (person >= 0) parts.push({ label: `infinitive (for ${['me', 'you', 'him/her', 'us', 'you all', 'them'][person]})`, hint: 'personal infinitive' });
    else parts.push({ label: 'infinitive', hint: 'to …' });
  } else if (nonfinite) {
    parts.push(NONFINITE[nonfinite]);
  } else {
    if (has(tags, 'possessive') && person >= 0) {
      const many = has(tags, 'possessed-many');
      parts.push({ label: `${POSSESSORS[person]}${many ? ' (several)' : ''}`, hint: many ? 'possessive, several things' : 'possessive' });
    } else if (has(tags, 'plural')) {
      parts.push({ label: 'plural' });
    }
    for (const t of tags) {
      const c = CASE_INFO.get(t);
      if (c && !(t === 'nominative' && parts.length)) parts.push(c);
    }
  }

  for (const t of tags) {
    if (!GRAMMAR_TAGS.has(t)) parts.push({ label: humanize(t) });
  }
  return parts;
}

/** True when a tag set only restates the dictionary form (nominative singular, plain infinitive…). */
export function isBaseForm(tags: string[]): boolean {
  if (!tags.length) return true;
  const grammar = tags.filter((t) => GRAMMAR_TAGS.has(t));
  return grammar.every((t) => t === 'nominative' || t === 'singular' || t === 'canonical');
}

// ----------------------------------------------------------------------------- tables

export interface Grid {
  kind: 'grid';
  title: string;
  cols: string[];
  rows: { head: string; sub?: string; cells: string[][] }[];
}

export interface List {
  kind: 'list';
  title: string;
  items: { head: string; forms: string[] }[];
}

export type Section = Grid | List;

function addUnique(list: string[], form: string) {
  if (!list.includes(form)) list.push(form);
}

function emptyGrid(title: string, cols: string[], heads: [string, string?][]): Grid {
  return { kind: 'grid', title, cols, rows: heads.map(([head, sub]) => ({ head, sub, cells: cols.map(() => []) })) };
}

/** Group a lemma's inflection table into declension/conjugation grids plus a list of other forms. */
export function buildSections(table: [string, number][], tagSets: string[][]): Section[] {
  const personHeads = PERSONS.map(([, pron, en]) => [pron, en] as [string, string]);
  const declension = emptyGrid('Declension', ['singular', 'plural'], CASES.map(([t, , hint]) => [t, hint]));
  const possessive = emptyGrid('Possessive', ['one thing', 'several things'], PERSONS.map(([, pron], i) => [POSSESSORS[i], `${pron}`]));
  const verbGrids = new Map<string, Grid>();
  const infinitive = emptyGrid('Personal infinitive', ['form'], personHeads);
  const other: List = { kind: 'list', title: 'Other forms', items: [] };
  let usedDecl = false;
  let usedPoss = false;
  let usedInf = false;

  for (const [form, idx] of table) {
    const tags = tagSets[idx] ?? [];
    const person = personIndex(tags);
    const mood = moodKey(tags);
    const potential = has(tags, 'potential');
    const caseIdx = CASES.findIndex(([t]) => tags.includes(t));

    if (mood) {
      const key = `${potential ? 'potential ' : ''}${mood}`;
      let g = verbGrids.get(key);
      if (!g) {
        const m = MOODS[mood]?.label ?? mood;
        const title = (potential ? `Potential (can) · ${m}` : m).replace(/^./, (c) => c.toUpperCase());
        g = emptyGrid(title, ['indefinite', 'definite'], personHeads);
        verbGrids.set(key, g);
      }
      if (has(tags, 'object-second-person')) {
        let lak = g.rows.find((r) => r.head === 'én → téged');
        if (!lak) {
          lak = { head: 'én → téged', sub: 'I … you', cells: [[], []] };
          g.rows.push(lak);
        }
        addUnique(lak.cells[1], form);
      } else if (person >= 0) {
        addUnique(g.rows[person].cells[has(tags, 'definite') ? 1 : 0], form);
      }
    } else if (has(tags, 'infinitive') && person >= 0 && !potential) {
      addUnique(infinitive.rows[person].cells[0], form);
      usedInf = true;
    } else if (has(tags, 'possessive') && person >= 0 && !tags.some((t) => CASE_INFO.has(t) && t !== 'nominative')) {
      addUnique(possessive.rows[person].cells[has(tags, 'possessed-many') ? 1 : 0], form);
      usedPoss = true;
    } else if (caseIdx >= 0 && !has(tags, 'possessive') && (has(tags, 'singular') || has(tags, 'plural'))) {
      addUnique(declension.rows[caseIdx].cells[has(tags, 'plural') ? 1 : 0], form);
      usedDecl = true;
    } else if (tags.length) {
      const head = describe(tags).map((p) => p.label).join(' · ');
      const item = other.items.find((i) => i.head === head);
      if (item) addUnique(item.forms, form);
      else other.items.push({ head, forms: [form] });
    }
  }

  const sections: Section[] = [];
  const order = ['indicative present', 'indicative past', 'indicative future', 'conditional present', 'conditional past', 'subjunctive present'];
  const verbKeys = [...verbGrids.keys()].sort((a, b) => {
    const pa = a.startsWith('potential') ? 1 : 0;
    const pb = b.startsWith('potential') ? 1 : 0;
    return pa - pb || order.indexOf(a.replace('potential ', '')) - order.indexOf(b.replace('potential ', ''));
  });
  for (const k of verbKeys) {
    const g = verbGrids.get(k)!;
    // Intransitive verbs have no definite column.
    if (g.rows.every((r) => r.cells[1].length === 0)) {
      g.cols = ['form'];
      g.rows = g.rows.map((r) => ({ ...r, cells: [r.cells[0]] }));
    }
    sections.push(g);
  }
  if (usedInf) sections.push(infinitive);
  if (usedDecl) {
    declension.rows = declension.rows.filter((r) => r.cells.some((c) => c.length));
    sections.push(declension);
  }
  if (usedPoss) sections.push(possessive);
  if (other.items.length) sections.push(other);
  return sections;
}
