/**
 * Word frequency ranks come from subtitle counts (FrequencyWords, OpenSubtitles 2018), summed over
 * each word's forms by scripts/build_data.py. Rank 1 is the most common word.
 */

export interface Commonness {
  label: string;
  /** Anki tag, e.g. "top1000". */
  tag: string;
  title: string;
}

const TIERS: [limit: number, label: string][] = [
  [1000, 'very common'],
  [5000, 'common'],
];

export function commonness(rank: number | undefined): Commonness | null {
  if (!rank) return null;
  for (const [limit, label] of TIERS) {
    if (rank <= limit) {
      return { label, tag: `top${limit}`, title: `#${rank.toLocaleString('en')} most frequent word in Hungarian film and TV subtitles` };
    }
  }
  return null;
}

/** Ranking bonus for search results: about 12 for the commonest words, 0 beyond rank 10,000. */
export function frequencyBonus(rank: number | undefined): number {
  return rank ? Math.max(0, 12 - 3 * Math.log10(rank)) : 0;
}
