import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { VitePWA } from 'vite-plugin-pwa';

// Keep in sync with APP_NAME in src/config.ts.
const APP_NAME = 'Szóda';

// GitHub Pages serves project sites from /<repo>/; the deploy workflow sets BASE_PATH.
const base = process.env.BASE_PATH ?? '/';

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
        description: 'Offline Hungarian–English dictionary with word breakdowns and example sentences.',
        lang: 'en',
        start_url: '.',
        scope: '.',
        display: 'standalone',
        background_color: '#f7f5ef',
        theme_color: '#2f6b4f',
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
    include: ['src/**/*.test.ts'],
  },
});
