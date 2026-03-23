# Codebase Structure

**Analysis Date:** 2026-03-22

## Directory Layout

```
emf/
├── kelta-platform/                  # Shared runtime libraries (Java)
│   ├── pom.xml                      # Parent POM with Spring Boot BOM
│   └── runtime/
│       ├── runtime-core/            # Core: models, query engine, storage, flows
│       ├── runtime-events/          # Kafka event classes (PlatformEvent<T>)
│       ├── runtime-jsonapi/         # JSON:API response formatting
│       ├── runtime-module-core/     # CRUD action handlers
│       ├── runtime-module-integration/ # Integration module
│       └── runtime-module-schema/   # Schema management module
├── kelta-gateway/                   # API Gateway service (Java)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/io/kelta/
│       ├── filter/                  # Request filter chain
│       ├── route/                   # Dynamic routing
│       ├── authz/                   # Cerbos authorization
│       ├── config/                  # Spring configuration
│       ├── listener/                # Kafka event consumers
│       └── metrics/                 # Custom gateway metrics
├── kelta-worker/                    # Worker service (Java)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/io/kelta/
│       │   ├── controller/          # REST controllers
│       │   ├── service/             # Business logic
│       │   ├── repository/          # Data access
│       │   ├── listener/            # Kafka listeners
│       │   └── config/              # Spring configuration
│       └── resources/
│           ├── application.yml
│           └── db/migration/        # Flyway migrations (V1-V65)
├── kelta-auth/                      # Auth service (Java)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/io/kelta/auth/
│       ├── controller/              # Auth endpoints
│       ├── federation/              # OIDC federation
│       ├── config/                  # Security & OAuth2 config
│       └── service/                 # Auth business logic
├── kelta-ui/                        # Admin/Builder UI
│   ├── Dockerfile
│   └── app/
│       ├── package.json
│       ├── vite.config.ts
│       └── src/
│           ├── main.tsx             # Entry point
│           ├── App.tsx              # Root component
│           ├── pages/               # 60+ page components
│           ├── components/          # 50+ reusable components
│           ├── context/             # React context providers
│           ├── hooks/               # Custom hooks
│           ├── services/            # API layer
│           ├── types/               # TypeScript types
│           ├── utils/               # Helpers
│           ├── lib/                 # Shared libraries
│           ├── i18n/                # Internationalization
│           ├── styles/              # Styling
│           └── telemetry/           # OpenTelemetry setup
├── kelta-web/                       # Frontend SDK monorepo
│   ├── package.json
│   ├── vitest.config.ts
│   ├── Dockerfile
│   └── packages/
│       ├── sdk/                     # TypeScript SDK
│       ├── components/              # React component library
│       └── plugin-sdk/              # Plugin development SDK
├── e2e-tests/                       # Playwright E2E tests
├── docker-compose.yml               # Local dev services
├── docker/                          # Docker configs (Keycloak, Jaeger)
├── .github/workflows/               # CI/CD pipelines
└── CLAUDE.md                        # Claude Code instructions
```

## Key File Locations

**Entry Points:**
- `kelta-gateway/src/main/java/io/kelta/GatewayApplication.java` - Gateway service
- `kelta-worker/src/main/java/io/kelta/WorkerApplication.java` - Worker service
- `kelta-auth/src/main/java/io/kelta/auth/AuthApplication.java` - Auth service
- `kelta-ui/app/src/main.tsx` - Admin UI
- `kelta-web/packages/sdk/src/index.ts` - SDK entry

**Configuration:**
- `kelta-platform/pom.xml` - Parent POM (Spring Boot version, shared deps)
- `kelta-gateway/src/main/resources/application.yml` - Gateway config
- `kelta-worker/src/main/resources/application.yml` - Worker config
- `kelta-auth/src/main/resources/application.yml` - Auth config
- `kelta-ui/app/vite.config.ts` - UI build config
- `kelta-web/vitest.config.ts` - SDK test config
- `docker-compose.yml` - Local development services

**Core Logic:**
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/router/DynamicCollectionRouter.java` - API routing
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/query/DefaultQueryEngine.java` - Query execution
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/storage/PhysicalTableStorageAdapter.java` - DB storage
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/model/CollectionDefinition.java` - Object model
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/model/FieldType.java` - Field types enum
- `kelta-gateway/src/main/java/io/kelta/route/DynamicRouteLocator.java` - Gateway routing

**Database Migrations:**
- `kelta-worker/src/main/resources/db/migration/` - Flyway (V1-V65)

**CI/CD:**
- `.github/workflows/ci.yml` - PR checks
- `.github/workflows/build-and-publish-containers.yml` - Build, push, deploy

## Naming Conventions

**Java Files:**
- PascalCase class names matching filename: `CollectionDefinition.java`
- Test suffix: `CollectionDefinitionTest.java`
- Package: `io.kelta.<service>.<feature>`

**TypeScript Files:**
- PascalCase for React components: `FilterBuilder.tsx`
- camelCase for modules: `tokenManager.ts` (SDK), PascalCase directories for pages/components (UI)
- Test suffix: `*.test.ts`, `*.test.tsx`, `*.property.test.ts`

**Directories:**
- Java: lowercase single-word packages (`filter/`, `route/`, `authz/`, `config/`)
- TypeScript: PascalCase for component dirs (`FilterBuilder/`), camelCase for utility dirs

## Where to Add New Code

**New Collection Feature (Java):**
- Model: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/model/`
- Query: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/query/`
- Storage: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/storage/`
- Tests: parallel `src/test/java/` structure

**New Worker Endpoint:**
- Controller: `kelta-worker/src/main/java/io/kelta/controller/`
- Service: `kelta-worker/src/main/java/io/kelta/service/`
- Tests: `kelta-worker/src/test/java/io/kelta/`

**New Gateway Filter:**
- Filter: `kelta-gateway/src/main/java/io/kelta/filter/`
- Config: `kelta-gateway/src/main/java/io/kelta/config/`
- Tests: `kelta-gateway/src/test/java/io/kelta/`

**New UI Page:**
- Page: `kelta-ui/app/src/pages/<PageName>/`
- Components: `kelta-ui/app/src/components/<ComponentName>/`
- Hooks: `kelta-ui/app/src/hooks/`

**New SDK Feature:**
- Client: `kelta-web/packages/sdk/src/`
- Components: `kelta-web/packages/components/src/`
- Tests: co-located `*.test.ts`

**Database Migration:**
- `kelta-worker/src/main/resources/db/migration/V{next}__description.sql`

---

*Structure analysis: 2026-03-22*
*Update when directory structure changes*
