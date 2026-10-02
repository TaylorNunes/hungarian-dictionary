/**
 * Suffix-stripper fallback for words the inflection tables miss, e.g. possessive + case
 * (könyveimben = könyveim + -ben) or prefixed verbs (megláttam = meg- + láttam).
 *
 * It only proposes candidate stems; search.ts keeps the ones that exist in the index.
 */
import type { Part } from './tags';

export interface Step {
  /** The piece that was removed, e.g. "ben" or "meg". */
  piece: string;
  part: Part;
  prefix?: boolean;
}

export interface Candidate {
  stem: string;
  /** Steps from the stem outwards (inner suffix first). */
  steps: Step[];
}

type Rule = [endings: string[], part: Part];

const CASE_RULES: Rule[] = [
  [['ban', 'ben'], { label: 'inessive', hint: 'in' }],
  [['ba', 'be'], { label: 'illative', hint: 'into' }],
  [['ból', 'ből'], { label: 'elative', hint: 'out of' }],
  [['on', 'en', 'ön', 'n'], { label: 'superessive', hint: 'on' }],
  [['ra', 're'], { label: 'sublative', hint: 'onto' }],
  [['ról', 'ről'], { label: 'delative', hint: 'off, about' }],
  [['nál', 'nél'], { label: 'adessive', hint: 'at, by' }],
  [['hoz', 'hez', 'höz'], { label: 'allative', hint: 'to (towards)' }],
  [['tól', 'től'], { label: 'ablative', hint: 'from' }],
  [['nak', 'nek'], { label: 'dative', hint: 'to, for' }],
  [['val', 'vel'], { label: 'instrumental', hint: 'with' }],
  [['vá', 'vé'], { label: 'translative', hint: 'into (becoming)' }],
  [['ért'], { label: 'causal-final', hint: 'for, because of' }],
  [['ig'], { label: 'terminative', hint: 'as far as, until' }],
  [['ként'], { label: 'essive-formal', hint: 'as, in the role of' }],
  [['kor'], { label: 'temporal', hint: 'at (time)' }],
  [['ul', 'ül'], { label: 'essive-modal', hint: 'as, -ly; in (a language)' }],
  [['at', 'ot', 'et', 'öt', 't'], { label: 'accusative', hint: 'direct object' }],
];

const DERIVATION_RULES: Rule[] = [
  [['ság', 'ség'], { label: '-ság/-ség', hint: '-ness, abstract noun' }],
  [['talan', 'telen', 'tlan', 'tlen'], { label: '-talan/-telen', hint: 'without, -less' }],
  [['beli'], { label: '-beli', hint: 'in/of the …' }],
  [['nyi'], { label: '-nyi', hint: 'an amount of' }],
  [['ék'], { label: '-ék', hint: '… and family/group' }],
  [['é'], { label: '-é', hint: "…'s (belonging to)" }],
  [['i'], { label: '-i', hint: 'of, from (adjective)' }],
  [['s'], { label: '-s', hint: 'having …, with …' }],
];

const RULES = [...CASE_RULES, ...DERIVATION_RULES];

const PREVERBS: [string, string][] = [
  ['meg', 'completed action'], ['el', 'away; completed'], ['ki', 'out'], ['be', 'in'],
  ['fel', 'up'], ['föl', 'up'], ['le', 'down'], ['át', 'across, over'], ['rá', 'onto'],
  ['össze', 'together'], ['vissza', 'back'], ['oda', 'there'], ['ide', 'here'], ['szét', 'apart'],
  ['túl', 'over, beyond'], ['végig', 'all the way'], ['hozzá', 'to'], ['bele', 'into'],
  ['alá', 'under'], ['elő', 'forth'], ['fölé', 'above'], ['mellé', 'beside'], ['körül', 'around'],
  ['keresztül', 'through'], ['haza', 'home'], ['újra', 'again'], ['abba', 'stop'],
  ['agyon', 'to death, excessively'], ['tovább', 'on, further'], ['félre', 'aside'],
  ['közbe', 'in between'], ['utána', 'after'], ['neki', 'against, at'],
];

const DIGRAPHS = ['sz', 'zs', 'cs', 'gy', 'ly', 'ny', 'ty', 'dz'];
const VOWELS = new Set([...'aáeéiíoóöőuúüű']);
const MIN_STEM = 2;
const MAX_STEPS = 3;

/** Stems the ending may have been attached to (handles -val/-vá assimilation and á/é lengthening). */
function stemsFor(word: string, ending: string, rule: Rule): string[] {
  const out: string[] = [];
  if (word.endsWith(ending)) out.push(word.slice(0, -ending.length));
  // házzal = ház + -val, busszal = busz + -val, házzá = ház + -vá: the v becomes a copy of the last consonant.
  const label = rule[1].label;
  if ((label === 'instrumental' || label === 'translative') && ending.startsWith('v')) {
    const assimilated = ending.slice(1);
    if (word.endsWith(assimilated)) {
      const r = word.slice(0, -assimilated.length);
      const dig = DIGRAPHS.find((d) => r.endsWith(d[0] + d));
      if (dig) out.push(r.slice(0, -dig.length - 1) + dig);
      else if (r.length >= 2 && r.at(-1) === r.at(-2) && !VOWELS.has(r.at(-1)!)) out.push(r.slice(0, -1));
    }
  }
  // almát → almá → alma, kefében → kefé → kefe
  for (const s of [...out]) {
    if (s.endsWith('á')) out.push(s.slice(0, -1) + 'a');
    if (s.endsWith('é')) out.push(s.slice(0, -1) + 'e');
  }
  return out.filter((s) => [...s].length >= MIN_STEM);
}

function* stripSuffixes(word: string, depth: number): Generator<Candidate> {
  if (depth >= MAX_STEPS) return;
  for (const rule of RULES) {
    for (const ending of rule[0]) {
      for (const stem of stemsFor(word, ending, rule)) {
        const step: Step = { piece: ending, part: rule[1] };
        yield { stem, steps: [step] };
        for (const inner of stripSuffixes(stem, depth + 1)) {
          yield { stem: inner.stem, steps: [...inner.steps, step] };
        }
      }
    }
  }
}

/** Candidate analyses of a word, most plausible (fewest steps, longest stem) first. */
export function candidates(input: string): Candidate[] {
  const word = input.toLowerCase().normalize('NFC');
  const seen = new Set<string>();
  const out: Candidate[] = [];
  const push = (c: Candidate) => {
    const key = c.stem + '|' + c.steps.map((s) => s.piece + (s.prefix ? '-' : '')).join('+');
    if (!seen.has(key) && c.stem !== word) {
      seen.add(key);
      out.push(c);
    }
  };

  // Question particle: tudod-e
  const bases: Candidate[] = [{ stem: word, steps: [] }];
  const q = word.match(/^(.+)-e$/);
  if (q) bases.push({ stem: q[1], steps: [{ piece: 'e', part: { label: 'question -e', hint: 'whether …?' } }] });

  for (const base of bases) {
    if (base.steps.length) push(base);
    const forms: Candidate[] = [base];
    for (const [pv, hint] of PREVERBS) {
      if (base.stem.startsWith(pv) && base.stem.length - pv.length >= MIN_STEM) {
        const c = { stem: base.stem.slice(pv.length), steps: [{ piece: pv, part: { label: `${pv}-`, hint: `verbal prefix: ${hint}` }, prefix: true }, ...base.steps] };
        push(c);
        forms.push(c);
      }
    }
    for (const f of forms) {
      for (const c of stripSuffixes(f.stem, f.steps.length)) {
        const prefixSteps = f.steps.filter((s) => s.prefix);
        const suffixSteps = f.steps.filter((s) => !s.prefix);
        push({ stem: c.stem, steps: [...prefixSteps, ...c.steps, ...suffixSteps] });
      }
    }
  }

  return out.sort((a, b) => a.steps.length - b.steps.length || b.stem.length - a.stem.length);
}
