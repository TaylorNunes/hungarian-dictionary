<script lang="ts">
  import { entryHref } from '../lib/entry';
  import type { Result } from '../lib/search';
  import Breakdown from './Breakdown.svelte';
  import Senses from './Senses.svelte';
  import WordHeader from './WordHeader.svelte';

  let { result, query }: { result: Result; query: string } = $props();

  const href = $derived(entryHref(result.lemma, result.lemmaId, result.sense === undefined ? query : undefined));
</script>

<article class="entry">
  <WordHeader lemma={result.lemma} lemmaId={result.lemmaId} {query} {href} />
  <Breakdown analyses={result.analyses} headword={result.lemma.w} searched={query} guessed={result.guessed} compact />
  <Senses senses={result.lemma.s} highlight={result.sense} />
</article>

<style>
  .entry {
    position: relative;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow);
    padding: 14px 16px 10px;
    overflow-wrap: anywhere;
    cursor: pointer;
    transition: border-color 0.15s;
  }
  .entry:hover {
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }
</style>
