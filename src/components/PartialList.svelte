<script lang="ts">
  import { entryHref } from '../lib/entry';
  import { fold } from '../lib/fold';
  import type { PartialMatch } from '../lib/search';

  const PAGE = 20;

  let {
    matches,
    query,
    shown = PAGE,
    onshow,
  }: {
    matches: PartialMatch[];
    query: string;
    /** How many rows are visible; kept by the caller so going back restores it. */
    shown?: number;
    onshow: (n: number) => void;
  } = $props();

  const length = $derived([...fold(query)].length);
  const visible = $derived(matches.slice(0, shown));

  /** The headword split into runs, with the part matching the query flagged. */
  function runs(m: PartialMatch): { text: string; hit: boolean }[] {
    const chars = [...m.word];
    const out: { text: string; hit: boolean }[] = [];
    chars.forEach((c, i) => {
      const hit = (m.start && i < length) || (m.end && i >= chars.length - length);
      const last = out[out.length - 1];
      if (last && last.hit === hit) last.text += c;
      else out.push({ text: c, hit });
    });
    return out;
  }
</script>

<section class="partial">
  <h2 class="section-head">Words starting or ending with <span lang="hu">“{query}”</span></h2>
  <p class="status">{matches.length === 1 ? '1 word' : `${matches.length}${matches.length >= 200 ? '+' : ''} words`}</p>
  <ul>
    {#each visible as m (m.lemmaId)}
      <li>
        <a href={entryHref({ w: m.word, pos: m.pos }, m.lemmaId)}>
          <span class="word" lang="hu">{#each runs(m) as r}{#if r.hit}<mark>{r.text}</mark>{:else}{r.text}{/if}{/each}</span>
          <span class="pos">{m.pos}</span>
          <span class="gloss">{m.gloss}</span>
        </a>
      </li>
    {/each}
  </ul>
  {#if matches.length > shown}
    <button class="more" onclick={() => onshow(shown + PAGE)}>
      Show {Math.min(PAGE, matches.length - shown)} more
    </button>
  {/if}
</section>

<style>
  .partial {
    margin-top: 28px;
  }
  .section-head {
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
    font-weight: 600;
    margin: 4px 2px 0;
  }
  .section-head span {
    text-transform: none;
    letter-spacing: 0;
    color: var(--text);
    font-size: 1rem;
  }
  .status {
    color: var(--muted);
    font-size: 0.9rem;
    margin: 4px 2px 8px;
  }
  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
  }
  li + li {
    border-top: 1px solid var(--border);
  }
  a {
    display: flex;
    align-items: baseline;
    gap: 8px;
    padding: 9px 14px;
    text-decoration: none;
    color: var(--text);
    min-width: 0;
  }
  a:hover {
    background: var(--surface-2);
  }
  .word {
    font-family: var(--serif);
    font-weight: 600;
    font-size: 1.05rem;
    white-space: nowrap;
  }
  mark {
    background: none;
    color: var(--accent);
  }
  .pos {
    color: var(--faint);
    font-style: italic;
    font-size: 0.85rem;
    white-space: nowrap;
  }
  .gloss {
    color: var(--muted);
    font-size: 0.92rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    min-width: 0;
  }
  .more {
    display: block;
    margin: 12px auto 0;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--accent);
    border-radius: 999px;
    padding: 7px 18px;
    font: inherit;
    font-size: 0.92rem;
    cursor: pointer;
  }
  .more:hover {
    border-color: var(--accent);
  }
</style>
