# Contributing to Kelta Platform

Thank you for contributing to Kelta! This guide covers our development workflow, coding standards, and PR process.

## Getting Started

### Prerequisites

- Java 25 (Temurin) — managed via `.tool-versions`
- Node.js 18+
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL (see `docker-compose.yml`)

### Setup

```bash
# Clone and install dependencies
git clone <repo-url> && cd emf
npm run setup
```

## Development Workflow

### Branch Naming

All work happens on feature branches — never commit directly to `main`.

- `feature/<description>` — new functionality
- `fix/<description>` — bug fixes
- `chore/<description>` — maintenance, refactoring, tooling

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

Scopes: `worker`, `gateway`, `auth`, `ai`, `web`, `ui`, `e2e`, `runtime`

### Pull Request Process

1. Create a feature branch from `main`
2. Make your changes with tests
3. Run verification: `npm run verify` (or run Java/frontend checks individually)
4. Push your branch and open a PR targeting `main`
5. Ensure CI passes (Java tests, frontend lint/typecheck/tests)
6. Request review from code owners
7. Squash merge after approval

## Coding Standards

### Java

- Java 25, Spring Boot 4.x
- All entities extend `BaseEntity` (UUID id, createdAt, updatedAt)
- All JPA repositories extend `JpaRepository`
- No raw types, no unchecked casts, no unused imports
- Flyway migrations: sequential numbering in `kelta-worker/src/main/resources/db/migration/`

### TypeScript

- Strict mode enabled (`"strict": true` in tsconfig)
- ESLint + Prettier enforced
- Named exports preferred
- Avoid `any` — use proper types

### Testing

- **Java**: JUnit 5 + Mockito, run with `mvn verify`
- **Frontend**: Vitest + Testing Library, run with `npm test`
- **E2E**: Playwright, run from `e2e-tests/`
- All new features require unit tests
- Coverage reports uploaded to CI artifacts

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for high-level design and service descriptions.

## Code of Conduct

Be respectful, constructive, and collaborative. We're all here to build great software.
