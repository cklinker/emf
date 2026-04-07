# kelta-worker — Claude AI Context

Primary worker service for the Kelta platform. Owns database migrations (Flyway), workflow execution, collection lifecycle management, and business logic.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.x |
| Build | Maven 3.9+ |
| Database | PostgreSQL + Flyway |
| Messaging | Kafka (spring-kafka) |
| Cache | Redis (Spring Data Redis) |
| Storage | AWS S3 (optional, presigned URLs) |
| Auth | Cerbos (authorization), JWT via gateway |
| Webhooks | Svix |
| Observability | OpenTelemetry, Prometheus, Logstash JSON logs |

## Key Patterns

### BaseEntity

All new JPA entities extend `BaseEntity` (provides UUID id, createdAt, updatedAt). All new repositories extend `JpaRepository`.

### Kafka Events (ConfigEventPublisher)

Any change to an in-memory registry or cache must be broadcast via Kafka so all pods receive the update. Never call `lifecycleManager.refreshX()` directly from a hook — that only updates the local pod.

Pattern:
1. Create a `BeforeSaveHook` for the system collection
2. In after-create/update/delete, publish via `ConfigEventPublisher` (e.g., to `kelta.config.collection.changed`)
3. All pods consume the event and refresh their local registry

### Flyway Migrations

Migrations live in `src/main/resources/db/migration/`. Always use the next sequential version number. Current range: V1–V67.

### Collection Lifecycle

`CollectionLifecycleManager` handles init, refresh, and teardown of collection routing. `DynamicCollectionRouter` serves JSON:API CRUD for all registered collections.

### Workflow Engine

`WorkflowEngine` evaluates rules and executes actions. `ScheduledWorkflowExecutor` polls every 60s with optimistic locking for due scheduled rules.

## Build and Test Commands

```bash
# Build runtime dependencies (run from repo root)
mvn clean install -DskipTests -f kelta-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B

# Build worker
mvn clean package -DskipTests -f kelta-worker/pom.xml -B

# Run tests
mvn verify -f kelta-worker/pom.xml -B

# Run locally
mvn spring-boot:run -f kelta-worker/pom.xml

# Or use make
make verify
make dev
```

## Internal API Endpoints (gateway-facing)

| Endpoint | Description |
|----------|-------------|
| `GET /internal/bootstrap` | All active collections + tenant governor limits |
| `GET /internal/tenants/slug-map` | Slug-to-tenant-ID mapping |
| `GET /internal/oidc/by-issuer` | OIDC provider lookup by issuer URI |
| `GET /internal/permissions` | Effective permissions for a user |

## Important Files

- `src/main/resources/application.yml` — primary configuration
- `src/main/resources/db/migration/` — Flyway migrations (V1–V67)
- `src/main/java/io/kelta/worker/service/CollectionLifecycleManager.java` — collection init/refresh/teardown
- `src/main/java/io/kelta/worker/workflow/` — workflow engine and scheduled executor
- `src/main/java/io/kelta/worker/listener/` — Kafka event consumers

## Reference Docs

For deeper context, read the root `.claude/docs/`:
- `architecture.md` — Service descriptions, layers, data flows
- `conventions.md` — Java naming, style, import order
- `integrations.md` — Kafka topics, Cerbos, Svix, S3, Redis
- `concerns.md` — Security risks, known bugs, tech debt
