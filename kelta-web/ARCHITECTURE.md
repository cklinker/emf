# kelta-web Architecture

## Overview

`kelta-web` is an npm workspaces monorepo containing the TypeScript SDK and React component library for the Kelta Enterprise Platform. It is consumed by `kelta-ui` (the admin/builder UI) and by third-party plugin developers.

## Repository Structure

```
kelta-web/
├── packages/
│   ├── sdk/              # @kelta/sdk — Core SDK
│   ├── components/       # @kelta/components — React UI components
│   ├── plugin-sdk/       # @kelta/plugin-sdk — Plugin extension API
│   └── cli/              # @kelta/cli — CLI tooling
├── docs/
│   └── api/
│       └── openapi.yaml  # OpenAPI spec for SDK-consumed endpoints
├── playwright.config.ts  # E2E reference stub (tests live in ../e2e-tests/)
├── vitest.config.ts      # Shared Vitest configuration
├── tsconfig.json         # Root TypeScript config (references per-package configs)
└── package.json          # Workspace root (scripts, shared devDependencies)
```

## Package Dependency Graph

```
kelta-ui (app)
    └── @kelta/components
            └── @kelta/sdk
    └── @kelta/sdk

plugin authors
    └── @kelta/plugin-sdk
            └── @kelta/sdk (peer)
```

## @kelta/sdk

The SDK is a thin HTTP client over the Kelta backend gateway. Key responsibilities:

- **Client**: Axios-based client with JWT auth injection and refresh logic
- **Query builders**: Fluent API for constructing JSON:API filter/sort/page parameters
- **Data models**: TypeScript types generated from the OpenAPI spec (see `docs/api/openapi.yaml`)
- **Hooks**: TanStack Query hooks wrapping SDK client methods

All API communication is JSON:API (`application/vnd.api+json`). The base URL is configured via `VITE_API_URL`.

## @kelta/components

Shared React components for building data-driven UIs:

- Form components wired to `react-hook-form` + `zod` validation
- Data table and list components integrated with SDK query hooks
- Modal and drawer shells

Components are library-built (not bundled into an app). Tree-shaking is expected by consumers.

## @kelta/plugin-sdk

The public surface for plugin developers. Exposes:

- Plugin registration API
- Extension point interfaces (views, actions, field renderers)
- Re-exports of safe SDK utilities

Breaking changes to this package require a major version bump and migration guide.

## @kelta/cli

CLI for scaffolding new collections, field types, and plugin boilerplate. Built as a Node.js ESM binary.

## Build Pipeline

Each package has its own `vite.config.ts` in library mode. The root `npm run build` delegates to each workspace via `npm run build --workspaces`. Declaration files are emitted by `vite-plugin-dts`.

## Testing Strategy

- **Unit tests**: Vitest with `@testing-library/react` for components, MSW for API mocking
- **Property tests**: `fast-check` for SDK query builder invariants
- **E2E tests**: Playwright, managed in `../e2e-tests/`, run against a live stack

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `http://localhost:8080` | Backend gateway base URL |
