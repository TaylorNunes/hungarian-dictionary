<script lang="ts">
  import type { Example } from '../lib/types';

  let { ex }: { ex: Example } = $props();

  // Split the Hungarian sentence into words (each a link to look it up) and the text between.
  const pieces = $derived(ex[0].split(/(\p{L}+(?:-\p{L}+)*)/u));
</script>

<span class="hu" lang="hu">
  {#each pieces as piece, i}
    {#if i % 2 === 1}<a href={`#/w/${encodeURIComponent(piece.toLowerCase())}`}>{piece}</a>{:else}{piece}{/if}
  {/each}
</span>
<span class="en">{ex[1]}</span>

<style>
  .hu {
    display: block;
  }
  .hu a {
    color: inherit;
    text-decoration: none;
    border-bottom: 1px dotted var(--faint);
  }
  .hu a:hover {
    color: var(--accent);
    border-bottom-color: var(--accent);
  }
  .en {
    display: block;
    color: var(--muted);
  }
</style>
