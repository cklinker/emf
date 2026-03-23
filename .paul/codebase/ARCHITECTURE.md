# Architecture

**Analysis Date:** 2026-03-22

## Pattern Overview

**Overall:** Microservices + Event-Driven + Dynamic Configuration Platform

**Key Characteristics:**
- Multi-tenant with per-tenant schema isolation
- Configuration-driven collection/object model (no code deployment for new objects)
- Event-driven updates via Kafka for schema changes, record mutations, and cache invalidation
- Gateway pattern for routing, authentication, rate limiting
- Separate auth service for OIDC federation

## Services

**Gateway Service** (`kelta-gateway/`):
- Entry point for all client requests
- Spring Cloud Gateway with dynamic route locator
- Handles: JWT auth, tenant resolution, rate limiting, authorization, request routing
- Entry: `kelta-gateway/src/main/java/io/kelta/GatewayApplication.java`

**Worker Service** (`kelta-worker/`):
- Generic collection hosting and REST endpoint provider
- Loads collections on startup, listens for schema changes via Kafka
- Owns database migrations (Flyway), workflow execution, search indexing
- Entry: `kelta-worker/src/main/java/io/kelta/WorkerApplication.java`

**Auth Service** (`kelta-auth/`):
- OIDC provider federation and user identity management
- Spring Authorization Server for OAuth2
- Federated user mapping from external IdPs
- Entry: `kelta-auth/src/main/java/io/kelta/auth/AuthApplication.java`

**Runtime Libraries** (`kelta-platform/runtime/`):
- `runtime-core` - Collection model, query engine, storage, validation, flow execution
- `runtime-events` - Shared Kafka event classes (PlatformEvent<T>)
- `runtime-jsonapi` - JSON:API response formatting
- `runtime-module-core` - Core action handlers (CRUD, flows, decisions)
- `runtime-module-integration` - Integration module
- `runtime-module-schema` - Schema management module

**Frontend - Admin UI** (`kelta-ui/app/`):
- Vite + React 19 + TypeScript
- Builder UI for collections, fields, flows, permissions
- Entry: `kelta-ui/app/src/main.tsx`

**Frontend - SDK** (`kelta-web/`):
- Monorepo with packages: `sdk`, `components`, `plugin-sdk`
- TypeScript SDK for Kelta API consumption

## Layers

### Gateway Layers

**Filter Chain (ordered by execution):**
- Purpose: Request processing pipeline
- Contains: Tenant resolution, JWT auth, rate limiting, security headers, logging
- Key files: `kelta-gateway/src/main/java/io/kelta/filter/` (TenantResolutionFilter -200, JwtAuthenticationFilter -100, IpRateLimitFilter, etc.)

**Routing Layer:**
- Purpose: Dynamic route matching and forwarding
- Contains: `DynamicRouteLocator.java`, `RouteRegistry.java`, `RouteDefinition.java`
- Location: `kelta-gateway/src/main/java/io/kelta/route/`

**Authorization Layer:**
- Purpose: Cerbos-based permission enforcement
- Contains: `CerbosAuthorizationService.java`, `CerbosPermissionResolver.java`
- Location: `kelta-gateway/src/main/java/io/kelta/authz/`

### Worker Layers

**Controller Layer:**
- Purpose: Custom REST endpoints for admin operations
- Location: `kelta-worker/src/main/java/io/kelta/controller/`
- Examples: `WorkflowMigrationController.java`, `SearchController.java`, `GovernorLimitsController.java`

**Dynamic Router:**
- Purpose: Routes `/api/{collectionName}` requests to appropriate collection
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/router/DynamicCollectionRouter.java`
- Supports: GET/POST/PUT/DELETE, sub-resources, pagination, sorting, filtering

**Service Layer:**
- Purpose: Business logic for each domain
- Location: `kelta-worker/src/main/java/io/kelta/service/`
- Key: `CollectionLifecycleManager.java`, `CerbosAuthorizationService.java`, `SearchIndexService.java`, `S3StorageService.java`

**Event Listener Layer:**
- Purpose: React to Kafka events for side effects
- Location: `kelta-worker/src/main/java/io/kelta/listener/`
- Key: `CollectionSchemaListener.java`, `SearchIndexListener.java`, `CerbosCacheInvalidationListener.java`, `SvixWebhookPublisher.java`

**Data Layer:**
- Purpose: Database access
- Location: `kelta-worker/src/main/java/io/kelta/repository/`
- Pattern: JdbcTemplate-based queries + JPA repositories

### Runtime Core Layers

**Model Layer:**
- `CollectionDefinition.java`, `FieldDefinition.java`, `FieldType.java` (28 types), `ReferenceConfig.java`, `ValidationRules.java`
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/model/`

**Query Engine:**
- `QueryEngine.java` (interface), `DefaultQueryEngine.java` (implementation)
- Supports: pagination, sorting, filtering, field selection, virtual fields
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/query/`

**Storage:**
- `PhysicalTableStorageAdapter.java` - PostgreSQL storage with dynamic schema
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/storage/`

**Flow Engine:**
- Flow execution, node processing, branching logic
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/flow/`

**Module System:**
- Action handlers: CreateRecord, UpdateRecord, QueryRecords, DeleteRecord, TriggerFlow, Decision, LogMessage
- Location: `kelta-platform/runtime/runtime-module-core/src/main/java/io/kelta/module/core/`

### Frontend Layers (kelta-ui/app/)

**Context Providers:** `src/context/` - AuthContext, ApiContext, TenantContext, CollectionStoreContext, ThemeContext, I18nContext, PluginContext
**Pages:** `src/pages/` - 60+ page components (objects, collections, fields, flows, analytics, admin)
**Components:** `src/components/` - 50+ reusable components (data tables, forms, editors, navigation)
**Hooks:** `src/hooks/` - Custom React hooks
**Services:** `src/services/` - API integration layer

## Data Flow

**HTTP Request (Client -> Gateway -> Worker):**
1. Client sends request (e.g., GET /api/contacts)
2. TenantSlugExtractionFilter (order -250) extracts tenant from URL
3. TenantResolutionFilter (order -200) resolves tenant ID
4. JwtAuthenticationFilter (order -100) validates JWT
5. DynamicRouteLocator matches route from RouteRegistry
6. Request forwarded to Worker with tenant headers
7. DynamicCollectionRouter matches collection
8. QueryEngine executes with pagination/filtering
9. PhysicalTableStorageAdapter queries PostgreSQL
10. JSON:API response formatted via JsonApiResponseBuilder

**Kafka Event Flow (Schema Change):**
1. Admin updates collection field via UI
2. Worker publishes `collection-changed` to `kelta.config.collection.changed`
3. CollectionSchemaListener receives on all workers
4. CollectionLifecycleManager refreshes definition
5. Schema migration triggered (ALTER TABLE)
6. CollectionRegistry updated
7. Downstream: SearchIndexListener syncs OpenSearch, SvixWebhookPublisher notifies

**State Management:**
- Server: Per-tenant PostgreSQL schemas, Redis for caching, Kafka for events
- Client: React Query for server state, React Context for UI state

## Key Abstractions

**CollectionDefinition:**
- Metadata-driven object model (name, fields, relationships, validation, permissions)
- Built via builder pattern, registered in CollectionRegistry
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/model/CollectionDefinition.java`

**PlatformEvent<T>:**
- Generic Kafka event envelope with tenantId, correlationId, userId
- Payload types: RecordChangedPayload, CollectionChangedPayload, ModuleChangedPayload
- Location: `kelta-platform/runtime/runtime-events/src/main/java/io/kelta/event/`

**TenantContext:**
- ThreadLocal tenant isolation
- Location: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/context/TenantContext.java`

**BootstrapConfig:**
- Gateway startup configuration (collections, routes, governor limits)
- Fetched from worker `/api/bootstrap` endpoint
- Location: `kelta-gateway/src/main/java/io/kelta/config/BootstrapConfig.java`

## Entry Points

**Gateway:** `kelta-gateway/src/main/java/io/kelta/GatewayApplication.java`
**Worker:** `kelta-worker/src/main/java/io/kelta/WorkerApplication.java`
**Auth:** `kelta-auth/src/main/java/io/kelta/auth/AuthApplication.java`
**UI:** `kelta-ui/app/src/main.tsx`

## Error Handling

**Strategy:** Exception-based with centralized error handling
- Gateway: Filter-level error handling with proper HTTP status codes
- Worker: Service exceptions caught at controller level
- Frontend: React Error Boundaries + React Query error handling

## Cross-Cutting Concerns

**Logging:** SLF4J + Logback with Logstash JSON encoder, structured parameterized logging
**Tracing:** OpenTelemetry (Java Agent + JS SDK) -> Jaeger
**Metrics:** Micrometer + Spring Boot Actuator
**Auth:** JWT validation at gateway, Cerbos for fine-grained authorization
**Multi-tenancy:** TenantContext (ThreadLocal), per-tenant PostgreSQL schemas, tenant-aware Kafka events

---

*Architecture analysis: 2026-03-22*
*Update when major patterns change*
