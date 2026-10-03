/** Routes for the word page: #/entry/<word>/<pos>?id=<lemmaId>&from=<searched form>. */
import type { Lemma } from './types';

export interface EntryRoute {
  word: string;
  pos: string;
  /** Lemma id in the data version the link was made with; only a hint, since ids can shift. */
  id?: number;
  /** The form that was searched, when different from the headword (for the breakdown). */
  from?: string;
}

export function entryHref(lemma: Pick<Lemma, 'w' | 'pos'>, lemmaId: number, from?: string): string {
  const params = new URLSearchParams({ id: String(lemmaId) });
  if (from && from.toLowerCase() !== lemma.w.toLowerCase()) params.set('from', from);
  return `#/entry/${encodeURIComponent(lemma.w)}/${encodeURIComponent(lemma.pos)}?${params}`;
}

export function parseEntryHash(hash: string): EntryRoute | null {
  const m = hash.replace(/^#\/?/, '').match(/^entry\/([^/?]+)\/([^/?]+)(?:\?(.*))?$/);
  if (!m) return null;
  const params = new URLSearchParams(m[3] ?? '');
  const id = Number(params.get('id'));
  return {
    word: decodeURIComponent(m[1]),
    pos: decodeURIComponent(m[2]),
    id: params.has('id') && Number.isInteger(id) && id >= 0 ? id : undefined,
    from: params.get('from') || undefined,
  };
}
