<script lang="ts">
  import { tick } from 'svelte';
  import { getManifest } from '../lib/data';
  import { allPos, describeLabel, emphasisRuns, labelName, labelsInGroup, LABEL_GROUPS } from '../lib/glossary';
  import { CASES, MOODS, PERSONS } from '../lib/tags';
  import type { Manifest } from '../lib/types';

  let { anchor }: { anchor?: string } = $props();

  let manifest = $state<Manifest | null>(null);
  getManifest()
    .then((m) => (manifest = m))
    .catch(() => {});

  const posRows = $derived(
    allPos()
      .map(([pos, info]) => ({ pos, info, count: manifest?.posCounts?.[pos] ?? 0 }))
      .filter((r) => !manifest?.posCounts || r.count > 0)
      .sort((a, b) => b.count - a.count || a.info.label.localeCompare(b.info.label)),
  );

  // Deep links (#/guide/proscribed) scroll to the entry and flash it.
  $effect(() => {
    const target = anchor;
    const ready = posRows.length; // re-run once counts have loaded and the table has re-rendered
    if (!target || !ready) return;
    tick().then(() => {
      const el = document.getElementById(`g-${target}`);
      if (!el) return;
      el.scrollIntoView({ block: 'start' });
      el.classList.remove('flash');
      void el.offsetWidth;
      el.classList.add('flash');
    });
  });

  const fmt = (n: number) => n.toLocaleString('en');
  const sample = describeLabel('proscribed');
</script>

{#snippet rich(text: string)}
  {#each emphasisRuns(text) as run}{#if run.em}<em lang="hu">{run.text}</em>{:else}{run.text}{/if}{/each}
{/snippet}

<article class="guide">
  <h1>Guide</h1>
  <p class="intro">What the labels on entries mean, and where the data comes from.</p>

  <nav class="toc" aria-label="On this page">
    <a href="#/guide/pos">Parts of speech</a>
    <a href="#/guide/commonness">“Very common” and “common”</a>
    <a href="#/guide/labels">Usage labels</a>
    <a href="#/guide/breakdown">Terms in word breakdowns</a>
  </nav>

  <section id="g-pos">
    <h2>Parts of speech</h2>
    <p>Shown in italics next to each headword. The Hungarian grammar term is given in case you meet it in a textbook.</p>
    <div class="scroll">
      <table class="pos-table">
        <tbody>
          {#each posRows as row (row.pos)}
            <tr id={`g-pos-${row.pos}`}>
              <th scope="row">
                <span class="pos">{row.info.label}</span>
                {#if row.info.hu}<small lang="hu">{row.info.hu}</small>{/if}
                {#if row.count}<small class="num">{fmt(row.count)} entries</small>{/if}
              </th>
              <td>{@render rich(row.info.text)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </section>

  <section id="g-commonness">
    <h2>“Very common” and “common”</h2>
    <p>
      <span class="badge">very common</span> marks the 1,000 most frequent Hungarian words, and
      <span class="badge">common</span> the next 4,000 (ranks 1,001–5,000). Hover over a badge to see the exact
      rank. Words without a badge are less frequent, or never appeared in the source. There is no separate
      “uncommon” badge.
    </p>
    <h3>How the ranking is calculated</h3>
    <ol class="steps">
      <li>
        <strong>Source.</strong> About 3.15 million Hungarian word forms with their counts, taken from film and TV
        subtitles (<a href="https://github.com/hermitdave/FrequencyWords" rel="noopener">FrequencyWords</a>,
        OpenSubtitles 2018). Subtitles are close to everyday spoken language. Forms seen only once are ignored as
        likely typos.
      </li>
      <li>
        <strong>Forms → dictionary words.</strong> The list counts word forms, not dictionary words: <em lang="hu">ház</em>,
        <em lang="hu">házat</em> and <em lang="hu">házban</em> are counted separately. Each form’s count is credited
        to the dictionary word(s) it belongs to, using this dictionary’s own inflection tables.
      </li>
      <li>
        <strong>Shared forms are split by weight.</strong> When several words share a form (<em lang="hu">fog</em> is
        both “tooth” and “will / to hold”), its count is divided between them. A word gets a bigger share when it has
        more everyday meanings, and when its dictionary form is itself common in the subtitles. Meanings labelled rare or
        archaic count little, and letters, names and suffixes get a small share.
      </li>
      <li>
        <strong>Ranking.</strong> Words are sorted by their total, giving a rank (1 = most frequent). For example
        <em lang="hu">van</em> “to be” is #2, <em lang="hu">ház</em> “house” #245, <em lang="hu">vonat</em>
        “train” around #1,400.
      </li>
    </ol>
    <h3>Limits</h3>
    <ul>
      <li>Subtitles favour conversation, so formal and technical words rank lower than they would in newspapers.</li>
      <li>
        Words spelled the same can’t be told apart reliably. <em lang="hu">ment</em> is both the past of
        <em lang="hu">megy</em> “went” and a verb “to save”, so the rank of “to save” is probably too high.
      </li>
      <li>Phrases of several words have no rank, because the source counts single words.</li>
    </ul>
    <p>
      The rank is also used to order search results (common words first), and adds <code>top1000</code> /
      <code>top5000</code> tags to cards in the Anki export.
    </p>
  </section>

  <section id="g-labels">
    <h2>Usage labels</h2>
    <p>
      The grey labels before a meaning come from Wiktionary. They describe how the word is used in that meaning,
      e.g. <span class="label">proscribed</span> {sample ? `— ${sample.text.charAt(0).toLowerCase()}${sample.text.slice(1)}` : ''}
    </p>
    {#each LABEL_GROUPS as group}
      <h3>{group}</h3>
      <dl class="labels">
        {#each labelsInGroup(group) as [tag, info] (tag)}
          <dt id={`g-${tag}`}><span class="label">{labelName(tag)}</span></dt>
          <dd>{@render rich(info.text)}</dd>
        {/each}
      </dl>
    {/each}
  </section>

  <section id="g-breakdown">
    <h2>Terms in word breakdowns</h2>
    <p>
      When you look up an inflected form, its breakdown names the endings: <em lang="hu">házat</em> = ház +
      accusative.
    </p>

    <h3 id="g-cases">Cases (noun endings)</h3>
    <div class="scroll">
      <table class="compact">
        <tbody>
          {#each CASES as [tag, label, hint] (tag)}
            <tr id={`g-case-${tag}`}><th scope="row">{label}</th><td>{hint}</td></tr>
          {/each}
        </tbody>
      </table>
    </div>

    <h3>Verb forms</h3>
    <dl class="labels">
      {#each Object.values(MOODS) as mood}
        <dt>{mood.label}</dt>
        <dd>{mood.hint ?? (mood.label === 'present' ? 'happens now or habitually' : mood.label === 'past' ? 'happened' : 'will happen')}</dd>
      {/each}
      <dt>indefinite</dt>
      <dd>Used when there is no object or a non-specific one: <em lang="hu">látok egy házat</em> “I see a house”.</dd>
      <dt>definite</dt>
      <dd>Used with a specific object (the, this, him/her…): <em lang="hu">látom a házat</em> “I see the house”.</dd>
      <dt>I → you</dt>
      <dd>The <em lang="hu">-lak/-lek</em> form: “I … you”, e.g. <em lang="hu">szeretlek</em> “I love you”.</dd>
      <dt>can</dt>
      <dd>The potential <em lang="hu">-hat/-het</em>: <em lang="hu">láthat</em> “can see, may see”.</dd>
      <dt>infinitive</dt>
      <dd>“To …”: <em lang="hu">látni</em> “to see”. The personal infinitive adds a person: <em lang="hu">látnom kell</em> “I have to see”.</dd>
    </dl>

    <h3>Persons</h3>
    <dl class="labels">
      {#each PERSONS as [key, pronoun, english] (key)}
        <dt lang="hu">{pronoun}</dt>
        <dd>{english}</dd>
      {/each}
    </dl>
  </section>
</article>

<style>
  .guide {
    overflow-wrap: anywhere;
  }
  h1 {
    font-family: var(--serif);
    font-weight: 600;
    font-size: 1.6rem;
    margin: 8px 0 4px;
  }
  .intro {
    color: var(--muted);
    margin-top: 0;
  }
  .toc {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin: 12px 0 8px;
  }
  .toc a {
    padding: 5px 12px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--border);
    text-decoration: none;
    font-size: 0.9rem;
  }
  section {
    margin-top: 28px;
  }
  h2 {
    font-family: var(--serif);
    font-weight: 600;
    font-size: 1.3rem;
    margin: 0 0 6px;
  }
  h3 {
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--faint);
    margin: 20px 0 6px;
    font-weight: 600;
  }
  p,
  li {
    max-width: 66ch;
  }
  /* Keep deep-link targets clear of the sticky header. */
  [id] {
    scroll-margin-top: 90px;
  }
  .scroll {
    overflow-x: auto;
  }
  table {
    border-collapse: collapse;
    width: 100%;
    font-size: 0.92rem;
  }
  th,
  td {
    text-align: left;
    vertical-align: top;
    padding: 6px 8px;
    border-bottom: 1px solid var(--border);
  }
  tbody th {
    font-weight: 500;
    white-space: nowrap;
  }
  .pos-table th {
    width: 9.5rem;
    white-space: normal;
  }
  .pos-table th small {
    display: block;
    font-size: 0.8rem;
    font-weight: 400;
    color: var(--muted);
  }
  .num {
    font-variant-numeric: tabular-nums;
    color: var(--faint) !important;
  }
  table {
    overflow-wrap: normal;
  }
  .pos {
    color: var(--accent);
    font-style: italic;
  }
  .compact th {
    width: 40%;
  }
  .steps li {
    margin: 8px 0;
  }
  .labels {
    display: grid;
    grid-template-columns: minmax(7rem, max-content) 1fr;
    gap: 6px 14px;
    margin: 0;
    font-size: 0.95rem;
  }
  dt {
    font-weight: 500;
  }
  dd {
    margin: 0;
  }
  .label {
    display: inline-block;
    font-size: 0.8rem;
    font-weight: 400;
    color: var(--muted);
    background: var(--surface-2);
    border-radius: 4px;
    padding: 0 6px;
  }
  .badge {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--accent-strong);
    background: var(--accent-soft);
    border-radius: 999px;
    padding: 1px 8px;
    white-space: nowrap;
  }
  code {
    font-size: 0.88em;
    background: var(--surface-2);
    padding: 0 4px;
    border-radius: 4px;
  }
  :global(.flash) {
    animation: flash 1.6s ease-out;
    border-radius: 6px;
  }
  @keyframes flash {
    0%,
    30% {
      background: var(--secondary-soft);
    }
    100% {
      background: transparent;
    }
  }
  @media (max-width: 480px) {
    .labels {
      grid-template-columns: 1fr;
      gap: 2px;
    }
    .labels dd {
      margin-bottom: 8px;
    }
  }
</style>
