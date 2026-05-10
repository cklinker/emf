import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
    // Force a single React instance across the app and the file:-linked
    // @kelta/components / @kelta/formula workspace packages. Without this,
    // npm hoists a second react@18 inside packages/components/node_modules
    // (because that package declares react ^18 as a peer/dev dep) while the
    // app uses react@19 — calling a hook from the components bundle then
    // hits a different ReactCurrentDispatcher and throws
    // "Cannot read properties of null (reading 'useRef')".
    dedupe: ['react', 'react-dom', 'react/jsx-runtime'],
  },
  server: {
    port: 5174,
    proxy: {
      // All control plane endpoints are under /control
      '/control': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/internal': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/openapi': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
