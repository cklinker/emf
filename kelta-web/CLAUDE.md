# kelta-web — Claude Code Instructions

## Stack

- **Language**: TypeScript (strict mode)
- **UI Framework**: React 19
- **Build**: Vite, with `vite-plugin-dts` for library builds
- **Testing**: Vitest + @testing-library/react, fast-check for property tests, MSW for API mocking
- **Linting**: ESLint + Prettier
- **Package manager**: npm workspaces (monorepo)

## Package Descriptions

| Package | Path | Purpose |
|---------|------|---------|
| `@kelta/sdk` | `packages/sdk` | Core HTTP client, data models, query builders for the Kelta API |
| `@kelta/components` | `packages/components` | Shared React UI components (forms, tables, modals) |
| `@kelta/plugin-sdk` | `packages/plugin-sdk` | Public extension API for building Kelta plugins |
| `@kelta/cli` | `packages/cli` | CLI tooling for scaffolding and code generation |

## Build Commands

```bash
npm install            # Install all workspace dependencies
npm run build          # Build all packages
npm run typecheck      # Type-check all packages (no emit)
npm run lint           # ESLint across all packages
npm run lint:fix       # Auto-fix lint errors
npm run format         # Prettier format
npm run format:check   # Check formatting (used in CI)
```

## Test Commands

```bash
npm run test           # Run all unit tests once
npm run test:watch     # Run tests in watch mode
npm run test:coverage  # Run tests with v8 coverage report
```

## Key Conventions

- All exports are named exports — no default exports
- Avoid `any`; use `unknown` with type guards when needed
- All packages are ESM (`"type": "module"`)
- API calls go through the SDK client — never `fetch` directly in components
- Data fetching uses TanStack Query (`@tanstack/react-query`)
- Forms use `react-hook-form` + `zod` for validation
- Follow existing patterns in each package before introducing new abstractions

## E2E Tests

E2E tests live in `../e2e-tests/` (Playwright). See `playwright.config.ts` for the reference stub.
