# EMF Platform

EMF (Enterprise Metadata Framework) is a platform for building dynamic, runtime-configurable enterprise applications. It provides a metadata-driven architecture where collections (tables), fields, validation rules, relationships, and workflows are all defined and managed at runtime — no redeployment required.

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   emf-ui    │────▶│ emf-gateway  │────▶│  emf-worker  │
│  (React)    │     │ (API Gateway)│     │  (Services)  │
└─────────────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │
                    ┌──────┴───────┐      ┌──────┴───────┐
                    │    Redis     │      │  PostgreSQL  │
                    │   (Cache)    │      │     (DB)     │
                    └──────────────┘      └──────────────┘
                           │
                    ┌──────┴───────┐
                    │    Kafka     │
                    │  (Events)    │
                    └──────────────┘
```

## Project Structure

| Module | Description |
|--------|-------------|
| `emf-platform/runtime/runtime-core` | Core runtime library — collection management, query engine, validation, dual storage modes |
| `emf-platform/runtime/runtime-events` | Shared Kafka event classes for lifecycle events |
| `emf-platform/runtime/runtime-jsonapi` | Shared JSON:API model classes |
| `emf-platform/runtime/runtime-module-core` | Workflow action handlers (field updates, record CRUD, tasks, decisions) |
| `emf-platform/runtime/runtime-module-integration` | Integration action handlers (HTTP callouts, email, scripts, delays) |
| `emf-platform/runtime/runtime-module-schema` | Schema lifecycle hooks for system collections |
| `emf-gateway` | Spring Cloud Gateway — authentication, authorization, JSON:API processing |
| `emf-worker` | Worker service — owns database migrations, executes business logic |
| `emf-web` | TypeScript SDK, React component library, and plugin SDK |
| `emf-ui/app` | Admin/builder UI application |

## Tech Stack

**Backend:** Java 21, Spring Boot 3.2, Spring Cloud Gateway, Maven

**Frontend:** TypeScript, React, Vite, Vitest, Tailwind CSS

**Infrastructure:** PostgreSQL 15, Redis 7, Kafka 3.7 (KRaft), Keycloak 23 (OIDC)

**Deployment:** Docker, Kubernetes, ArgoCD

## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- Node.js 18+
- Docker & Docker Compose

## Getting Started

### 1. Start Infrastructure

```bash
cp .env.example .env
docker-compose up -d
```

This starts PostgreSQL, Redis, Kafka, and Keycloak. Optional debugging tools (Kafka UI, Redis Commander, pgAdmin) are available via the `tools` profile:

```bash
docker-compose --profile tools up -d
```

### 2. Build Runtime Libraries

The runtime modules must be built first as they are dependencies for the gateway and worker.

```bash
mvn clean install -DskipTests -f emf-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B
```

### 3. Run Backend Services

```bash
# Gateway (port 8080)
mvn spring-boot:run -f emf-gateway/pom.xml

# Worker (port 8083)
mvn spring-boot:run -f emf-worker/pom.xml
```

### 4. Run Frontend

```bash
cd emf-web && npm install
cd emf-ui/app && npm install && npm run dev
```

## Running Tests

### Java

```bash
# Build runtime (required before testing)
mvn clean install -DskipTests -f emf-platform/pom.xml \
  -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema \
  -am -B

# Test gateway
mvn verify -f emf-gateway/pom.xml -B

# Test worker
mvn verify -f emf-worker/pom.xml -B
```

### Frontend

```bash
cd emf-web
npm run lint
npm run typecheck
npm run format:check
npm run test:coverage
```

## Local Services

| Service | URL | Notes |
|---------|-----|-------|
| Gateway API | http://localhost:8080 | Main API entry point |
| Worker | http://localhost:8083 | Worker service |
| Keycloak | http://localhost:8180 | OIDC provider (admin/admin) |
| Kafka UI | http://localhost:8090 | Requires `tools` profile |
| Redis Commander | http://localhost:8091 | Requires `tools` profile |
| pgAdmin | http://localhost:8092 | Requires `tools` profile |

## Environment Variables

See [`.env.example`](.env.example) for the full list. Key variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Kafka broker address |
| `OIDC_ISSUER_URI` | `http://localhost:8180/realms/emf` | Keycloak OIDC issuer |
| `SECURITY_ENABLED` | `true` | Enable/disable authentication |
| `KAFKA_EVENTS_ENABLED` | `true` | Enable/disable Kafka event publishing |

## CI/CD

Pull requests run the full quality gate via GitHub Actions:

1. **Build & Test Java** — builds runtime modules, runs gateway and worker tests
2. **Test Frontend** — lint, typecheck, format check, and test coverage for emf-web
3. **Quality Gate** — all jobs must pass before merge

On merge to `main`, container images are built, pushed, and deployed to Kubernetes via ArgoCD.
