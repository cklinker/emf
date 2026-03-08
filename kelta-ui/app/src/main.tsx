import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
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
