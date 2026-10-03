<script lang="ts">
  import type { Sense } from '../lib/types';
  import Sentence from './Sentence.svelte';

  let {
    senses,
    highlight,
    examples = false,
  }: {
    senses: Sense[];
    /** Index of the meaning an English search matched; highlighted in place. */
    highlight?: number;
    /** Show the Wiktionary examples under each meaning (word page only). */
    examples?: boolean;
  } = $props();
</script>

<ol class="senses">
  {#each senses as s, i}
    <li class:match={i === highlight}>
      {#each s.t ?? [] as label}<span class="label">{label.replace(/-/g, ' ')}</span>{/each}
      {#if s.p}<span class="parent">{s.p} ›</span>{/if}
      <span class="gloss">{s.g}</span>
      {#if examples && s.ex?.length}
        <ul class="sense-ex">
          {#each s.ex as ex}<li><Sentence {ex} /></li>{/each}
        </ul>
      {/if}
    </li>
  {/each}
</ol>

<style>
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
    margin: 4px 0 8px;
    font-size: 0.92rem;
  }
</style>
