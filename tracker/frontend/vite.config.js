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
      // Caching disabled for now so changes are visible immediately. The
      // generated SW unregisters itself and clears caches on next visit.
      selfDestroying: true,
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
        globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest}'],
        navigateFallback: '/tracker/index.html',
        // Never serve the SPA shell for API calls.
        navigateFallbackDenylist: [/^\/tracker\/api\//],
      },
    }),
  ],
})
