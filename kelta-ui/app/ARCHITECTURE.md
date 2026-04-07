# kelta-ui/app — Architecture

## Overview

Single-page application built with React 19 and Vite. It serves as the admin and builder UI for the Kelta Enterprise Platform. All API traffic is routed through `kelta-gateway`.

## Directory Structure

```
src/
  components/     # Shared, reusable UI components (shadcn/ui wrappers and custom)
  context/        # React context providers (auth, tenant, theme)
  hooks/          # Custom React hooks
  i18n/           # Internationalization (i18next)
  lib/            # Utility helpers (cn, date formatting, etc.)
  pages/          # Route-level page components
  services/       # Axios-based API service modules (one per domain)
  shells/         # Layout shells (authenticated shell, public shell)
  styles/         # Global CSS and Tailwind base styles
  telemetry/      # OpenTelemetry browser instrumentation
  test/           # Shared test utilities, MSW handlers, factories
  types/          # Global TypeScript type definitions
  utils/          # Pure utility functions
  main.tsx        # Application entry point
  App.tsx         # Root component, router setup
```

## Routing

React Router 7 with file-based route grouping under `src/pages/`. The authenticated shell (`src/shells/`) wraps protected routes and enforces auth state from context.

## State Management

- **Server state**: TanStack Query (`@tanstack/react-query`) for all API data fetching, caching, and mutations.
- **UI / auth state**: React context providers in `src/context/`.
- **Form state**: `react-hook-form` with `zod` schema validation.

## API Communication

All HTTP calls are made through typed service modules in `src/services/` using `axios`. The base URL is configured via `VITE_API_URL` (pointing at `kelta-gateway`). Auth tokens are injected by an axios request interceptor.

## Authentication

The UI relies on `kelta-auth` (OIDC provider). Token refresh and session management are handled in `src/context/AuthContext`. The gateway URL is configured via `VITE_AUTH_URL`.

## Styling

Tailwind CSS 4 with CSS variables for theming. `next-themes` manages light/dark mode. Component primitives come from `@kelta/components` (shadcn/ui-based). Class merging uses `clsx` + `tailwind-merge` via the `cn()` utility.

## Observability

OpenTelemetry browser SDK in `src/telemetry/` traces fetch and XHR requests. Traces are exported to the gateway OTLP endpoint.

## Build

Vite 7 with `@vitejs/plugin-react` and `@tailwindcss/vite`. The production build outputs to `dist/` and is served by nginx (see `Dockerfile`).
