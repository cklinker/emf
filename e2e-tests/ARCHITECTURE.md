# E2E Test Architecture

## Directory Structure

```
e2e-tests/
├── tests/                    # Test files, organized by feature area
│   ├── auth/                 # Authentication flows
│   ├── admin/                # Admin UI flows
│   ├── end-user/             # End-user portal flows
│   └── journeys/             # Multi-feature user journeys
├── pages/                    # Page object model classes
├── fixtures/                 # Custom Playwright fixtures
│   └── index.ts              # Exports extended `test` and `expect`
├── helpers/                  # Shared utilities
│   ├── api.ts                # API client helpers (seeding, cleanup)
│   └── ...
├── docs/
│   └── api/README.md         # Test patterns and page object docs
├── playwright.config.ts      # Playwright configuration
├── vitest.config.ts          # Vitest config (for helper/utility unit tests)
├── tsconfig.json             # TypeScript configuration
├── eslint.config.js          # ESLint flat config
├── .prettierrc               # Prettier config
└── .github/
    └── workflows/
        └── ci.yml            # CI pipeline
```

## Layers

### Tests (`tests/`)

Playwright spec files. Each file targets a specific feature or flow. Tests import the custom fixture-extended `test` from `@fixtures`.

### Page Objects (`pages/`)

Encapsulate UI interactions for a given page or component. Receive `Page` from Playwright via constructor. No inheritance hierarchy — composition is preferred.

### Fixtures (`fixtures/`)

Extend Playwright's `test` with authenticated sessions, pre-seeded data, and other shared setup. The `index.ts` re-exports `test` and `expect` for use throughout the suite.

### Helpers (`helpers/`)

Pure utility functions: REST API clients for seeding/cleanup, data factories, polling utilities, etc. These are plain TypeScript, not Playwright-specific, and can be unit-tested with Vitest.

## Configuration

- `playwright.config.ts` defines projects (chromium, firefox, webkit, ci).
- Base URL and credentials come from environment variables (`.env`).
- `vitest.config.ts` is provided for unit-testing helper utilities, with v8 coverage.

## CI

The CI workflow (`.github/workflows/ci.yml`) runs:
1. Lint (ESLint + Prettier check)
2. Type check (`tsc --noEmit`)
3. Playwright tests (all projects)
4. Gitleaks secrets scan
