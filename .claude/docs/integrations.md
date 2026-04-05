# External Integrations

## APIs & Services

| Service | Purpose | SDK/Version | Connection | Key Files |
|---------|---------|------------|------------|-----------|
| Cerbos | Authorization (gRPC) | `cerbos-sdk-java` 0.12.0 | `${CERBOS_HOST}:${CERBOS_GRPC_PORT:3593}` | `kelta-gateway/.../authz/cerbos/CerbosAuthorizationService.java`, `kelta-worker/.../service/CerbosAuthorizationService.java` |
| Svix | Webhook delivery | `com.svix:svix` 1.68.0 | `${SVIX_SERVER_URL}` | `kelta-worker/.../listener/SvixWebhookPublisher.java`, `.../service/SvixTenantService.java` |
| Superset | Embedded analytics | REST API | `${SUPERSET_URL}` | `kelta-worker/.../service/SupersetApiClient.java`, `kelta-ui/app/` (`@superset-ui/embedded-sdk`) |
| AWS S3 / Garage | Object storage | `aws-sdk-s3` 2.25.16 | `${KELTA_S3_ENDPOINT}` | `kelta-worker/.../service/S3StorageService.java` |
| Keycloak | OIDC federation | Spring Security OAuth2 | Port 8180 (docker-compose) | `kelta-auth/.../federation/FederatedUserMapper.java` |

## Data Storage

| Store | Purpose | Config Key | Details |
|-------|---------|-----------|---------|
| PostgreSQL 15 | Primary DB | `${SPRING_DATASOURCE_URL}` | Multi-tenant per-tenant schemas, Flyway migrations in `kelta-worker/.../db/migration/` |
| OpenSearch 2.17.1 | Full-text search + audit | Port 9200 | `kelta-worker/.../service/OpenSearchQueryService.java`, `OpenSearchAuditService.java` |
| Redis 7 | Cache + sessions | `${REDIS_HOST}:${REDIS_PORT:6379}` | Route caching, permission caching, session management |
| Caffeine | Local in-memory cache | — | Hot-path caching alongside Redis |
| H2 | Test database | — | In-memory for unit tests |

## Messaging

**Apache Kafka 3.7.0** (KRaft mode, no Zookeeper)
- Bootstrap: `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`

| Topic | Purpose |
|-------|---------|
| `kelta.config.collection.changed` | Schema change events |
| `kelta.worker.assignment.changed` | Worker assignment changes |
| `kelta.record.changed` | Record CRUD events |

Event envelope: `PlatformEvent<T>` with `eventId`, `eventType`, `tenantId`, `correlationId`, `timestamp`, `payload`
Location: `kelta-platform/runtime/runtime-events/src/main/java/io/kelta/event/`

## Monitoring

| Tool | Purpose | Config |
|------|---------|--------|
| Jaeger 2 | Distributed tracing | Ports 16686 (UI), 4317 (gRPC), 4318 (HTTP) |
| OpenTelemetry 1.35.0 | Java instrumentation | Java Agent + JS SDK (kelta-ui) |
| Micrometer | Metrics bridge | Spring Boot Actuator (`/actuator/health`, `/actuator/metrics`) |
| Logstash Logback Encoder 7.4 | JSON structured logging | `kelta-worker/.../logging/OpenSearchLogAppender.java` |

## Local Development (docker-compose.yml)

| Service | Port | Profile |
|---------|------|---------|
| PostgreSQL | 5432 | default |
| Kafka | 9092 | default |
| Redis | 6379 | default |
| Keycloak | 8180 | default |
| Cerbos | 3592/3593 | default |
| Jaeger | 16686 | default |
| OpenSearch | 9200 | default |
| Kafka UI | 8090 | tools |
| Redis Commander | 8091 | tools |
