# kelta-ai Architecture

## Overview

`kelta-ai` is a Spring Boot service that acts as a bridge between the Kelta platform and the Anthropic Claude API. It is responsible for prompt construction, context management, response streaming, and conversation persistence.

## Request Flow

```
Client (kelta-ui / kelta-gateway)
    │
    ▼
AiController (REST / SSE)
    │
    ├─► TenantContextResolver         — resolves tenant from JWT / header
    │
    ├─► ConversationContextService    — loads/saves context from Redis + PostgreSQL
    │       ├─ Redis: recent turns (TTL-based, fast)
    │       └─ PostgreSQL: full conversation history (durable)
    │
    ├─► PromptBuilder                 — constructs final Claude prompt
    │       ├─ System prompt (schema, tenant context, instructions)
    │       └─ User turn + conversation history
    │
    ├─► AnthropicClient (SDK)         — sends request to Anthropic API
    │       ├─ Streaming: returns Flux<ServerSentEvent>
    │       └─ Non-streaming: returns Mono<AiResponse>
    │
    └─► ResponseMapper                — maps SDK response to Kelta API response
```

## Key Components

### `AiController`
Spring `@RestController` exposing two modes:
- `POST /api/ai/chat` — non-streaming, returns full response as JSON
- `POST /api/ai/chat/stream` — streaming via SSE (`text/event-stream`)

### `ConversationContextService`
Manages the context window sent to Claude:
- Loads recent turns from Redis (fast path, TTL = 30 min)
- Falls back to PostgreSQL for older history
- Persists new turns after each exchange
- Enforces a configurable token budget before truncating history

### `PromptBuilder`
Assembles the messages array sent to the Claude API:
1. System prompt: tenant schema summary, user permissions, platform instructions
2. Conversation history: prior turns from `ConversationContextService`
3. Current user message

### `AnthropicClient`
Thin wrapper around `anthropic-java` SDK:
- Handles authentication via `ANTHROPIC_API_KEY`
- Applies retry logic for 429 (rate limit) and 529 (overloaded) responses
- Configured model and max tokens via `application.yml`

### `AiExceptionHandler`
`@RestControllerAdvice` that maps SDK exceptions to HTTP responses:
- `RateLimitException` → 429
- `AuthenticationException` → 502 (bad gateway — upstream auth failure)
- `AnthropicException` → 502

## Data Stores

| Store | Purpose |
|-------|---------|
| PostgreSQL | Durable conversation history, AI request audit log |
| Redis | Short-term context cache (recent turns, session state) |

## Kafka Integration

`kelta-ai` consumes events from other services to keep its in-memory caches warm:
- `kelta.config.collection.changed` — refreshes schema context when collections change
- `kelta.tenant.updated` — invalidates tenant-level cached context

It does not publish events (read-only consumer role).

## Security

- All endpoints require a valid JWT issued by `kelta-auth`
- Tenant isolation is enforced at the `TenantContextResolver` level — cross-tenant data is never included in prompts
- `ANTHROPIC_API_KEY` must never be logged or returned in responses
- See [SECURITY.md](SECURITY.md) for additional AI-specific notes

## Observability

- OpenTelemetry traces exported via OTLP (configured in `application.yml`)
- Structured JSON logs via Logstash encoder
- Actuator health + metrics at `/actuator`
