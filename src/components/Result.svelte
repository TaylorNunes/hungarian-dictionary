<script lang="ts">
  import type { Result } from '../lib/search';
  import { isSaved, toggleSaved } from '../lib/saved.svelte';
  import InflectionTable from './InflectionTable.svelte';
  import Sentence from './Sentence.svelte';

  let { result, query }: { result: Result; query: string } = $props();

  const lemma = $derived(result.lemma);
  const isSavedNow = $derived(isSaved(result.lemmaId));
  const posLabel: Record<string, string> = {
    noun: 'noun', verb: 'verb', adj: 'adjective', adv: 'adverb', pron: 'pronoun', num: 'numeral',
    name: 'proper noun', intj: 'interjection', conj: 'conjunction', postp: 'postposition', det: 'determiner',
    article: 'article', particle: 'particle', prefix: 'prefix', suffix: 'suffix', phrase: 'phrase',
    proverb: 'proverb', character: 'letter', abbrev: 'abbreviation', prep_phrase: 'phrase', contraction: 'contraction',
  };
  const examples = $derived(lemma.ex ?? []);
  // English lookups match one sense; show it first, keeping its original number.
  const senses = $derived(
    lemma.s
      .map((s, i) => ({ ...s, n: i + 1, match: i === result.sense }))
      .sort((a, b) => Number(b.match) - Number(a.match)),
  );
</script>

<article class="entry">
  <header>
    <div class="head">
      <h2 lang="hu">{lemma.w}</h2>
      <span class="pos">{posLabel[lemma.pos] ?? lemma.pos}</span>
      {#if lemma.ipa}<span class="ipa">{lemma.ipa}</span>{/if}
    </div>
    <button
      class="star"
      class:on={isSavedNow}
      aria-pressed={isSavedNow}
      aria-label={isSavedNow ? `Remove ${lemma.w} from saved words` : `Save ${lemma.w}`}
      title={isSavedNow ? 'Saved' : 'Save for review / Anki'}
      onclick={() => toggleSaved(result.lemmaId, lemma, query)}
    >
      {isSavedNow ? '★' : '☆'}
    </button>
  </header>

  {#if result.analyses.length}
    <ul class="analyses">
      {#each result.analyses as a}
        <li>
          <span class="form" lang="hu">{result.guessed ? query : a.form}</span>
          <span class="eq">=</span>
          <span class="lemma" lang="hu">{lemma.w}</span>
          {#each a.parts as p}
            <span class="plus">+</span>
            <span class="part" title={p.hint}>{p.label}{#if p.hint}<small>({p.hint})</small>{/if}</span>
          {/each}
          {#if result.guessed}<span class="guessed" title="Worked out by removing suffixes">guess</span>{/if}
        </li>
      {/each}
    </ul>
  {/if}

  <ol class="senses">
    {#each senses as s (s.n)}
      <li value={s.n} class:match={s.match}>
        {#if s.t?.length}
          {#each s.t as label}<span class="label">{label.replace(/-/g, ' ')}</span>{/each}
        {/if}
        {#if s.p}<span class="parent">{s.p} ›</span>{/if}
        <span class="gloss">{s.g}</span>
        {#if s.ex?.length}
          <ul class="sense-ex">
            {#each s.ex as ex}<li><Sentence {ex} /></li>{/each}
          </ul>
        {/if}
      </li>
    {/each}
  </ol>

  {#if examples.length}
    <section class="examples">
      <h3>Examples</h3>
      <ul>
        {#each examples as ex}<li><Sentence {ex} /></li>{/each}
      </ul>
    </section>
  {/if}

  {#if lemma.t?.length}
    <details class="table">
      <summary>{lemma.pos === 'verb' ? 'Conjugation' : 'All forms'}</summary>
      <InflectionTable table={lemma.t} />
    </details>
  {/if}
</article>

<style>
  .entry {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow);
    padding: 14px 16px 12px;
    overflow-wrap: anywhere;
  }
  header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 8px;
  }
  .head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 4px 10px;
  }
  h2 {
    font-family: var(--serif);
    font-size: 1.65rem;
    font-weight: 600;
    margin: 0;
    line-height: 1.2;
  }
  .pos {
    color: var(--accent);
    font-style: italic;
    font-size: 0.95rem;
  }
  .ipa {
    color: var(--faint);
    font-size: 0.9rem;
  }
  .star {
    border: 0;
    background: none;
    font-size: 1.6rem;
    line-height: 1;
    color: var(--faint);
    cursor: pointer;
    padding: 4px 6px;
    margin: -4px -6px 0 0;
    border-radius: 8px;
  }
  .star.on {
    color: var(--star);
  }
  .analyses {
    list-style: none;
    padding: 0;
    margin: 10px 0 6px;
    display: grid;
    gap: 6px;
  }
  .analyses li {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 4px 6px;
    background: var(--accent-soft);
    border-radius: 8px;
    padding: 6px 10px;
    font-size: 0.95rem;
  }
  .form {
    font-style: italic;
  }
  .eq,
  .plus {
    color: var(--faint);
  }
  .lemma {
    font-weight: 650;
  }
  .part small {
    color: var(--muted);
    font-size: 0.85em;
    margin-left: 0.3em;
  }
  .guessed {
    margin-left: auto;
    font-size: 0.75rem;
    color: var(--warn);
    background: var(--warn-soft);
    border-radius: 999px;
    padding: 1px 8px;
  }
  .senses {
    margin: 10px 0 4px;
    padding-left: 1.4em;
  }
  .senses > li {
    margin: 4px 0;
  }
  .senses > li::marker {
    color: var(--faint);
  }
  .senses > li.match {
    background: var(--accent-soft);
    border-radius: 6px;
    padding: 2px 8px;
    margin-left: -8px;
  }
  .senses > li.match::marker {
    color: var(--accent);
  }
  .label {
    display: inline-block;
    font-size: 0.75rem;
    color: var(--muted);
    background: var(--surface-2);
    border-radius: 4px;
    padding: 0 6px;
    margin-right: 5px;
    vertical-align: 1px;
  }
  .parent {
    color: var(--muted);
  }
  .sense-ex {
    list-style: none;
    padding: 0;
    margin: 4px 0 6px;
    font-size: 0.92rem;
  }
  .examples h3 {
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--faint);
    margin: 14px 0 6px;
    font-weight: 600;
  }
  .examples ul {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 8px;
    font-size: 0.95rem;
  }
  .table {
    margin-top: 12px;
    border-top: 1px solid var(--border);
    padding-top: 8px;
  }
  summary {
    cursor: pointer;
    color: var(--accent);
    font-weight: 550;
    padding: 4px 0;
  }
</style>
