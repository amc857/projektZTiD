import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // All /api requests forwarded to Spring Boot — no CORS needed in dev
      '/api': {
        target: 'http://backend:8080',
        changeOrigin: true,
      },
    },
  },
})
