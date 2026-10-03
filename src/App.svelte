<script lang="ts">
  import { onMount, tick } from 'svelte';
  import { APP_NAME, APP_TAGLINE } from './config';
  import { getManifest, pruneOldShards } from './lib/data';
  import { searchBoth, cleanQuery, type CombinedResponse } from './lib/search';
  import { saved } from './lib/saved.svelte';
  import { parseEntryHash, type EntryRoute } from './lib/entry';
  import Entry from './components/Entry.svelte';
  import Result from './components/Result.svelte';
  import SavedList from './components/SavedList.svelte';
  import About from './components/About.svelte';

  type View = 'search' | 'entry' | 'saved' | 'about';

  let view = $state<View>('search');
  let query = $state('');
  let response = $state<CombinedResponse | null>(null);
  let loading = $state(false);
  let error = $state('');
  let input: HTMLInputElement | undefined = $state();
  let entryRoute = $state<EntryRoute | null>(null);
  let backQuery = $state<string | undefined>();
  let backViaHistory = $state(false);
  let seq = 0;
  let debounce: ReturnType<typeof setTimeout> | undefined;

  // Recent searches and their scroll positions, so returning from a word page is instant and in place.
  const cache = new Map<string, CombinedResponse>();
  const scrollPositions = new Map<string, number>();
  const CACHE_SIZE = 20;

  function parseHash(): { view: View; word: string; entry?: EntryRoute } {
    const h = location.hash.replace(/^#\/?/, '');
    if (h === 'saved') return { view: 'saved', word: '' };
    if (h === 'about') return { view: 'about', word: '' };
    const entry = parseEntryHash(location.hash);
    if (entry) return { view: 'entry', word: '', entry };
    const m = h.match(/^w\/(.*)$/);
    return { view: 'search', word: m ? decodeURIComponent(m[1]) : '' };
  }

  async function run(q: string) {
    const id = ++seq;
    const clean = cleanQuery(q);
    if (!clean) {
      response = null;
      loading = false;
      error = '';
      return;
    }
    const cached = cache.get(clean);
    if (cached) {
      response = cached;
      loading = false;
      error = '';
      return;
    }
    loading = true;
    try {
      const r = await searchBoth(clean);
      if (id !== seq) return;
      response = r;
      error = '';
      cache.set(clean, r);
      if (cache.size > CACHE_SIZE) cache.delete(cache.keys().next().value!);
    } catch (e) {
      if (id !== seq) return;
      // fetch() rejects with a TypeError when the network is unreachable.
      const offline = !navigator.onLine || e instanceof TypeError;
      error = offline
        ? "You're offline and this part of the dictionary isn't saved on this device yet. Download everything for offline use under About."
        : `Couldn't load the dictionary data (${(e as Error).message}).`;
    } finally {
      if (id === seq) loading = false;
    }
  }

  function setHashForQuery(q: string, replace: boolean) {
    const clean = cleanQuery(q);
    const target = clean ? `#/w/${encodeURIComponent(clean)}` : '#/';
    if (location.hash === target) return;
    if (replace) history.replaceState(null, '', target);
    else history.pushState(null, '', target);
    lastHash = location.hash;
  }

  function onInput() {
    clearTimeout(debounce);
    debounce = setTimeout(() => {
      // Typing refines the current search rather than adding history steps.
      setHashForQuery(query, parseHash().word !== '');
      view = 'search';
      run(query);
    }, 180);
  }

  function onSubmit(e: SubmitEvent) {
    e.preventDefault();
    clearTimeout(debounce);
    setHashForQuery(query, false);
    view = 'search';
    run(query);
    input?.blur();
  }

  function clear() {
    query = '';
    response = null;
    setHashForQuery('', false);
    input?.focus();
  }

  let lastHash: string | null = null;

  function rememberScroll() {
    if (view === 'search' && response) scrollPositions.set(response.query, window.scrollY);
  }

  async function syncFromHash() {
    // Going back fires both popstate and hashchange; handle each address once.
    if (location.hash === lastHash) return;
    lastHash = location.hash;
    const from = view;
    const h = parseHash();

    if (h.entry) {
      // Opened from the results: "back" is a history step, which keeps their scroll position.
      backViaHistory = from === 'search' && !!response;
      backQuery = from === 'search' ? response?.query : undefined;
      entryRoute = h.entry;
      view = 'entry';
      query = h.entry.from ?? h.entry.word;
      window.scrollTo({ top: 0 });
      return;
    }

    view = h.view;
    document.title = APP_NAME;
    if (h.view === 'search') {
      if (h.word !== query) query = h.word;
      await run(h.word);
      await tick();
      window.scrollTo({ top: scrollPositions.get(cleanQuery(h.word)) ?? 0 });
    }
  }

  onMount(() => {
    syncFromHash();
    window.addEventListener('hashchange', syncFromHash);
    window.addEventListener('popstate', syncFromHash);
    window.addEventListener('scroll', rememberScroll, { passive: true });
    getManifest().then(pruneOldShards).catch(() => {});
    if (!parseHash().word && view === 'search' && matchMedia('(pointer: fine)').matches) input?.focus();
    return () => {
      window.removeEventListener('hashchange', syncFromHash);
      window.removeEventListener('popstate', syncFromHash);
      window.removeEventListener('scroll', rememberScroll);
    };
  });
</script>

<header class="top">
  <div class="bar">
    <a class="brand" href="#/" onclick={() => (query = '')}>
      <span class="logo" aria-hidden="true">ő</span>
      <span class="name">{APP_NAME}</span>
    </a>
    <nav aria-label="Main">
      <a href="#/" aria-current={view === 'search' ? 'page' : undefined}>Search</a>
      <a href="#/saved" aria-current={view === 'saved' ? 'page' : undefined}>
        Saved{#if saved.list.length}<span class="count">{saved.list.length}</span>{/if}
      </a>
      <a href="#/about" aria-current={view === 'about' ? 'page' : undefined}>About</a>
    </nav>
  </div>

  {#if view === 'search' || view === 'entry'}
    <form class="search" role="search" onsubmit={onSubmit}>
      <label for="q" class="visually-hidden">Hungarian or English word</label>
      <input
        id="q"
        bind:this={input}
        bind:value={query}
        oninput={onInput}
        type="search"
        lang="hu"
        placeholder="Hungarian or English word…"
        autocomplete="off"
        autocapitalize="off"
        autocorrect="off"
        spellcheck="false"
        enterkeyhint="search"
      />
      {#if query}
        <button type="button" class="clear" onclick={clear} aria-label="Clear search">×</button>
      {/if}
    </form>
  {/if}
</header>

<main>
  {#if view === 'entry' && entryRoute}
    <Entry route={entryRoute} {backQuery} {backViaHistory} />
  {:else if view === 'saved'}
    <SavedList />
  {:else if view === 'about'}
    <About />
  {:else}
    {#if error}
      <p class="notice" role="alert">{error}</p>
    {:else if response}
      {#if response.tokens.length}
        {#if !response.sections.length}<p class="status">Tap a word to look it up.</p>{/if}
        <div class="tokens" aria-label="Words in your text">
          {#each response.tokens as t}
            <a href={`#/w/${encodeURIComponent(t)}`} lang="hu">{t}</a>
          {/each}
        </div>
      {/if}

      {#each response.sections as section (section.lang)}
        <section class="results-section">
          {#if section.lang === 'en'}
            <h2 class="section-head">English <span lang="en">“{section.term}”</span> → Hungarian</h2>
          {:else if response.sections.length > 1}
            <h2 class="section-head">Hungarian <span lang="hu">{section.term}</span></h2>
          {/if}
          <p class="status" aria-live="polite">
            {section.results.length === 1 ? '1 entry' : `${section.results.length} entries`}
            {#if section.lang === 'hu' && section.results[0].guessed}
              · <span class="guess">no exact form in the tables; best guess by removing suffixes</span>
            {:else if section.lang === 'en' && section.results[0].guessed}
              · <span class="guess">matched the base form “{section.term}”</span>
            {/if}
          </p>
          <ol class="results">
            {#each section.results as r (r.lemmaId)}
              <li><Result result={r} query={response.query} /></li>
            {/each}
          </ol>
        </section>
      {:else}
        {#if !loading && !response.tokens.length}
          <div class="empty">
            <p>No entry for <strong>{response.query}</strong> in Hungarian or English.</p>
            {#if response.suggestions.length}
              <p>Did you mean:</p>
              <div class="tokens">
                {#each response.suggestions as s}
                  <a href={`#/w/${encodeURIComponent(s)}`} lang="hu">{s}</a>
                {/each}
              </div>
            {/if}
          </div>
        {/if}
      {/each}
    {:else if !query}
      <section class="welcome">
        <h1>{APP_TAGLINE}</h1>
        <p>
          Paste any form of a Hungarian word to see its dictionary form, meaning, how it's built and example sentences,
          or type an English word to find the Hungarian.
        </p>
        <p class="try">
          Try
          {#each ['házat', 'könyveimben', 'láttalak', 'szeretném', 'house', 'beautiful', 'to see'] as w, i}
            {#if i}, {/if}<a href={`#/w/${encodeURIComponent(w)}`}>{w}</a>
          {/each}
        </p>
        <p class="hint">Accents are optional: <em lang="hu">orom</em> finds <em lang="hu">öröm</em>.</p>
      </section>
    {/if}
    {#if loading}
      <p class="loading" aria-live="polite">Looking up…</p>
    {/if}
  {/if}
</main>

<style>
  .top {
    position: sticky;
    top: 0;
    z-index: 10;
    background: color-mix(in srgb, var(--bg) 92%, transparent);
    backdrop-filter: blur(8px);
    border-bottom: 1px solid var(--border);
    padding: env(safe-area-inset-top) 0 0;
  }
  .bar,
  .search,
  main {
    max-width: 760px;
    margin: 0 auto;
    padding-left: 16px;
    padding-right: 16px;
  }
  .bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-top: 10px;
    padding-bottom: 8px;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 8px;
    text-decoration: none;
    color: var(--text);
    font-weight: 650;
    font-size: 1.1rem;
  }
  .logo {
    display: grid;
    place-items: center;
    width: 30px;
    height: 30px;
    border-radius: 8px;
    background: var(--accent);
    color: var(--bg);
    font-family: var(--serif);
    font-size: 1.25rem;
    line-height: 1;
    padding-bottom: 2px;
  }
  nav {
    display: flex;
    gap: 4px;
  }
  nav a {
    padding: 6px 10px;
    border-radius: 999px;
    color: var(--muted);
    text-decoration: none;
    font-size: 0.95rem;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  nav a[aria-current='page'] {
    background: var(--accent-soft);
    color: var(--accent-strong);
  }
  .count {
    background: var(--accent);
    color: var(--bg);
    border-radius: 999px;
    font-size: 0.75rem;
    padding: 0 6px;
    min-width: 20px;
    text-align: center;
  }
  .search {
    position: relative;
    padding-bottom: 12px;
  }
  .search input {
    width: 100%;
    font: inherit;
    font-size: 1.15rem;
    padding: 12px 44px 12px 16px;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
    -webkit-appearance: none;
    appearance: none;
  }
  .search input::-webkit-search-cancel-button {
    display: none;
  }
  .search input:focus {
    outline: none;
    border-color: var(--accent);
    box-shadow: 0 0 0 3px var(--accent-soft);
  }
  .clear {
    position: absolute;
    right: 24px;
    top: 7px;
    width: 36px;
    height: 36px;
    border: 0;
    background: none;
    color: var(--faint);
    font-size: 1.6rem;
    line-height: 1;
    cursor: pointer;
    border-radius: 50%;
  }
  main {
    padding-top: 12px;
    padding-bottom: calc(48px + env(safe-area-inset-bottom));
  }
  .results-section + .results-section {
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
  .results {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 14px;
  }
  .status,
  .loading {
    color: var(--muted);
    font-size: 0.9rem;
    margin: 4px 2px 12px;
  }
  .guess {
    color: var(--warn);
  }
  .notice {
    background: var(--warn-soft);
    color: var(--warn);
    padding: 12px 14px;
    border-radius: var(--radius);
  }
  .tokens {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin: 4px 0 16px;
  }
  .tokens a {
    padding: 6px 12px;
    border-radius: 999px;
    background: var(--surface);
    border: 1px solid var(--border);
    text-decoration: none;
  }
  .empty {
    color: var(--muted);
  }
  .welcome h1 {
    font-family: var(--serif);
    font-weight: 600;
    font-size: 1.6rem;
    margin: 16px 0 8px;
  }
  .welcome p {
    color: var(--muted);
    max-width: 52ch;
  }
  .welcome .try a {
    font-weight: 550;
  }
  .hint {
    font-size: 0.9rem;
  }
</style>
