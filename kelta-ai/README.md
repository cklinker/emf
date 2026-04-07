# kelta-ai

AI assistant service for the Kelta Enterprise Platform. Integrates with Anthropic Claude to provide intelligent, context-aware assistance across tenant workspaces — including schema-aware querying, workflow suggestions, and natural language interactions with platform data.

## Stack

- Java 25, Spring Boot 4.x, Maven
- Anthropic Java SDK (`anthropic-java`)
- PostgreSQL + Flyway (conversation history, context storage)
- Redis (session context caching)
- Spring WebFlux (streaming responses)

## Getting Started

```bash
cp .env.example .env
# Fill in ANTHROPIC_API_KEY and datasource credentials
make dev
```

## Build & Test

```bash
make build    # compile
make test     # unit tests
make verify   # build + lint + test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) and [ARCHITECTURE.md](ARCHITECTURE.md) for more detail.
