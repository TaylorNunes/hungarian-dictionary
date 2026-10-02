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
}

/** One row in a form shard: [form, lemma id, tag set index]. */
export type FormRow = [form: string, lemmaId: number, tagIdx: number];

export interface Manifest {
  version: string;
  built: string;
  lemmaCount: number;
  formCount: number;
  lemmasPerShard: number;
  lemmaShards: number;
  formShards: string[];
  bytes: number;
  partial?: boolean;
}
