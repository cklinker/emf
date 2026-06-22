import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // Progressive Web App: installable end-user shell + offline app-shell precache.
    // Read-only first (Rec 2A) — API requests stay network-only (no runtimeCaching for
    // /api): offline *data* (IndexedDB replica + outbox) is the separate Rec 2B slice.
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'logo.svg'],
      manifest: {
        name: 'Kelta',
        short_name: 'Kelta',
        description: 'Kelta application platform',
        theme_color: '#0f172a',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [{ src: 'logo.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' }],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,woff2}'],
        // The app's main bundle is a few MB; raise the precache size limit so it's cached
        // for offline use (default is 2 MiB). Code-splitting to shrink it is a separate task.
        maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
        // SPA fallback to index.html for client routes, but never for backend paths.
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [
          /^\/api/,
          /^\/control/,
          /^\/internal/,
          /^\/actuator/,
          /^\/openapi/,
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
    // Force a single instance of React AND every React-context-bearing
    // singleton across the app and the file:-linked @kelta/components /
    // @kelta/formula workspace packages. Without this, npm installs a
    // second copy inside packages/components/node_modules (each declares
    // these as peer/dev deps) while the app uses its own — a hook from the
    // components bundle then reads a different context provider and throws.
    //
    // react/react-dom: "Cannot read properties of null (reading 'useRef')".
    // react-router-dom: the detail-page components migrated to
    //   @kelta/components in #903–#907 (Crumb, Navigation) call <Link> /
    //   router hooks; a duplicate copy made useContext(NavigationContext)
    //   null → "Cannot destructure property 'basename' of useContext(...)
    //   as it is null", crashing the whole record-detail page.
    // @tanstack/react-query: a duplicate QueryClientProvider context means
    //   useQuery in the shared components never sees the app's client, so
    //   data lists silently never load.
    // react-hook-form: duplicate FormProvider context breaks shared
    //   form-field components the same way.
    dedupe: [
      'react',
      'react-dom',
      'react/jsx-runtime',
      'react-router-dom',
      '@tanstack/react-query',
      'react-hook-form',
    ],
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
