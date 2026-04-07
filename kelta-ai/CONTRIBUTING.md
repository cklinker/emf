# Contributing to kelta-ai

See the root [../CONTRIBUTING.md](../CONTRIBUTING.md) for general contribution guidelines, branch naming conventions, and pull request process.

## AI Service-Specific Notes

### Commit Format

All commits scoped to this service must use the `ai` scope:

```
<type>(ai): <description>
```

Examples:

```
feat(ai): add streaming response support for long-form generation
fix(ai): handle rate limit retries with exponential backoff
refactor(ai): extract context builder into dedicated service
test(ai): add unit tests for prompt template rendering
```

### Code Style

- Run `make format` before committing to apply Google Java Format (AOSP style)
- Run `make lint` to check Checkstyle violations
- All new code must have unit tests; integration tests are preferred where feasible

### Adding New Endpoints

Document new endpoints in [docs/api/README.md](docs/api/README.md).
