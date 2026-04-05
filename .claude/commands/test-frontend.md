Run frontend lint, typecheck, and tests for kelta-web, kelta-ui, or both.

Usage: `/test-frontend` (both), `/test-frontend web`, `/test-frontend ui`

Argument: $ARGUMENTS (optional: "web", "ui", or empty for both)

## kelta-web (if argument is "web" or empty)
```bash
cd kelta-web && npm install
npm run lint
npm run typecheck
npm run format:check
npm run test:coverage
```

## kelta-ui (if argument is "ui" or empty)
```bash
cd kelta-ui/app && npm install
npm run lint
npm run format:check
npm run test:run
```

Report pass/fail for each module tested. If any check fails, show the error output.
