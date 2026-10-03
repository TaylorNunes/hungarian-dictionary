export type Example = [hungarian: string, english: string];

export interface Sense {
  /** English gloss. */
  g: string;
  /** Parent gloss, for sub-senses. */
  p?: string;
  /** Usage labels (transitive, archaic…). */
  t?: string[];
  ex?: Example[];
}

export interface Lemma {
  w: string;
  pos: string;
  ipa?: string;
  s: Sense[];
  /** Inflection table: [form, tag set index]. */
  t?: [string, number][];
  /** Example sentences from Tatoeba. */
  ex?: Example[];
  /** Frequency rank in subtitles (1 = most common); absent for words not seen there. */
  fr?: number;
}

/** One row in a form shard: [form, lemma id, tag set index]. */
export type FormRow = [form: string, lemmaId: number, tagIdx: number];

/** One row in an English shard: [lemma id, matching sense index, 0 if the term leads its gloss else 1]. */
export type EnglishRow = [lemmaId: number, senseIndex: number, position: number];

export interface Manifest {
  version: string;
  built: string;
  lemmaCount: number;
  /** Entries per part of speech. */
  posCounts?: Record<string, number>;
  formCount: number;
  lemmasPerShard: number;
  lemmaShards: number;
  formShards: string[];
  englishTermCount: number;
  enShards: string[];
  bytes: number;
  partial?: boolean;
}
