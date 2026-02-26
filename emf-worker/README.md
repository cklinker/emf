# EMF Worker

Generic collection hosting worker for the EMF platform. Owns database migrations, serves REST endpoints for all collections via `DynamicCollectionRouter`, executes workflows, and publishes record change events.

## Architecture

```
                    ┌──────────────────────────┐
                    │       emf-worker         │
                    ├──────────────────────────┤
  Kafka ──────────► │ CollectionSchemaListener │ ◄── schema change events
                    │ WorkflowEventListener    │ ◄── record change events
                    ├──────────────────────────┤
  HTTP  ──────────► │ DynamicCollectionRouter  │ ◄── JSON:API CRUD for all collections
                    │ InternalBootstrapCtrl    │ ◄── gateway bootstrap, permissions
                    │ GovernorLimitsCtrl       │ ◄── rate limit config + usage
                    ├──────────────────────────┤
                    │ CollectionLifecycleMgr   │ ◄── init, refresh, teardown
                    │ WorkflowEngine           │ ◄── rule evaluation + execution
                    │ ScheduledWorkflowExec    │ ◄── polls for due scheduled rules
                    ├──────────────────────────┤
                    │ PostgreSQL (Flyway)       │ ◄── 67 versioned migrations
                    │ Redis (rate limit counts) │
                    │ Kafka (event publishing)  │
                    │ S3 (optional attachments) │
                    └──────────────────────────┘
```

## Key Packages

| Package | Description |
|---------|-------------|
| `controller` | `InternalBootstrapController` (gateway bootstrap, tenants, OIDC, permissions), `GovernorLimitsController` |
| `service` | `WorkerBootstrapService` (startup init), `CollectionLifecycleManager` (collection lifecycle), `S3StorageService` (attachment URLs) |
| `listener` | `CollectionSchemaListener` (Kafka schema events), `WorkflowEventListener` (record change → workflow) |
| `event` | `KafkaRecordEventPublisher` -- publishes record CRUD events to `emf.record.changed` |
| `workflow` | `ScheduledWorkflowExecutor` -- polls for due rules every 60s with optimistic locking |
| `filter` | `RequestMetricsFilter` -- Prometheus metrics per collection (request count, duration, errors) |
| `advice` | `AttachmentUrlEnricher` -- enriches JSON:API responses with presigned S3 download URLs |
| `config` | Kafka, storage, metrics, workflow, and S3 configuration beans |

## Internal API Endpoints

These endpoints are called by the gateway (not exposed externally):

| Endpoint | Description |
|----------|-------------|
| `GET /internal/bootstrap` | Returns all active collections + tenant governor limits |
| `GET /internal/tenants/slug-map` | Slug-to-tenant-ID mapping |
| `GET /internal/oidc/by-issuer` | OIDC provider lookup by issuer URI |
| `GET /internal/permissions` | Resolves effective permissions for a user (profiles + permission sets + groups) |

## Configuration

Key properties from `application.yml`:

```yaml
spring.datasource:
  url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:emf_control_plane}
  username: ${DB_USERNAME:emf}
  password: ${DB_PASSWORD:emf}

spring.flyway:
  enabled: ${FLYWAY_ENABLED:true}
  baseline-on-migrate: true

spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}

emf:
  storage.mode: PHYSICAL_TABLES
  worker.id: ${emf.worker.id:emf-worker-default}
  workflow:
    enabled: true
    scheduled.poll-interval-ms: 60000
  storage.s3:
    enabled: false
    bucket: emf-attachments
    presigned-url-expiry-minutes: 15
```

## Database Migrations

Flyway migrations are in `src/main/resources/db/migration/` (V1 through V67). Key migrations:

| Range | Content |
|-------|---------|
| V1-V3 | Core schema (collections, fields, tenants), OIDC seeding, default menus |
| V10-V12 | Users, permissions, sharing |
| V15 | Picklist tables |
| V26 | Workflow rules |
| V39 | Worker-specific tables |
| V42-V43 | Notes, attachments, system collections |
| V50 | Demo data (ecommerce clothing store) |
| V55 | Enhanced permission model |
| V59-V62 | Workflow engine foundation |
| V66-V67 | BaseEntity columns for junction/layout tables |

## Governor Limits

Default per-tenant limits tracked via `GovernorLimitsController`:

| Limit | Default |
|-------|---------|
| API calls/day | 100,000 |
| Storage | 10 GB |
| Max users | 100 |
| Max collections | 200 |
| Max fields/collection | 500 |
| Max workflows | 50 |
| Max reports | 200 |

## Metrics

Prometheus metrics exposed at `/actuator/prometheus`:

- `emf_worker_request_total` -- counter by collection, method, status
- `emf_worker_request_duration_seconds` -- histogram by collection, method
- `emf_worker_error_total` -- counter by collection, error type
- `emf.worker.collections.active` -- gauge of active collections
- `emf.worker.collection.count` -- gauge for HPA scaling

## Building

```bash
# Build runtime dependencies first
mvn clean install -DskipTests -f emf-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B

# Build and test worker
mvn verify -f emf-worker/pom.xml -B
```

## Running Locally

Requires PostgreSQL, Redis, and Kafka. Start infrastructure via Docker Compose from the repo root:

```bash
docker-compose up -d
mvn spring-boot:run -f emf-worker/pom.xml
```

The worker starts on port **8080** (mapped to **8083** in Docker Compose).

## Testing

JUnit 5 + Mockito. Tests cover bootstrap, governor limits, schema listeners, workflow execution, metrics, S3 storage, and event publishing.

```bash
mvn verify -f emf-worker/pom.xml -B
```

## Docker

Multi-stage build: `maven:3.9-eclipse-temurin-21` (build) -> `eclipse-temurin:21-jre` (runtime). Runs as non-root user `emf` on port 8080 with G1GC and 75% RAM allocation.

## Dependencies

- Java 21, Spring Boot 3.2.2
- All `runtime-*` modules (core, events, jsonapi, module-core, module-integration, module-schema)
- PostgreSQL + Flyway, Spring Kafka, Spring Data Redis
- AWS SDK S3 (optional, for attachment presigned URLs)
