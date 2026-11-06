import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // One .env at the repository root for the server, compose and this app (only VITE_* reach the page).
  envDir: '../..',
  resolve: {
    // plano-shared is a symlink to bridge-web/build, outside this folder: its own imports must
    // resolve from apps/web/node_modules (npm does not install dependencies of `file:` links).
    dedupe: ['@js-joda/core'],
    // Ktor's JS engine imports `ws` on its Node.js path only: an empty stub keeps it out of the bundle.
    alias: { ws: fileURLToPath(new URL('./src/stubs/ws.ts', import.meta.url)) },
  },
  server: {
    // Same origin for the browser: the session cookie needs no CORS and never crosses origins.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
