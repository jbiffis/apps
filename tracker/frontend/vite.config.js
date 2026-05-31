import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Deployed at apps.biffis.com/tracker — see tracker-proxy.conf for the
// reverse-proxy + SPA-fallback wiring in the shared Apache.
export default defineConfig({
  base: '/tracker/',
  server: {
    // Local dev: Vite at :5173, API at :8080. The client calls base-relative
    // `/tracker/api/...` (same as prod); strip the base prefix when forwarding
    // so the backend sees plain `/api/...`. Keeps the fetch client identical
    // in dev and prod.
    proxy: {
      '/tracker/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/tracker/, ''),
      },
    },
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'icons/apple-touch-icon.png'],
      manifest: {
        id: '/tracker/',
        name: 'LifeTracker',
        short_name: 'LifeTracker',
        description: 'Track events around health and daily life.',
        start_url: '/tracker/',
        scope: '/tracker/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#f6efe4',
        theme_color: '#329f5b',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // No caching while we iterate: precache nothing and route every request
        // straight to the network. The service worker exists only to make the
        // app installable — there is never a stale asset to clear during
        // testing. Add precaching/offline support here once the UI settles.
        globPatterns: [],
        navigateFallback: null,
        cleanupOutdatedCaches: true,
        skipWaiting: true,
        clientsClaim: true,
        runtimeCaching: [
          { urlPattern: () => true, handler: 'NetworkOnly' },
        ],
      },
    }),
  ],
})
