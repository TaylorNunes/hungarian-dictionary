/**
 * English query handling for English → Hungarian lookup. The index (built by
 * build_english_index in scripts/build_data.py) holds gloss terms like "house" or
 * "see", so queries are normalised the same way and inflected words reduced to them.
 */

/** Lowercase, trim, collapse spaces and drop a leading "to"/article: "To See" → "see". */
export function normalizeEnglish(q: string): string {
  return q
    .normalize('NFC')
    .toLowerCase()
    .replace(/[\s ]+/g, ' ')
    .trim()
    .replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '')
    .replace(/^(?:to|a|an|the) /, '');
}

const IRREGULAR: Record<string, string> = {
  went: 'go', gone: 'go', goes: 'go', saw: 'see', seen: 'see', ate: 'eat', eaten: 'eat',
  was: 'be', were: 'be', been: 'be', am: 'be', are: 'be', did: 'do', done: 'do', does: 'do',
  had: 'have', has: 'have', made: 'make', took: 'take', taken: 'take', came: 'come',
  gave: 'give', given: 'give', knew: 'know', known: 'know', thought: 'think', bought: 'buy',
  brought: 'bring', told: 'tell', said: 'say', found: 'find', got: 'get', gotten: 'get',
  left: 'leave', felt: 'feel', kept: 'keep', ran: 'run', wrote: 'write', written: 'write',
  spoke: 'speak', spoken: 'speak', drank: 'drink', drunk: 'drink', sang: 'sing', sung: 'sing',
  swam: 'swim', slept: 'sleep', sat: 'sit', stood: 'stand', understood: 'understand',
  began: 'begin', begun: 'begin', broke: 'break', broken: 'break', chose: 'choose',
  chosen: 'choose', drove: 'drive', driven: 'drive', fell: 'fall', fallen: 'fall', flew: 'fly',
  flown: 'fly', forgot: 'forget', forgotten: 'forget', held: 'hold', met: 'meet', paid: 'pay',
  sold: 'sell', sent: 'send', spent: 'spend', taught: 'teach', caught: 'catch', fought: 'fight',
  won: 'win', lost: 'lose', heard: 'hear', meant: 'mean', built: 'build', lay: 'lie', lain: 'lie',
  rode: 'ride', ridden: 'ride', wore: 'wear', worn: 'wear', grew: 'grow', grown: 'grow',
  threw: 'throw', thrown: 'throw', children: 'child', men: 'man', women: 'woman', feet: 'foot',
  teeth: 'tooth', mice: 'mouse', geese: 'goose', people: 'person', better: 'good', best: 'good',
  worse: 'bad', worst: 'bad',
};

const VOWEL = /[aeiou]/;

/**
 * Possible base forms of an inflected English term, most likely first (excluding the term itself).
 * Only the last word changes: "running shoes" → "running shoe".
 */
export function deinflect(term: string): string[] {
  const words = term.split(' ');
  const last = words.pop()!;
  const head = words.length ? words.join(' ') + ' ' : '';
  const out: string[] = [];
  const add = (w: string) => {
    if (w.length >= 2 && w !== last && !out.includes(head + w)) out.push(head + w);
  };
  const undouble = (stem: string) => {
    // running → runn → run, stopped → stopp → stop
    if (stem.length >= 3 && stem.at(-1) === stem.at(-2) && !VOWEL.test(stem.at(-1)!)) add(stem.slice(0, -1));
  };

  if (IRREGULAR[last]) add(IRREGULAR[last]);
  if (last.endsWith("'s")) add(last.slice(0, -2));
  if (last.endsWith('ies')) add(last.slice(0, -3) + 'y');
  if (last.endsWith('ves')) {
    add(last.slice(0, -3) + 'f');
    add(last.slice(0, -3) + 'fe');
  }
  if (last.endsWith('s') && !last.endsWith('ss')) add(last.slice(0, -1));
  if (last.endsWith('es')) add(last.slice(0, -2));
  if (last.endsWith('ied')) add(last.slice(0, -3) + 'y');
  if (last.endsWith('ed')) {
    const stem = last.slice(0, -2);
    add(stem + 'e');
    add(stem);
    undouble(stem);
  }
  if (last.endsWith('ing')) {
    const stem = last.slice(0, -3);
    add(stem);
    add(stem + 'e');
    undouble(stem);
  }
  if (last.endsWith('ier')) add(last.slice(0, -3) + 'y');
  if (last.endsWith('iest')) add(last.slice(0, -4) + 'y');
  if (last.endsWith('er') || last.endsWith('est')) {
    const stem = last.slice(0, last.endsWith('er') ? -2 : -3);
    add(stem);
    add(stem + 'e');
    undouble(stem);
  }
  if (last.endsWith('ly')) add(last.slice(0, -2));
  return out;
}
