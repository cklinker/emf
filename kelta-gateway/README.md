# Kelta API Gateway

Spring Cloud Gateway service that serves as the main ingress point for the Kelta platform. Provides centralized authentication, multi-tenant routing, JSON:API processing, and rate limiting.

## Architecture

```
Client Request
  │
  ├─ TenantSlugExtractionFilter (-300)   Strip tenant slug from URL
  ├─ JwtAuthenticationFilter (-100)       Validate JWT, extract principal
  ├─ RateLimitFilter (-50)                Per-tenant rate limiting (Redis)
  ├─ RouteAuthorizationFilter (0)         Object-level permission checks
  ├─ Request forwarded to worker
  ├─ FieldAuthorizationFilter             Filter response fields by permissions
  └─ IncludeResolutionFilter (10200)      Resolve JSON:API ?include= params
```

## Key Packages

| Package | Description |
|---------|-------------|
| `auth` | JWT validation, `GatewayPrincipal` extraction, public path matching |
| `authz` | Route/field authorization filters, permission resolution from worker |
| `config` | Spring configuration, bootstrap, Kafka/Redis/Security beans |
| `filter` | Tenant resolution, security headers, request logging |
| `route` | `DynamicRouteLocator`, `RouteRegistry` (in-memory, thread-safe) |
| `cache` | `GatewayCacheManager` -- Caffeine-backed tenant slug and governor limit caches |
| `ratelimit` | `RedisRateLimiter` (sliding window) |
| `jsonapi` | `IncludeResolver` -- fetches related resources from Redis cache with backend fallback |
| `listener` | Kafka consumers for collection and worker assignment changes |
| `health` | Health indicators for Redis, Kafka, and worker service |

## Configuration

Key properties from `application.yml`:

```yaml
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${OIDC_ISSUER_URI:http://localhost:9000/realms/kelta}

kelta.gateway:
  worker-service-url: ${WORKER_SERVICE_URL:http://kelta-worker:80}
  tenant-slug:
    enabled: ${TENANT_SLUG_ENABLED:true}
    cache-refresh-ms: 60000
  security:
    permissions-enabled: ${PERMISSIONS_ENABLED:false}
    permissions-cache-ttl-minutes: ${PERMISSIONS_CACHE_TTL:5}
    public-paths: /api/ui-pages,/api/ui-menus,/api/oidc-providers,/api/tenants
  kafka.topics:
    collection-changed: kelta.config.collection.changed
    worker-assignment-changed: kelta.worker.assignment.changed
    record-changed: kelta.record.changed
```

## How It Works

**Startup:**
1. `RouteInitializer` primes the `GatewayCacheManager` tenant slug cache by calling the worker
2. Fetches initial routes and governor limits via `POST {worker}/internal/bootstrap`
3. Populates `RouteRegistry` and publishes `RefreshRoutesEvent`

**Runtime:**
- Routes are **not** hardcoded -- they are discovered from the worker at startup and updated dynamically via Kafka events without restart
- JSON:API resources are cached in Redis with a 10-minute TTL
- Rate limits are enforced per-tenant using Redis sliding windows backed by governor limit configuration

**Kafka topics consumed:**
- `kelta.config.collection.changed` -- updates routes on collection create/update/delete
- `kelta.worker.assignment.changed` -- updates routes on worker assignment changes
- `kelta.record.changed` -- record change events

## Building

```bash
# Build runtime dependencies first
mvn clean install -DskipTests -f kelta-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B

# Build and test gateway
mvn verify -f kelta-gateway/pom.xml -B
```

## Running Locally

Requires Redis, Kafka, Keycloak, and the worker service. Start infrastructure via Docker Compose from the repo root:

```bash
docker-compose up -d
mvn spring-boot:run -f kelta-gateway/pom.xml
```

The gateway starts on port **8080**.

## Testing

- **Unit tests** -- JUnit 5 + Mockito, test classes in isolation
- **Integration tests** -- Testcontainers for Kafka/Redis, MockWebServer for HTTP
- **Property-based tests** -- JUnit QuickCheck for universal property validation

```bash
mvn verify -f kelta-gateway/pom.xml -B
```

## Health Checks

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Overall health |
| `GET /actuator/health/redis` | Redis connectivity |
| `GET /actuator/health/kafka` | Kafka connectivity |
| `GET /actuator/metrics` | Micrometer metrics |

## Docker

Multi-stage build: `maven:3.9-eclipse-temurin-21` (build) -> `eclipse-temurin:21-jre` (runtime). Runs as non-root user `kelta` on port 8080 with G1GC and 75% RAM allocation.

## Dependencies

- Java 21, Spring Boot 3.2.2, Spring Cloud 2023.0.0
- `runtime-events`, `runtime-jsonapi` (Kelta platform modules)
- Spring Cloud Gateway, Spring WebFlux (reactive)
- Spring Security OAuth2 Resource Server
- Spring Data Redis (reactive), Spring Kafka
