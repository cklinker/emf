# External Integrations

## APIs & Services

| Service | Purpose | SDK/Version | Connection | Key Files |
|---------|---------|------------|------------|-----------|
| Anthropic Claude | AI assistant | `anthropic-java` 2.18.0 | API key | `kelta-ai/.../service/AnthropicService.java` |
| Cerbos | Authorization (gRPC) | `cerbos-sdk-java` 0.12.0 | `${CERBOS_HOST}:${CERBOS_GRPC_PORT:3593}` | `kelta-gateway/.../authz/cerbos/CerbosAuthorizationService.java`, `kelta-worker/.../service/CerbosAuthorizationService.java` |
| Svix | Webhook delivery | `com.svix:svix` 1.68.0 | `${SVIX_SERVER_URL}` | `kelta-worker/.../listener/SvixWebhookPublisher.java`, `.../service/SvixTenantService.java` |
| Superset | Embedded analytics | REST API | `${SUPERSET_URL}` | `kelta-worker/.../service/SupersetApiClient.java`, `kelta-ui/app/` (`@superset-ui/embedded-sdk`) |
| AWS S3 / Garage | Object storage | `aws-sdk-s3` 2.30.1 | `${KELTA_S3_ENDPOINT}` | `kelta-worker/.../service/S3StorageService.java` |
| Keycloak | OIDC federation | Spring Security OAuth2 | Port 8180 (docker-compose) | `kelta-auth/.../federation/FederatedUserMapper.java` |

## Data Storage

| Store | Purpose | Config Key | Details |
|-------|---------|-----------|---------|
| PostgreSQL 15 | Primary DB | `${SPRING_DATASOURCE_URL}` | Multi-tenant per-tenant schemas, Flyway migrations in `kelta-worker/.../db/migration/`. Dynamic user-collection tables are created by `PhysicalTableStorageAdapter.initializeCollection` which uses `CREATE TABLE IF NOT EXISTS` + `SchemaMigrationEngine.reconcileSchema` (introspects `information_schema.columns` and ALTERs to add missing ones) — this path must reconcile before issuing FK constraint statements so post-create `ADD CONSTRAINT FOREIGN KEY` references columns that exist. Because `CREATE TABLE IF NOT EXISTS` is **not atomic against concurrent CREATEs** in PG (two transactions can both pass the existence check and then both INSERT into `pg_type`, losing one with SQLSTATE 23505 on `pg_type_typname_nsp_index`), `initializeCollection` catches `DuplicateKeyException` from the CREATE step and falls through to `reconcileSchema`. This matters whenever the same NATS `CollectionChanged` event is delivered to multiple worker pods in parallel. CI: shared `kelta-ci-db` pool (schema-isolated per run, see `scripts/ci/README.md`); local: docker-compose or Testcontainers |
| OpenSearch 2.17.1 | Full-text search + audit | Port 9200 | `kelta-worker/.../service/OpenSearchQueryService.java`, `OpenSearchAuditService.java` |
| Redis 7 | Cache + sessions | `${REDIS_HOST}:${REDIS_PORT:6379}` | Route caching, permission caching, session management |
| Caffeine | Local in-memory cache | — | Hot-path caching alongside Redis |
| H2 | Test database | — | In-memory for unit tests |

## Messaging

**NATS 2.10** (`nats:2.10-alpine` with `--jetstream`)
- Server: `${NATS_URL:nats://localhost:4222}` (K8s: `nats.nats.svc.cluster.local:4222`)

| Subject | Purpose |
|---------|---------|
| `kelta.config.collection.changed` | Schema change events |
| `kelta.worker.assignment.changed` | Worker assignment changes |
| `kelta.record.changed` | Record CRUD events |

Event envelope: `PlatformEvent<T>` with `eventId`, `eventType`, `tenantId`, `correlationId`, `timestamp`, `payload`
Publishing: `PlatformEventPublisher` (replaces former KafkaTemplate usage)
Subscriptions: `NatsSubscriptionConfig` registration (broadcast consumers for config changes, queue groups for load-balanced work)
Location: `kelta-platform/runtime/runtime-events/src/main/java/io/kelta/event/`

## Monitoring & Observability

**Production (Kubernetes — Grafana LGTM stack in `observability` namespace):**

| Tool | Purpose | K8s Address |
|------|---------|-------------|
| Grafana | Dashboards & visualization | `grafana.observability.svc.cluster.local` |
| Tempo | Distributed tracing | `tempo.observability.svc.cluster.local:3200` |
| Loki | Log aggregation | `loki.observability.svc.cluster.local:3100` |
| Mimir | Metrics storage | `mimir.observability.svc.cluster.local:8080` |
| Alloy | OTLP collector | Routes telemetry from services to LGTM backends |

**Local Development (docker-compose `observability` profile):**

| Tool | Purpose | Port |
|------|---------|------|
| Jaeger 2 | Trace UI + OTLP receiver | 16686 (UI), 4317 (gRPC), 4318 (HTTP) |
| OpenSearch | Trace/log/metric storage | 9200 |

**Instrumentation:**

| Component | Details |
|-----------|---------|
| OpenTelemetry Java Agent | v2.25.0, bundled in all service Dockerfiles |
| Spring Boot OpenTelemetry Starter | Auto-instrumentation for traces, metrics |
| Logstash Logback Encoder | v8.0, JSON structured logging |
| OTLP export | HTTP to port 4318 (configurable via `MANAGEMENT_OTLP_METRICS_EXPORT_URL`) |
| Sampling | W3C propagation, 100% by default (configurable via `OTEL_TRACES_SAMPLER_ARG`) |

## Local Development (docker-compose.yml)

| Service | Port | Profile |
|---------|------|---------|
| PostgreSQL | 5432 | default |
| NATS | 4222 | default |
| Redis | 6379 | default |
| Keycloak | 8180 | default |
| Cerbos | 3592/3593 | default |
| Mailpit (SMTP / UI) | 1025 / 8025 | default |
| Jaeger | 16686 | default |
| OpenSearch | 9200 | default |
| NATS Box | 8090 | tools |
| Redis Commander | 8091 | tools |

## Frontend workspace build (`kelta-web` → `kelta-ui/app`)

`kelta-ui/app` consumes the four `@kelta/*` packages in `kelta-web/packages/*` as `file:` deps — `npm install` symlinks them but does **not** build them, so the `dist/` outputs that `package.json`'s `main`/`module`/`types` point at must exist before `tsc -b` can resolve any of them (otherwise: 46 `TS2307` "Cannot find module" errors).

Build order matters because `plugin-sdk` and `components` use `vite-plugin-dts` with `rollupTypes: true`, which resolves re-exported symbols (e.g. `KeltaClient`) from `@kelta/sdk`'s rolled-up `.d.ts` — that file must already exist. `kelta-web`'s root `build` script and `kelta-ui/Dockerfile` both run:

1. `formula` + `sdk` (no internal deps)
2. `plugin-sdk` + `components` (depend on `formula`/`sdk`)

Local dev: `cd kelta-web && npm install && npm run build` once before `cd kelta-ui/app && npm install && npm run build`.

## Email (SMTP)

Worker emails go out via `spring-boot-starter-mail`. The kelta-worker `application.yml`
defaults target a local **mailpit** (`localhost:1025`, no auth, no TLS) so
`docker compose up` and bare `mvn spring-boot:run` both work out of the box —
mailpit captures every outbound message and serves them at <http://localhost:8025>.

Toggle via `kelta.email.enabled` (`EMAIL_ENABLED` env, default `true`). Override
the SMTP target in K8s via `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`. Per-tenant SMTP overrides live in
`TenantEmailSettings` and take precedence over the platform-wide config.
