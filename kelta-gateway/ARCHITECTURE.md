# kelta-gateway Architecture

## Overview

`kelta-gateway` is the single ingress point for all external traffic to the Kelta platform. It is a reactive API gateway built on Spring Cloud Gateway (WebFlux), responsible for authentication, authorization, rate limiting, and request forwarding.

## Request Lifecycle

```
Client Request
    │
    ▼
[GlobalFilter: TenantResolutionFilter]
    │  Extracts JWT → resolves tenant_id claim
    │  Attaches tenant context to ServerWebExchange attributes
    ▼
[GlobalFilter: JwtValidationFilter]
    │  Validates JWT signature via JWKS (cached, refreshed on key rollover)
    │  Rejects expired or unsigned tokens with 401
    ▼
[GlobalFilter: RateLimitFilter]
    │  Checks per-tenant + per-principal rate limits in Redis
    │  Returns 429 if limit exceeded
    ▼
[GlobalFilter: CerbosAuthorizationFilter]
    │  Calls Cerbos via gRPC with principal + resource + action
    │  Returns 403 if policy denies request
    ▼
[Route Matching]
    │  Matches request path/method to configured routes
    ▼
[Upstream Forwarding]
    │  Forwards to kelta-worker, kelta-auth, or kelta-ai
    │  Injects X-Tenant-ID and X-Principal-ID headers
    ▼
Upstream Service Response → Client
```

## Routing

Routes are defined in `application.yml` under `spring.cloud.gateway.routes`. Each route specifies:
- `uri`: upstream service (e.g., `lb://kelta-worker`)
- `predicates`: path and method matching rules
- `filters`: per-route transformations (header injection, path rewrite, etc.)

Dynamic route updates arrive via Kafka (`kelta.config.routes.changed`) and are applied without restart.

## Authentication

- All requests must carry a Bearer JWT in the `Authorization` header.
- JWTs are issued by `kelta-auth` (internal OIDC provider).
- The gateway validates JWT signature, expiry, and audience.
- Public paths (e.g., `/actuator/health`) are excluded from JWT validation.

## Authorization

- After JWT validation, the `CerbosAuthorizationFilter` calls Cerbos via gRPC.
- The principal (from JWT), resource (derived from route), and action (HTTP method) are sent to Cerbos.
- Cerbos evaluates policies and returns allow/deny.
- Denied requests receive `403 Forbidden` before reaching any upstream service.

## Rate Limiting

- Implemented via `ReactiveRedisRateLimiter` (token bucket algorithm).
- Rate limit keys are `{tenantId}:{principalId}` to prevent cross-tenant interference.
- Limits are configurable per tenant via platform configuration.
- Redis stores rate limit state; all gateway pods share the same Redis instance.

## Config Change Propagation

- Route and rate limit configuration changes are broadcast via Kafka.
- All gateway pods consume `kelta.config.*` topics and refresh their in-memory state.
- This ensures consistency across pods without requiring restart or external coordination.

## Observability

- Traces exported via OpenTelemetry (OTLP) to Tempo.
- Metrics exported via OTLP to Mimir.
- Structured JSON logs shipped to Loki via logstash-logback-encoder.
- Health endpoint: `GET /actuator/health`
- Metrics endpoint: `GET /actuator/prometheus`
