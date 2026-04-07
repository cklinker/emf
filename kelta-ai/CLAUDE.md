# kelta-ai — Claude Code Instructions

## Service Overview

AI assistant service for the Kelta Enterprise Platform. Wraps the Anthropic Claude API to deliver schema-aware, tenant-scoped AI interactions. Responses may be streamed via Server-Sent Events (SSE).

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.x (Web + WebFlux) |
| AI SDK | `anthropic-java` 2.18.0 |
| Database | PostgreSQL + Flyway |
| Cache | Redis (Spring Data Redis) |
| Build | Maven 3.9.x |

## Key Patterns

- **Streaming**: Use `WebFlux` + SSE for streaming Claude responses. Never block on `Flux` inside a `@RestController`.
- **Tenant isolation**: Always resolve `TenantContext` from the incoming request before building prompts. Never share context across tenant boundaries.
- **Context management**: Conversation history and context windows are stored in Redis (short-term) and PostgreSQL (long-term). Use the `ConversationContextService` abstraction.
- **Prompt templates**: Prompts are constructed via `PromptBuilder` — do not inline raw strings in controllers or service methods.
- **Error handling**: Anthropic rate limit errors (`429`) must be retried with exponential backoff. Map other SDK errors to appropriate HTTP status codes via `AiExceptionHandler`.
- **Entities**: All new JPA entities extend `BaseEntity` (UUID id, createdAt, updatedAt).
- **No star imports**: Enforced by Checkstyle.

## Build & Test Commands

```bash
# Build (skip tests)
mvn clean package -DskipTests

# Run all tests
mvn test

# Full verify (build + checkstyle + tests)
mvn verify

# Apply Google Java Format
mvn spotless:apply

# Check formatting without applying
mvn spotless:check

# Checkstyle report
mvn checkstyle:check

# Run dev server (requires .env populated)
mvn spring-boot:run
```

Or use the `Makefile` shortcuts:

```bash
make build
make test
make verify
make format
make lint
make dev
```

## Environment

Copy `.env.example` to `.env` and populate before running locally. The `ANTHROPIC_API_KEY` is required.

## Reference Docs

- [ARCHITECTURE.md](ARCHITECTURE.md) — service internals, request flow, context management
- [docs/api/README.md](docs/api/README.md) — REST/SSE endpoint reference
- [../../.claude/docs/integrations.md](../../.claude/docs/integrations.md) — platform-level integrations (Kafka, Redis, Cerbos)
