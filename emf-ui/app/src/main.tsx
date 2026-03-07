import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { initTelemetry } from './telemetry'
import App from './App.tsx'

// Initialize OpenTelemetry before rendering
initTelemetry()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
