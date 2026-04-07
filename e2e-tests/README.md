# Kelta E2E Tests

Playwright end-to-end test suite for the Kelta Enterprise Platform.

## Prerequisites

- Node.js 18+
- Running Kelta platform (see root `../README.md` for local dev setup)
- Environment variables set (copy `.env.example` to `.env` and fill in values)

## Setup

```bash
npm run setup
```

This installs dependencies and downloads Playwright browsers.

## Running Tests

```bash
# Run all tests (headless)
npm test

# Run with browser UI visible
npm run test:headed

# Open Playwright UI mode
npm run test:ui
npm run dev

# Run a specific test suite
npm run test:auth
npm run test:admin
npm run test:end-user
npm run test:journeys

# Run only in Chromium
npm run test:chromium

# Debug a specific test
npm run test:debug

# Show the last HTML report
npm run report
```

## Writing Tests

- Tests live in `tests/` organized by feature area
- Page objects live in `pages/`
- Shared fixtures live in `fixtures/`
- Helper utilities live in `helpers/`

See `docs/api/README.md` for page object patterns and conventions.

## Generating Tests

```bash
npm run codegen
```

Launches Playwright's code generator pointed at the base URL.
