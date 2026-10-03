import { commonness } from './frequency';
import type { Lemma } from './types';

export interface SavedWord {
  lemmaId: number;
  word: string;
  pos: string;
  meaning: string;
  example?: [string, string];
  /** The form that was looked up, if different from the headword. */
  lookedUp?: string;
  /** Frequency rank (1 = most common). */
  fr?: number;
  added: number;
}

// Named after the app's old working title (Szóda); kept so saved words survive the rename.
const KEY = 'szoda.saved.v1';

function load(): SavedWord[] {
  try {
    const raw = localStorage.getItem(KEY);
    const list = raw ? JSON.parse(raw) : [];
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function persist(list: SavedWord[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {
    // Storage unavailable (private mode, quota): keep the in-memory list for this session.
  }
}

export const saved = $state({ list: load() });

export function isSaved(lemmaId: number): boolean {
  return saved.list.some((s) => s.lemmaId === lemmaId);
}

export function meaningOf(lemma: Lemma, max = 3): string {
  return lemma.s
    .slice(0, max)
    .map((s) => s.g)
    .join('; ');
}

export function toggleSaved(lemmaId: number, lemma: Lemma, lookedUp?: string) {
  if (isSaved(lemmaId)) {
    saved.list = saved.list.filter((s) => s.lemmaId !== lemmaId);
  } else {
    const example = lemma.ex?.[0] ?? lemma.s.find((s) => s.ex?.length)?.ex?.[0];
    saved.list = [
      {
        lemmaId,
        word: lemma.w,
        pos: lemma.pos,
        meaning: meaningOf(lemma),
        example,
        lookedUp: lookedUp && lookedUp.toLowerCase() !== lemma.w.toLowerCase() ? lookedUp : undefined,
        fr: lemma.fr,
        added: Date.now(),
      },
      ...saved.list,
    ];
  }
  persist(saved.list);
}

export function removeSaved(lemmaId: number) {
  saved.list = saved.list.filter((s) => s.lemmaId !== lemmaId);
  persist(saved.list);
}

function field(s: string): string {
  return s.replace(/[\t\r\n]+/g, ' ').trim();
}

/** Tab-separated text Anki imports directly (File → Import); header lines set the options. */
export function ankiTsv(list: SavedWord[]): string {
  const lines = ['#separator:tab', '#html:false', '#columns:Hungarian\tEnglish\tExample\tTags', '#tags column:4'];
  for (const s of list) {
    const example = s.example ? `${s.example[0]} — ${s.example[1]}` : '';
    const tags = ['hungarian', commonness(s.fr)?.tag].filter(Boolean).join(' ');
    lines.push([s.word, `${s.meaning} (${s.pos})`, example, tags].map(field).join('\t'));
  }
  return lines.join('\n') + '\n';
}

export function downloadAnki(list: SavedWord[]) {
  const blob = new Blob([ankiTsv(list)], { type: 'text/tab-separated-values;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `hungarian-words-${new Date().toISOString().slice(0, 10)}.txt`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
