# External Integrations

**Analysis Date:** 2026-03-22

## APIs & External Services

**Authorization:**
- Cerbos - Open-source authorization engine (gRPC)
  - SDK/Client: `dev.cerbos:cerbos-sdk-java` v0.12.0
  - Auth: gRPC at `${CERBOS_HOST:cerbos.emf.svc.cluster.local}:${CERBOS_GRPC_PORT:3593}`
  - Files: `kelta-gateway/src/main/java/io/kelta/authz/cerbos/CerbosAuthorizationService.java`, `kelta-worker/src/main/java/io/kelta/service/CerbosAuthorizationService.java`
  - Permission caching with policy-change-driven invalidation

**Identity & Authentication:**
- Keycloak - OIDC Provider federation
  - Port 8180 in docker-compose
  - Realm config: `docker/keycloak/kelta-realm.json`
- kelta-auth (Internal) - Spring Authorization Server
  - Files: `kelta-auth/src/main/java/io/kelta/auth/AuthApplication.java`
  - Federated user mapping: `kelta-auth/src/main/java/io/kelta/auth/federation/FederatedUserMapper.java`

**Webhooks:**
- Svix - Webhook delivery platform
  - SDK/Client: `com.svix:svix` v1.68.0
  - REST API: `${SVIX_SERVER_URL:http://localhost:8071}`
  - Files: `kelta-worker/src/main/java/io/kelta/listener/SvixWebhookPublisher.java`, `kelta-worker/src/main/java/io/kelta/service/SvixTenantService.java`

**Analytics:**
- Apache Superset - Embedded analytics
  - REST API: `${SUPERSET_URL}`
  - Files: `kelta-worker/src/main/java/io/kelta/service/SupersetApiClient.java`, `kelta-worker/src/main/java/io/kelta/service/SupersetTenantService.java`, `kelta-worker/src/main/java/io/kelta/service/SupersetDatasetService.java`, `kelta-worker/src/main/java/io/kelta/service/SupersetGuestTokenService.java`
  - Frontend: `@superset-ui/embedded-sdk` in `kelta-ui/app/`

**Cloud Storage:**
- AWS S3 - Object storage for attachments
  - SDK: `software.amazon.awssdk:s3` v2.25.16
  - Endpoint: `${KELTA_S3_ENDPOINT:http://localhost:3900}`
  - Files: `kelta-worker/src/main/java/io/kelta/service/S3StorageService.java`

## Data Storage

**Databases:**
- PostgreSQL 15 - Primary relational database
  - Connection: `jdbc:postgresql://${SPRING_DATASOURCE_URL:localhost:5432/kelta_control_plane}`
  - Config: `kelta-worker/src/main/resources/application.yml`, `kelta-auth/src/main/resources/application.yml`
  - Client: Spring Data JPA + JdbcTemplate
  - Migrations: Flyway at `kelta-worker/src/main/resources/db/migration/` (V1-V65)
  - Multi-tenant with per-tenant schemas

**Search:**
- OpenSearch 2.17.1 - Full-text search and audit logging
  - Port 9200 in docker-compose
  - Files: `kelta-worker/src/main/java/io/kelta/service/OpenSearchQueryService.java`, `kelta-worker/src/main/java/io/kelta/service/OpenSearchAuditService.java`, `kelta-worker/src/main/java/io/kelta/config/OpenSearchClientConfig.java`

**Caching:**
- Redis 7 - Distributed cache and session storage
  - Connection: `spring.data.redis.host: ${REDIS_HOST:localhost}`, port `${REDIS_PORT:6379}`
  - Config: `kelta-gateway/src/main/resources/application.yml`, `kelta-worker/src/main/resources/application.yml`
  - Used for: route caching, permission caching, session management (kelta-auth)
- Caffeine - Local in-memory caching
  - Used alongside Redis for hot-path caching

**Testing Database:**
- H2 - In-memory database for unit tests

## Messaging & Events

**Message Broker:**
- Apache Kafka 3.7.0 (KRaft mode, no Zookeeper)
  - Bootstrap: `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`
  - Topics:
    - `kelta.config.collection.changed` - Schema change events
    - `kelta.worker.assignment.changed` - Worker assignment changes
    - `kelta.record.changed` - Record CRUD events
  - Event envelope: `PlatformEvent<T>` with `eventId`, `eventType`, `tenantId`, `correlationId`, `timestamp`, `payload`
  - Files: `kelta-platform/runtime/runtime-events/src/main/java/io/kelta/event/`

## Monitoring & Observability

**Distributed Tracing:**
- Jaeger 2 (OTLP collector & UI)
  - Ports: 16686 (UI), 4317 (gRPC), 4318 (HTTP)
  - Config: `docker/jaeger/config.yaml`
- OpenTelemetry API 1.35.0 (Java instrumentation)
- OpenTelemetry JS 1.25.0+ (Client-side tracing in kelta-ui)
- Micrometer Tracing Bridge (Micrometer to OTEL)

**Logging:**
- Logback + Logstash Logback Encoder 7.4 - JSON structured logging
- OpenSearch Log Appender - `kelta-worker/src/main/java/io/kelta/logging/OpenSearchLogAppender.java`

**Metrics:**
- Spring Boot Actuator - Health & metrics endpoints (`/actuator/health`, `/actuator/metrics`)
- Custom gateway metrics: `kelta-gateway/src/main/java/io/kelta/metrics/GatewayMetrics.java`

## CI/CD & Deployment

**CI Pipeline:**
- GitHub Actions
  - `.github/workflows/ci.yml` - test-java + test-frontend
  - `.github/workflows/build-and-publish-containers.yml` - build, push, deploy, smoke-test
  - Runner: `k8s-runner` (custom Kubernetes runner)

**Container Registry:**
- Docker Hub (username: `cklinker`)

**Deployment:**
- Kubernetes (namespace: `kelta`)
- ArgoCD (GitOps) - Separate repo: `https://github.com/cklinker/homelab-argo`

**Container Images:**
- Eclipse Temurin JRE 21 for Java services
- Nginx 1.25-alpine for frontends
- Node 18-alpine for build stages
- OpenTelemetry Java Agent v2.25.0 bundled

## Development Tools

**Local Development (docker-compose.yml profiles):**
- Kafka UI (port 8090) - profile: tools
- Redis Commander (port 8091) - profile: tools
- PostgreSQL 15 (port 5432)
- Kafka (port 9092)
- Redis (port 6379)
- Keycloak (port 8180)
- Cerbos (port 3592/3593)
- Jaeger (port 16686)
- OpenSearch (port 9200)

---

*Integration audit: 2026-03-22*
*Update when adding/removing external services*
