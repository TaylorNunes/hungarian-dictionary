<script lang="ts">
  // The seedling "ő": an o with two leaves for its accents. Paths come from scripts/make_icons.py
  // (glyph_svg), which also draws the app icons; regenerate both together if the shape changes.
  let { size = 30 }: { size?: number } = $props();
</script>

<svg viewBox="0 0 100 100" width={size} height={size} aria-hidden="true">
  <circle cx="50.00" cy="63.50" r="17.25" fill="none" stroke="currentColor" stroke-width="8.50" />
  <rect x="47.40" y="36.00" width="5.20" height="7.00" fill="currentColor" />
  <path d="M49.50 37.00Q49.48 16.02 28.50 16.50Q28.52 37.48 49.50 37.00Z" fill="currentColor" />
  <path d="M50.50 37.00Q71.48 37.48 71.50 16.50Q50.52 16.02 50.50 37.00Z" fill="currentColor" />
</svg>
