import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// The Spring Boot backend runs on :8080. Proxying /api keeps the browser
// on one origin in development, so no CORS setup is needed.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
    },
    // Only on preview: the dev server needs inline styles and a websocket for hot reload.
    // A production web server should send the same headers.
    headers: {
      'Content-Security-Policy':
        "default-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
    },
  },
  build: {
    target: 'es2020',
    // Small fonts would otherwise become data: URIs, which the CSP above blocks.
    assetsInlineLimit: 0,
  },
  test: {
    environment: 'jsdom',
    globals: false,
    include: ['src/**/*.spec.ts'],
  },
})
