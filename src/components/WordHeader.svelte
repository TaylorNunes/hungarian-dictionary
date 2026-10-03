<script lang="ts">
  import { commonness } from '../lib/frequency';
  import { describePos, guideHref } from '../lib/glossary';
  import { isSaved, toggleSaved } from '../lib/saved.svelte';
  import type { Lemma } from '../lib/types';

  let {
    lemma,
    lemmaId,
    query = '',
    href,
    level = 'h2',
  }: {
    lemma: Lemma;
    lemmaId: number;
    /** What was searched, remembered with a saved word. */
    query?: string;
    /** On a results card, the headword links to the word page and the whole card is its tap target. */
    href?: string;
    level?: 'h1' | 'h2';
  } = $props();

  const pos = $derived(describePos(lemma.pos));
  const saved = $derived(isSaved(lemmaId));
  const common = $derived(commonness(lemma.fr));
</script>

<header class:page={level === 'h1'}>
  <div class="head">
    <svelte:element this={level} lang="hu">
      {#if href}<a {href} class="stretched">{lemma.w}</a>{:else}{lemma.w}{/if}
    </svelte:element>
    {#if href}
      <span class="pos" title={pos.text.replace(/\*/g, '')}>{pos.label}</span>
      {#if common}<span class="common" title={common.title}>{common.label}</span>{/if}
    {:else}
      <!-- On the word page, the part of speech and badge link to their explanations. -->
      <a class="pos" href={guideHref.pos(lemma.pos)} title={pos.text.replace(/\*/g, '')}>{pos.label}</a>
      {#if common}<a class="common" href={guideHref.commonness} title={common.title}>{common.label}</a>{/if}
    {/if}
    {#if lemma.ipa}<span class="ipa">{lemma.ipa}</span>{/if}
  </div>
  <button
    class="star"
    class:on={saved}
    aria-pressed={saved}
    aria-label={saved ? `Remove ${lemma.w} from saved words` : `Save ${lemma.w}`}
    title={saved ? 'Saved' : 'Save for review / Anki'}
    onclick={() => toggleSaved(lemmaId, lemma, query)}
  >
    {saved ? '★' : '☆'}
  </button>
</header>

<style>
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
  h1,
  h2 {
    font-family: var(--serif);
    font-weight: 600;
    margin: 0;
    line-height: 1.2;
  }
  h2 {
    font-size: 1.65rem;
  }
  h1 {
    font-size: 2.2rem;
  }
  .stretched {
    color: inherit;
    text-decoration: none;
  }
  /* The link covers the whole card (the nearest positioned ancestor). */
  .stretched::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: var(--radius);
  }
  .stretched:focus-visible {
    outline: none;
  }
  .stretched:focus-visible::after {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
  .pos {
    color: var(--accent);
    font-style: italic;
    font-size: 0.95rem;
  }
  a.pos,
  a.common {
    text-decoration: none;
  }
  a.pos:hover {
    text-decoration: underline;
  }
  .common {
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--accent-strong);
    background: var(--accent-soft);
    border-radius: 999px;
    padding: 1px 8px;
    align-self: center;
    cursor: help;
    position: relative;
    z-index: 1;
  }
  .ipa {
    color: var(--faint);
    font-size: 0.9rem;
  }
  .star {
    position: relative;
    z-index: 1;
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
</style>
