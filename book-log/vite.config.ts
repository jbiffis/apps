import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Deployed at apps.biffis.com/bookstory
export default defineConfig({
  base: '/bookstory/',
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // Caching disabled for now so changes are visible immediately.
      // The generated SW unregisters itself and clears caches on next visit.
      selfDestroying: true,
      includeAssets: [
        'favicon.svg',
        'icons/apple-touch-icon.png',
      ],
      manifest: {
        id: '/bookstory/',
        name: 'BookStory — A cozy reading clubhouse',
        short_name: 'BookStory',
        description: 'Track books, earn badges, conquer your reading quest!',
        start_url: '/bookstory/',
        scope: '/bookstory/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#FBF3E4',
        theme_color: '#D9613A',
        icons: [
          {
            src: 'icons/icon-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: 'icons/icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: 'icons/maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest}'],
        navigateFallback: '/bookstory/index.html',
        navigateFallbackDenylist: [/^\/bookstory\/api\//],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/covers\.openlibrary\.org\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'openlibrary-covers',
              expiration: {
                maxEntries: 500,
                maxAgeSeconds: 60 * 60 * 24 * 90,
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /^https:\/\/books\.google\.com\/books\/content.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'googlebooks-covers',
              expiration: {
                maxEntries: 500,
                maxAgeSeconds: 60 * 60 * 24 * 90,
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /^https:\/\/openlibrary\.org\/api\/books.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'openlibrary-api',
              networkTimeoutSeconds: 5,
              expiration: { maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
})
