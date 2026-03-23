# Technology Stack

**Analysis Date:** 2026-03-22

## Languages

**Primary:**
- Java 21 - Backend services (`kelta-platform/pom.xml`, `kelta-gateway/pom.xml`, `kelta-worker/pom.xml`, `kelta-auth/pom.xml`)
- TypeScript 5.9 - Frontend and SDK (`kelta-web/tsconfig.json`, `kelta-ui/app/tsconfig.json`)

**Secondary:**
- JavaScript - Config files, build scripts
- SQL - Flyway migrations (`kelta-worker/src/main/resources/db/migration/`)

## Runtime

**Environment:**
- Java 21 (Eclipse Temurin) - All backend services
- Node.js 18+ - Frontend build and dev
- PostgreSQL 15 - Primary database
- Apache Kafka 3.7.0 (KRaft mode) - Messaging
- Redis 7 - Caching and sessions

**Package Managers:**
- Maven 3.9.9 - Java dependency management (`kelta-platform/pom.xml`)
- npm - TypeScript/Node dependencies (`kelta-web/package.json`, `kelta-ui/app/package.json`)

## Frameworks

**Core:**
- Spring Boot 3.2.2 - Java microservices foundation (`kelta-platform/pom.xml`)
- Spring Cloud Gateway 2023.0.0 - API gateway (`kelta-gateway/pom.xml`)
- Spring Authorization Server 1.2.1 - OAuth2 authorization (`kelta-auth/pom.xml`)
- Spring Security 6.x - Authentication/authorization
- Spring Data JPA & JDBC - Database access
- Spring Kafka - Messaging integration
- Spring Data Redis - Caching
- React 19 / React 18 - UI framework (`kelta-ui/app/package.json`, `kelta-web/package.json`)

**Testing:**
- JUnit 5 (Jupiter) - Java unit/integration tests
- Mockito 5.21.0 - Java mocking (`kelta-platform/pom.xml`)
- AssertJ - Fluent assertions
- Testcontainers 1.19.3 - Docker-based integration testing (`kelta-platform/pom.xml`)
- jqwik 1.8.2 - Property-based testing (`kelta-platform/pom.xml`)
- OkHttp MockWebServer - HTTP mocking (`kelta-gateway/pom.xml`)
- Awaitility - Async test assertions (`kelta-gateway/pom.xml`)
- Vitest 1.3+ - TypeScript unit testing (`kelta-web/vitest.config.ts`)
- React Testing Library 16.3 - Component testing (`kelta-ui/app/package.json`)
- MSW 2.12.7 - Mock Service Worker (`kelta-ui/app/package.json`)
- fast-check 4.5.3 - JS property-based testing (`kelta-web/package.json`)
- Playwright 1.50.0 - E2E testing (`e2e-tests/package.json`)

**Build/Dev:**
- Vite 7.2 / 5.1 - Frontend bundling (`kelta-ui/app/vite.config.ts`)
- TypeScript 5.9 - TypeScript compiler
- ESLint - Linting (`kelta-web/.eslintrc.cjs`)
- Prettier - Formatting (`kelta-web/.prettierrc`)

## Key Dependencies

**Java - Critical:**
- Jackson 2.17.2 - JSON processing (`kelta-worker/pom.xml`)
- Cerbos SDK 0.12.0 - Authorization engine (`kelta-gateway/pom.xml`, `kelta-worker/pom.xml`)
- Flyway - Database migrations (`kelta-worker/pom.xml`)
- Lombok - Boilerplate reduction
- Caffeine - Local in-memory caching (`kelta-gateway/pom.xml`, `kelta-worker/pom.xml`)

**Java - Infrastructure:**
- Svix 1.68.0 - Webhook delivery (`kelta-worker/pom.xml`)
- AWS SDK 2.25.16 - S3 storage (`kelta-worker/pom.xml`)
- OpenSearch REST Client 2.17.1 - Search/audit (`kelta-worker/pom.xml`)
- gRPC Netty 1.63.0 - Service communication (`kelta-gateway/pom.xml`)
- OpenTelemetry 1.35.0 - Distributed tracing
- Logstash Logback Encoder 7.4 - Structured JSON logging

**TypeScript - Critical:**
- Axios 1.13.5 - HTTP client (`kelta-ui/app/package.json`)
- @tanstack/react-query 5.90.20 - Data fetching/caching
- React Hook Form 7.51+ - Form management
- React Router DOM 7.13.0 - Client-side routing
- Zod 4.3.6 - Schema validation
- Tailwind CSS 4.1 - Styling (`kelta-ui/app/package.json`)
- Radix UI 1.4.3 - Headless UI components
- @xyflow/react 12.10.1 - Flow designer
- @superset-ui/embedded-sdk 0.3.0 - Embedded analytics

## Configuration

**Environment:**
- Spring Boot `application.yml` per service (`kelta-gateway/src/main/resources/application.yml`, `kelta-worker/src/main/resources/application.yml`, `kelta-auth/src/main/resources/application.yml`)
- `.env.example` at repo root
- Environment variables for secrets (DATABASE_URL, REDIS_HOST, KAFKA_BOOTSTRAP_SERVERS, etc.)

**Build:**
- `pom.xml` per Java module (parent at `kelta-platform/pom.xml`)
- `vite.config.ts` for frontend builds
- `tsconfig.json` per TypeScript project
- `vitest.config.ts` for test runners

## Platform Requirements

**Development:**
- macOS/Linux (any platform with Java 21 and Node 18)
- Docker Compose for local services (`docker-compose.yml`)
- Local services: PostgreSQL, Kafka, Redis, Keycloak, Cerbos, Jaeger, OpenSearch

**Production:**
- Kubernetes (namespace: `kelta`)
- Docker containers (Eclipse Temurin JRE 21 for Java, Nginx 1.25-alpine for frontend)
- ArgoCD for GitOps deployment (separate repo: `homelab-argo`)
- OpenTelemetry Java Agent v2.25.0 in runtime images

---

*Stack analysis: 2026-03-22*
*Update after major dependency changes*
