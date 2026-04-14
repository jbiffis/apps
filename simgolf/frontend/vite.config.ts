import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  base: '/simgolf/',
  plugins: [
    react(),
    VitePWA({
      selfDestroying: true,
      manifest: {
        name: 'Sim Golf League',
        short_name: 'SimGolf',
        description: 'Simulation Golf League Score Tracker',
        theme_color: '#2d5a27',
        background_color: '#faf8f2',
        display: 'standalone',
        orientation: 'portrait',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'pwa-maskable-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/simgolf/api': {
        target: 'http://localhost:8082',
        rewrite: (path) => path.replace(/^\/simgolf/, ''),
        changeOrigin: true,
      },
    },
  },
})
