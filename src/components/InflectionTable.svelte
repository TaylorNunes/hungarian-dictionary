<script lang="ts">
  import { getTags } from '../lib/data';
  import { buildSections, type Section } from '../lib/tags';

  let { table }: { table: [string, number][] } = $props();

  let sections = $state<Section[]>([]);
  $effect(() => {
    const t = table;
    getTags().then((tags) => (sections = buildSections(t, tags)));
  });
</script>

<div class="sections">
  {#each sections as s}
    <section>
      <h4>{s.title}</h4>
      {#if s.kind === 'grid'}
        <div class="scroll">
          <table>
            {#if s.cols.length > 1}
              <thead>
                <tr><th></th>{#each s.cols as c}<th scope="col">{c}</th>{/each}</tr>
              </thead>
            {/if}
            <tbody>
              {#each s.rows as row}
                <tr>
                  <th scope="row">
                    <span lang="hu">{row.head}</span>
                    {#if row.sub}<small>{row.sub}</small>{/if}
                  </th>
                  {#each row.cells as cell}
                    <td lang="hu">{cell.length ? cell.join(', ') : '—'}</td>
                  {/each}
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {:else}
        <dl>
          {#each s.items as item}
            <dt>{item.head}</dt>
            <dd lang="hu">{item.forms.join(', ')}</dd>
          {/each}
        </dl>
      {/if}
    </section>
  {/each}
</div>

<style>
  .sections {
    display: grid;
    gap: 16px;
    margin-top: 8px;
  }
  h4 {
    margin: 0 0 6px;
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
    font-weight: 600;
  }
  .scroll {
    overflow-x: auto;
  }
  table {
    border-collapse: collapse;
    width: 100%;
    font-size: 0.92rem;
    /* Never split a word form; narrow screens scroll the table sideways instead. */
    overflow-wrap: normal;
    word-break: keep-all;
  }
  td {
    min-width: 5.5em;
  }
  th,
  td {
    text-align: left;
    padding: 5px 8px;
    border-bottom: 1px solid var(--border);
    vertical-align: top;
  }
  thead th {
    color: var(--faint);
    font-weight: 500;
    font-size: 0.8rem;
  }
  tbody th {
    font-weight: 500;
    white-space: nowrap;
  }
  tbody th small {
    display: block;
    color: var(--faint);
    font-weight: 400;
    font-size: 0.75rem;
  }
  dl {
    display: grid;
    grid-template-columns: minmax(8rem, max-content) 1fr;
    gap: 4px 12px;
    margin: 0;
    font-size: 0.92rem;
  }
  dt {
    color: var(--muted);
  }
  dd {
    margin: 0;
  }
</style>
