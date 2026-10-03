<script lang="ts">
  import { entryHref } from '../lib/entry';
  import { saved, removeSaved, downloadAnki } from '../lib/saved.svelte';
</script>

<section>
  <div class="top">
    <h1>Saved words</h1>
    {#if saved.list.length}
      <button class="primary" onclick={() => downloadAnki(saved.list)}>Export for Anki</button>
    {/if}
  </div>

  {#if !saved.list.length}
    <p class="muted">Tap ☆ on any entry to save it here. You can export saved words as a file Anki can import.</p>
  {:else}
    <ul>
      {#each saved.list as s (s.lemmaId)}
        <li>
          <div class="text">
            <a href={entryHref({ w: s.word, pos: s.pos }, s.lemmaId)} lang="hu" class="word">{s.word}</a>
            {#if s.lookedUp}<span class="looked" lang="hu">from {s.lookedUp}</span>{/if}
            <span class="meaning">{s.meaning}</span>
            {#if s.example}<span class="example" lang="hu">{s.example[0]}</span>{/if}
          </div>
          <button class="remove" onclick={() => removeSaved(s.lemmaId)} aria-label={`Remove ${s.word}`}>Remove</button>
        </li>
      {/each}
    </ul>
    <p class="muted small">
      In Anki: <strong>File → Import</strong>, choose the downloaded file, and map the columns to your note type's fields.
      The file has Hungarian, English and an example sentence.
    </p>
  {/if}
</section>

<style>
  .top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  h1 {
    font-family: var(--serif);
    font-weight: 600;
    font-size: 1.5rem;
    margin: 8px 0;
  }
  .primary {
    background: var(--accent);
    color: var(--bg);
    border: 0;
    border-radius: 999px;
    padding: 8px 16px;
    font-weight: 600;
    cursor: pointer;
  }
  ul {
    list-style: none;
    padding: 0;
    margin: 12px 0;
    display: grid;
    gap: 10px;
  }
  li {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: flex-start;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 10px 14px;
  }
  .text {
    display: grid;
    gap: 2px;
    min-width: 0;
    overflow-wrap: anywhere;
  }
  .word {
    font-family: var(--serif);
    font-size: 1.2rem;
    font-weight: 600;
    text-decoration: none;
  }
  .looked,
  .example {
    color: var(--muted);
    font-size: 0.88rem;
  }
  .looked {
    font-style: italic;
  }
  .remove {
    border: 1px solid var(--border);
    background: none;
    color: var(--muted);
    border-radius: 999px;
    padding: 4px 12px;
    font-size: 0.85rem;
    cursor: pointer;
    flex-shrink: 0;
  }
  .muted {
    color: var(--muted);
  }
  .small {
    font-size: 0.9rem;
  }
</style>
