<script lang="ts">
  import type { Analysis } from '../lib/search';

  let {
    analyses,
    headword,
    searched,
    guessed = false,
    compact = false,
  }: {
    analyses: Analysis[];
    headword: string;
    /** What the user typed; shown instead of the table form when the analysis is a guess. */
    searched: string;
    guessed?: boolean;
    /** One line, labels only (results list); otherwise every analysis with hints (word page). */
    compact?: boolean;
  } = $props();

  const shown = $derived(compact ? analyses.slice(0, 1) : analyses);
</script>

{#if analyses.length}
  <ul class="analyses" class:compact>
    {#each shown as a}
      <li>
        <span class="form" lang="hu">{guessed ? searched : a.form}</span>
        <span class="eq">{compact ? '→' : '='}</span>
        <span class="lemma" lang="hu">{headword}</span>
        {#each a.parts as p}
          <span class="plus">+</span>
          <span class="part" title={p.hint}>{p.label}{#if p.hint && !compact}<small>({p.hint})</small>{/if}</span>
        {/each}
        {#if compact && analyses.length > 1}<span class="more">+{analyses.length - 1} more</span>{/if}
        {#if guessed}<span class="guessed" title="Worked out by removing suffixes">guess</span>{/if}
      </li>
    {/each}
  </ul>
{/if}

<style>
  .analyses {
    list-style: none;
    padding: 0;
    margin: 10px 0 6px;
    display: grid;
    gap: 6px;
  }
  li {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 4px 6px;
    background: var(--accent-soft);
    border-radius: 8px;
    padding: 6px 10px;
    font-size: 0.95rem;
  }
  .compact {
    margin: 6px 0 4px;
  }
  .compact li {
    background: none;
    padding: 0;
    font-size: 0.92rem;
    color: var(--muted);
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
    color: var(--text);
  }
  .part small {
    color: var(--muted);
    font-size: 0.85em;
    margin-left: 0.3em;
  }
  .more {
    color: var(--faint);
    font-size: 0.85em;
  }
  .guessed {
    margin-left: auto;
    font-size: 0.75rem;
    color: var(--secondary);
    background: var(--secondary-soft);
    border-radius: 999px;
    padding: 1px 8px;
  }
  .compact .guessed {
    margin-left: 4px;
  }
</style>
