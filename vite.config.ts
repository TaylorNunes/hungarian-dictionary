import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { VitePWA } from 'vite-plugin-pwa';

// Keep in sync with APP_NAME / APP_SLOGAN in src/config.ts.
const APP_NAME = 'Szókert';
const APP_SLOGAN = 'Cultivate your knowledge.';

// Relative asset URLs, so the same build works at a domain root (szokert.org) and under a
// sub-path (taylornunes.github.io/szokert/). Routing is hash-based, so the page's own path
// never changes. Set BASE_PATH to force an absolute base.
const base = process.env.BASE_PATH ?? './';

export default defineConfig({
  base,
  plugins: [
    svelte(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/icon.svg', 'icons/icon-192.png'],
      manifest: {
        name: `${APP_NAME} — Hungarian dictionary`,
        short_name: APP_NAME,
        description: `${APP_SLOGAN} A Hungarian ⇄ English dictionary with word breakdowns and example sentences, offline.`,
        lang: 'en',
        start_url: '.',
        scope: '.',
        display: 'standalone',
        background_color: '#121212',
        theme_color: '#121212',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-512-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,webmanifest}'],
        globIgnores: ['data/**'],
        navigateFallback: 'index.html',
        runtimeCaching: [
          {
            // The manifest names the current data version; always try the network first.
            urlPattern: ({ url }) => url.pathname.endsWith('/data/manifest.json'),
            handler: 'NetworkFirst',
            options: { cacheName: 'data-manifest', networkTimeoutSeconds: 4 },
          },
          {
            // Shards live under data/<version>/ and never change once published.
            urlPattern: ({ url }) => /\/data\/[^/]+\/.+\.json$/.test(url.pathname),
            handler: 'CacheFirst',
            options: { cacheName: 'data-shards', cacheableResponse: { statuses: [200] } },
          },
        ],
      },
    }),
  ],
  test: {
    // core_cases.test.ts checks the shared fixtures the Android port is tested against.
    include: ['src/**/*.test.ts', 'scripts/core_cases.test.ts'],
  },
});
