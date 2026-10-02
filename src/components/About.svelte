<script lang="ts">
  import { onMount } from 'svelte';
  import { APP_NAME } from '../config';
  import { downloadAll, getManifest, offlineStatus } from '../lib/data';
  import type { Manifest } from '../lib/types';

  let manifest = $state<Manifest | null>(null);
  let status = $state<{ cached: number; total: number } | null>(null);
  let progress = $state<{ done: number; total: number } | null>(null);
  let message = $state('');
  let controller: AbortController | null = null;

  const canCache = typeof window !== 'undefined' && 'caches' in window;
  const complete = $derived(!!status && status.total > 0 && status.cached === status.total);

  onMount(async () => {
    try {
      manifest = await getManifest();
      if (canCache) status = await offlineStatus();
    } catch {
      message = "Couldn't load the data manifest.";
    }
  });

  async function startDownload() {
    message = '';
    controller = new AbortController();
    try {
      await navigator.storage?.persist?.();
      await downloadAll((done, total) => (progress = { done, total }), controller.signal);
      message = 'Done. The whole dictionary now works offline.';
    } catch (e) {
      message = (e as Error).name === 'AbortError' ? 'Paused. Start again to resume where it stopped.' : `Download stopped: ${(e as Error).message}`;
    } finally {
      controller = null;
      progress = null;
      status = await offlineStatus();
    }
  }

  const mb = (n: number) => `${Math.round(n / 1e6)} MB`;
</script>

<section>
  <h1>About {APP_NAME}</h1>
  <p>
    A free Hungarian → English dictionary. Type or paste any form of a word to see its dictionary form, meaning,
    a breakdown of its endings, its full inflection table and example sentences.
  </p>

  <h2>Offline use</h2>
  <p>
    Words you look up are saved on this device automatically. To use the whole dictionary without a connection,
    download everything{#if manifest} (about {mb(manifest.bytes)}, less over the network since it's compressed){/if}.
  </p>
  {#if !canCache}
    <p class="muted">This browser doesn't support offline storage.</p>
  {:else if progress}
    <div class="progress">
      <progress max={progress.total} value={progress.done}></progress>
      <span>{progress.done} / {progress.total} files</span>
      <button onclick={() => controller?.abort()}>Pause</button>
    </div>
  {:else if complete}
    <p class="ok">✓ Everything is available offline.</p>
  {:else}
    <button class="primary" onclick={startDownload} disabled={!manifest}>
      {status?.cached ? `Continue download (${status.cached} of ${status.total} files saved)` : 'Download for offline use'}
    </button>
  {/if}
  {#if message}<p class="muted" role="status">{message}</p>{/if}

  <h2>Install</h2>
  <p>
    On Android, open the browser menu and choose <strong>Install app</strong> or <strong>Add to home screen</strong>.
    On iPhone, use <strong>Share → Add to Home Screen</strong>.
  </p>

  <h2>Sources and licences</h2>
  <ul>
    <li>
      Definitions and inflection tables: <a href="https://en.wiktionary.org/" rel="noopener">English Wiktionary</a>
      contributors, extracted by <a href="https://kaikki.org/dictionary/Hungarian/" rel="noopener">Kaikki.org</a> (Wiktextract).
      Licensed <a href="https://creativecommons.org/licenses/by-sa/4.0/" rel="noopener">CC BY-SA 4.0</a>.
      Verb conjugation labels were reconstructed from the table layout and may occasionally be wrong.
    </li>
    <li>
      Example sentences: <a href="https://tatoeba.org/" rel="noopener">Tatoeba</a> contributors, licensed
      <a href="https://creativecommons.org/licenses/by/2.0/fr/" rel="noopener">CC BY 2.0 FR</a>.
    </li>
    <li>App code: MIT licence.</li>
  </ul>
  {#if manifest}
    <p class="muted small">
      Data version {manifest.version}, built {new Date(manifest.built).toLocaleDateString()} ·
      {manifest.lemmaCount.toLocaleString()} entries · {manifest.formCount.toLocaleString()} word forms
      {#if manifest.partial}· partial development build{/if}
    </p>
  {/if}
</section>

<style>
  h1 {
    font-family: var(--serif);
    font-weight: 600;
    font-size: 1.5rem;
    margin: 8px 0;
  }
  h2 {
    font-size: 1.05rem;
    margin: 24px 0 6px;
  }
  p,
  li {
    max-width: 62ch;
  }
  ul {
    padding-left: 1.2em;
  }
  li {
    margin: 6px 0;
  }
  .primary {
    background: var(--accent);
    color: var(--bg);
    border: 0;
    border-radius: 999px;
    padding: 10px 18px;
    font-weight: 600;
    cursor: pointer;
  }
  .primary:disabled {
    opacity: 0.5;
  }
  .progress {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  progress {
    flex: 1;
    min-width: 160px;
    accent-color: var(--accent);
  }
  .progress button {
    border: 1px solid var(--border);
    background: none;
    border-radius: 999px;
    padding: 4px 12px;
    cursor: pointer;
  }
  .ok {
    color: var(--accent);
    font-weight: 550;
  }
  .muted {
    color: var(--muted);
  }
  .small {
    font-size: 0.85rem;
  }
</style>
