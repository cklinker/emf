# Contributing to kelta-worker

See the root [../CONTRIBUTING.md](../CONTRIBUTING.md) for full contribution guidelines,
code of conduct, and PR process.

## Quick Reference

### Make Commands

| Command | Description |
|---------|-------------|
| `make setup` | Install toolchain dependencies |
| `make build` | Compile and package (skips tests) |
| `make test` | Run unit tests |
| `make verify` | Full build + test + lint |
| `make dev` | Run locally with Spring Boot devtools |
| `make lint` | Run Checkstyle |
| `make format` | Check formatting with Spotless |
| `make format-fix` | Apply Spotless formatting |
| `make clean` | Remove build artifacts |

### Commit Format

```
<type>(worker): <description>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

Examples:
- `feat(worker): add webhook retry backoff strategy`
- `fix(worker): resolve race condition in collection lifecycle init`
- `test(worker): add coverage for scheduled workflow executor`

### Branch Naming

- `feature/<desc>` — new functionality
- `fix/<desc>` — bug fixes
- `chore/<desc>` — maintenance, deps, tooling

### Key Rules

- All changes require a PR — never commit directly to `main`
- Run `make verify` before opening a PR
- New Flyway migrations must be sequential (check current max in `src/main/resources/db/migration/`)
- Any registry/cache change must be broadcast via Kafka (see `ConfigEventPublisher` pattern)
