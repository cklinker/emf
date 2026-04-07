# kelta-gateway

Spring Cloud Gateway — API entry point for all Kelta platform requests. Handles JWT authentication, tenant resolution, Cerbos authorization, rate limiting, and dynamic routing to backend services.

## Package Layout

```
io.kelta.gateway/
  auth/            ← JWT validation, PAT auth, principal extraction, public path matching
  authz/           ← Route authorization filter + Cerbos integration
    cerbos/        ← CerbosAuthorizationService, CerbosConfig, CerbosPrincipalBuilder
  cache/           ← GatewayCacheManager
  config/          ← Spring Security, Redis, Kafka, route init, WebSocket, service accounts
  error/           ← Exception classes + GlobalErrorHandler
  filter/          ← Gateway filters (tenant, security headers, rate limit, logging, caching)
  health/          ← Health indicators (Kafka, Redis, Worker)
  listener/        ← Kafka consumers (config events, realtime bridge)
  metrics/         ← GatewayMetrics (Micrometer)
  ratelimit/       ← Redis-backed rate limiter
  route/           ← DynamicRouteLocator, RouteRegistry, RouteDefinition
  service/         ← RouteConfigService
  websocket/       ← Real-time subscriptions (WebSocket handler, SubscriptionManager)
```

## Key Patterns

### Reactive Filters
All filters use reactive `Mono`/`Flux` — never block in the filter chain:
```java
public class MyFilter implements GlobalFilter, Ordered {
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Extract from exchange, transform, chain.filter(exchange)
    }
}
```
**Reference**: `TenantResolutionFilter.java`, `SecurityHeadersFilter.java`

### Per-Request State
Use `ServerWebExchange` attributes — never instance fields:
```java
exchange.getAttributes().put("tenantId", tenantId);
```

### Dynamic Routing
Routes are loaded from worker at startup and updated via Kafka events:
- `RouteRegistry` — in-memory route store
- `DynamicRouteLocator` — bridges RouteRegistry with Spring Cloud Gateway
- `ConfigEventListener` — consumes `kelta.config.collection.changed` and updates routes

### Error Handling
| Exception | When |
|-----------|------|
| `GatewayAuthenticationException` | JWT validation fails |
| `GatewayAuthorizationException` | Cerbos denies access |
| `RateLimitExceededException` | Rate limit hit |
| `RouteNotFoundException` | No matching route |

All handled by `GlobalErrorHandler`.

### Authorization Flow
1. `JwtAuthenticationFilter` validates token
2. `TenantResolutionFilter` extracts tenant from JWT claims
3. `RouteAuthorizationFilter` calls `CerbosAuthorizationService` (gRPC)
4. If authorized, request forwarded to worker

## When Creating a New Filter

1. Create class in `filter/` implementing `GlobalFilter` (or `GatewayFilter` for per-route)
2. Implement `Ordered` to control filter chain position
3. Use `ServerWebExchange` for request/response access
4. Add test in `src/test/java/io/kelta/gateway/filter/`

**Reference**: `RequestLoggingFilter.java`, `IpRateLimitFilter.java`

## Reference Implementations

| Pattern | File |
|---------|------|
| Global filter | `filter/TenantResolutionFilter.java` |
| Route filter | `filter/SystemCollectionResponseCacheFilter.java` |
| Auth filter | `auth/JwtAuthenticationFilter.java` |
| Kafka listener | `listener/ConfigEventListener.java` |
| Error handler | `error/GlobalErrorHandler.java` |
| Health indicator | `health/WorkerHealthIndicator.java` |
| Unit test | Any test in `src/test/java/io/kelta/gateway/` |

## Running Tests

```bash
mvn test                              # All tests
mvn test -Dtest=GatewayMetricsTest    # Single class
mvn test -Dtest=GatewayMetricsTest#shouldRecordRequestDuration  # Single method
mvn test -Dtest="*Filter*"            # Pattern match
```

## Build Commands

```bash
make build     # mvn clean package -DskipTests
make test      # mvn test
make verify    # mvn verify
make dev       # mvn spring-boot:run
make lint      # mvn checkstyle:check
make format    # mvn spotless:check
```

## Do Not

- Block in reactive filter chains — use `.subscribeOn(Schedulers.boundedElastic())` only when unavoidable
- Store per-request state in instance fields — use `ServerWebExchange` attributes
- Bypass JWT validation for any route except `/actuator/health`

## Test Fixtures

Use `TestFixtures.java` in `src/test/java/io/kelta/gateway/` for pre-built `RouteDefinition`, `GatewayPrincipal`, and `RateLimitConfig` instances.
