import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Backend runs on http://localhost:8280 (see DESIGN.md / hero-backend
// application.yml). Requests are proxied through /api so the frontend stays
// same-origin at http://localhost:5171 - this avoids needing any backend
// CORS configuration change for a frontend-only slice.
const backendTarget = 'http://localhost:8280'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5171,
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
  preview: {
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
})
