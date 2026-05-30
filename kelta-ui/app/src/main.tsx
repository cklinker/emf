import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './styles/kelta-design.css'
// Map controls + popup styling for the optional interactive map in
// @kelta/components AddressMap. Lazy-loaded chunk imports maplibre-gl at
// runtime; the CSS must be present from the first render so popovers
// styled by the lib aren't unstyled. Tree-shakes to ~7 KB.
import 'maplibre-gl/dist/maplibre-gl.css'
import { initTelemetry } from './telemetry'
import { migrateLocalStorage } from './migrateLocalStorage'
import App from './App.tsx'

// Migrate localStorage keys from emf_* to kelta_* (one-time)
migrateLocalStorage()

// Initialize OpenTelemetry before rendering
initTelemetry()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
