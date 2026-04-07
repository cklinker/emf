# Contributing to Kelta Auth

See the [root CONTRIBUTING.md](../CONTRIBUTING.md) for the full development workflow, coding standards, and PR process.

## Quick Reference

### Build & Test

```bash
make build    # Compile and package
make test     # Run unit tests
make verify   # Run all tests including integration
make dev      # Start locally
```

### Branch Naming

- `feature/<description>` — new functionality
- `fix/<description>` — bug fixes
- `chore/<description>` — maintenance

### Commit Format

```
<type>(auth): <description>
```

### Code Standards

- Java 25, Spring Boot 4.x
- All entities extend `BaseEntity`
- Checkstyle enforced (see `checkstyle.xml`)
- Spotless formatter (Google Java Format, AOSP style)
- All new features require unit tests
