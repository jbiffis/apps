import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Deployed at apps.biffis.com/book-log
export default defineConfig({
  base: '/book-log/',
  plugins: [react()],
})
