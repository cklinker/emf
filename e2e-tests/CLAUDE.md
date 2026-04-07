# E2E Tests — Claude Instructions

## Stack

- **Test framework**: Playwright (`@playwright/test`)
- **Language**: TypeScript (strict mode)
- **Node version**: 18 (see `.nvmrc`)
- **Linter**: ESLint with `typescript-eslint` flat config
- **Formatter**: Prettier

## Test Commands

```bash
npm test                  # Run all tests headless
npm run test:headed       # Run with browser visible
npm run dev               # Playwright UI mode
npm run test:auth         # Auth tests only
npm run test:admin        # Admin tests only
npm run test:end-user     # End-user tests only
npm run test:journeys     # Journey tests only
npm run build             # Type-check only (tsc --noEmit)
npm run setup             # Install deps + Playwright browsers
```

## Patterns

- **Page objects** in `pages/` — one class per page/feature area
- **Fixtures** in `fixtures/` — import from `@fixtures`
- **Helpers** in `helpers/` — import from `@helpers/<name>`
- Use `getByRole`, `getByLabel`, `getByText` — avoid CSS selectors
- Each test must be self-contained; no shared mutable state between tests

## Path Aliases

- `@fixtures` → `./fixtures/index.ts`
- `@pages/*` → `./pages/*`
- `@helpers/*` → `./helpers/*`

## Important Notes

- Tests run against a live Kelta instance. Ensure the platform is running before executing tests.
- Auth state is typically stored via Playwright's `storageState` and reused across tests.
- CI uses the `ci` Playwright project config (see `playwright.config.ts`).
