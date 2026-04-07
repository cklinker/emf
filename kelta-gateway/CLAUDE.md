# kelta-gateway — Claude Instructions

## Stack

- **Java 25**, Spring Boot 4.0.5, Maven
- **Spring Cloud Gateway 2025.1.1** (WebFlux-based reactive gateway)
- **Spring Security** with OAuth2 Resource Server (JWT validation)
- **Redis** (reactive) for rate limiting and token caching
- **Kafka** for receiving config change events from other services
- **Cerbos** (gRPC) for authorization policy enforcement
- **OpenTelemetry** with OTLP export for traces and metrics

## Key Patterns

- All filters are `GatewayFilter` or `GlobalFilter` implementations using reactive `Mono`/`Flux`.
- JWT claims are extracted once per request and propagated via `ServerWebExchange` attributes.
- Tenant context is resolved from the JWT `tenant_id` claim before routing decisions are made.
- Rate limiting uses `ReactiveRedisRateLimiter`; keys are scoped per tenant + principal.
- Authorization calls to Cerbos are made after JWT validation, before upstream forwarding.
- Config updates (e.g., route changes) arrive via Kafka (`kelta.config.*` topics) and update in-memory state on all pods.

## Build and Test Commands

```bash
make build        # compile only
make test         # unit tests
make verify       # full build + lint + coverage
make lint         # Checkstyle only
make format-fix   # apply Spotless formatting
make dev          # run locally with local profile
```

Equivalent Maven commands:
```bash
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## File Layout

```
src/main/java/io/kelta/gateway/
  config/          # Spring Security, Redis, routing config classes
  filter/          # Global and route-specific gateway filters
  security/        # JWT extractors, tenant resolution, Cerbos client
  ratelimit/       # Rate limiter implementations
  kafka/           # Kafka consumers for config events
src/test/java/     # Unit and slice tests mirroring main structure
checkstyle.xml     # Checkstyle rules (warnings only, not blocking)
```

## Do Not

- Do not call blocking APIs from reactive filter chains — use `.subscribeOn(Schedulers.boundedElastic())` only when unavoidable.
- Do not store per-request state in instance fields — use `ServerWebExchange` attributes.
- Do not bypass JWT validation for any route except `/actuator/health`.
