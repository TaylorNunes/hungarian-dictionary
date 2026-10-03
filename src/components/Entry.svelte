<script lang="ts">
  import { APP_NAME } from '../config';
  import { findEntry } from '../lib/data';
  import type { EntryRoute } from '../lib/entry';
  import { search, type Analysis } from '../lib/search';
  import type { Lemma } from '../lib/types';
  import Breakdown from './Breakdown.svelte';
  import InflectionTable from './InflectionTable.svelte';
  import Senses from './Senses.svelte';
  import Sentence from './Sentence.svelte';
  import WordHeader from './WordHeader.svelte';

  let {
    route,
    backQuery,
    backViaHistory = false,
  }: {
    route: EntryRoute;
    /** The search this page was opened from, for the back link. */
    backQuery?: string;
    /** Going back is a history step (keeps the results' scroll position) rather than a new search. */
    backViaHistory?: boolean;
  } = $props();

  let status = $state<'loading' | 'ready' | 'missing' | 'error'>('loading');
  let errorMessage = $state('');
  let entry = $state<{ id: number; lemma: Lemma } | null>(null);
  let analyses = $state<Analysis[]>([]);
  let guessed = $state(false);
  let seq = 0;

  $effect(() => {
    load(route);
  });

  async function load(r: EntryRoute) {
    const id = ++seq;
    status = 'loading';
    analyses = [];
    guessed = false;
    try {
      const found = await findEntry(r.word, r.pos, r.id);
      if (id !== seq) return;
      if (!found) {
        status = 'missing';
        return;
      }
      entry = found;
      status = 'ready';
      document.title = `${found.lemma.w} – ${APP_NAME}`;
      if (r.from) {
        const match = (await search(r.from)).results.find((x) => x.lemmaId === found.id);
        if (id !== seq || !match) return;
        analyses = match.analyses;
        guessed = match.guessed;
      }
    } catch (e) {
      if (id !== seq) return;
      status = 'error';
      errorMessage =
        !navigator.onLine || e instanceof TypeError
          ? "You're offline and this word isn't saved on this device yet. Download everything for offline use under About."
          : `Couldn't load this word (${(e as Error).message}).`;
    }
  }

  function goBack(e: MouseEvent) {
    if (backViaHistory) {
      e.preventDefault();
      history.back();
    }
  }

  const lemma = $derived(entry?.lemma);
  const back = $derived(backQuery ?? route.from ?? route.word);
</script>

<nav class="back">
  <a href={`#/w/${encodeURIComponent(back)}`} onclick={goBack}>← Results for <span lang="hu">“{back}”</span></a>
</nav>

{#if status === 'loading'}
  <p class="muted" aria-live="polite">Loading…</p>
{:else if status === 'missing'}
  <p class="muted">No entry for <strong lang="hu">{route.word}</strong>.</p>
{:else if status === 'error'}
  <p class="notice" role="alert">{errorMessage}</p>
{:else if lemma && entry}
  <article class="page">
    <WordHeader {lemma} lemmaId={entry.id} query={route.from ?? ''} level="h1" />
    {#if route.from}
      <Breakdown {analyses} headword={lemma.w} searched={route.from} {guessed} />
    {/if}

    <section>
      <h2>Meanings</h2>
      <Senses senses={lemma.s} examples />
    </section>

    {#if lemma.ex?.length}
      <section>
        <h2>Examples</h2>
        <ul class="examples">
          {#each lemma.ex as ex}<li><Sentence {ex} /></li>{/each}
        </ul>
      </section>
    {/if}

    {#if lemma.t?.length}
      <section>
        <h2>{lemma.pos === 'verb' ? 'Conjugation' : 'Forms'}</h2>
        <InflectionTable table={lemma.t} collapseSecondary />
      </section>
    {/if}
  </article>
{/if}

<style>
  .back {
    margin: 0 0 12px;
  }
  .back a {
    text-decoration: none;
    font-size: 0.95rem;
  }
  .page {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow);
    padding: 18px 16px 16px;
    overflow-wrap: anywhere;
  }
  section {
    margin-top: 20px;
    border-top: 1px solid var(--border);
    padding-top: 12px;
  }
  h2 {
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--faint);
    margin: 0 0 6px;
    font-weight: 600;
  }
  .examples {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    gap: 10px;
    font-size: 0.95rem;
  }
  .muted {
    color: var(--muted);
  }
  .notice {
    background: var(--warn-soft);
    color: var(--warn);
    padding: 12px 14px;
    border-radius: var(--radius);
  }
</style>
