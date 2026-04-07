# Contributing to kelta-gateway

See [../CONTRIBUTING.md](../CONTRIBUTING.md) for the full contribution guide covering branching, PR process, and code review standards.

## Quick Reference

### Make Commands

| Command | Description |
|---------|-------------|
| `make build` | Compile without running tests |
| `make test` | Run unit tests |
| `make verify` | Full build + tests + Checkstyle + coverage |
| `make lint` | Run Checkstyle only |
| `make format` | Check Spotless formatting |
| `make format-fix` | Apply Spotless formatting |
| `make dev` | Start gateway with local profile |
| `make clean` | Remove build artifacts |

### Commit Format

```
<type>(gateway): <description>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

Examples:
- `feat(gateway): add per-tenant rate limiting filter`
- `fix(gateway): correct JWT audience validation`
- `chore(gateway): upgrade Spring Cloud to 2025.1.2`

### Branch Naming

- `feature/<desc>` — new features
- `fix/<desc>` — bug fixes
- `chore/<desc>` — maintenance tasks

All changes go through a PR. Never push directly to `main`.
