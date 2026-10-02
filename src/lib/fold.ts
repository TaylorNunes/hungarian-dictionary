/** Lowercase and strip accents (á→a, ő→o, ü→u). Must match fold() in scripts/build_data.py. */
export function fold(s: string): string {
  return s.toLowerCase().normalize('NFD').replace(/\p{Mn}/gu, '').normalize('NFC');
}

/** Filename-safe version of a folded form. Must match shard_key_chars() in scripts/build_data.py. */
export function shardChars(folded: string): string {
  return [...folded].map((c) => (/[a-z0-9]/.test(c) ? c : '_')).join('');
}

/** True when the text contains any accented letter. */
export function hasAccents(s: string): boolean {
  return fold(s) !== s.toLowerCase();
}
