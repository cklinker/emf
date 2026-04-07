# Contributing to kelta-web

Contribution guidelines are maintained at the repository root. See [../CONTRIBUTING.md](../CONTRIBUTING.md) for the full guide.

## Quick Reference — kelta-web

### Setup

```bash
npm install
```

### Common Commands

| Command | Description |
|---------|-------------|
| `npm run build` | Build all packages |
| `npm run test` | Run unit tests (Vitest) |
| `npm run test:coverage` | Run tests with coverage report |
| `npm run lint` | Lint all packages |
| `npm run lint:fix` | Auto-fix lint errors |
| `npm run format` | Format all files with Prettier |
| `npm run format:check` | Check formatting without writing |
| `npm run typecheck` | Type-check all packages |

### Package Structure

- `packages/sdk` — Core HTTP client and data models
- `packages/components` — Shared React UI components
- `packages/plugin-sdk` — Plugin extension API
- `packages/cli` — CLI tooling

### Before Submitting a PR

1. Run `npm run format:check && npm run lint && npm run typecheck && npm run test`
2. Ensure all new code has unit tests
3. Follow the branch naming convention: `feature/<desc>`, `fix/<desc>`, `chore/<desc>`
