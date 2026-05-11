# Architecture

## Pattern

Microservices + Event-Driven + Dynamic Configuration Platform. Multi-tenant with per-tenant schema isolation. Configuration-driven collection/object model (no code deployment for new objects). Event-driven updates via NATS JetStream for schema changes, record mutations, and cache invalidation.

## Services

**Gateway** (`kelta-gateway/`):
- Entry point for all client requests. Spring Cloud Gateway with dynamic route locator.
- Handles: JWT auth, tenant resolution, rate limiting, Cerbos authorization, request routing.
- Entry: `kelta-gateway/src/main/java/io/kelta/GatewayApplication.java`

**Worker** (`kelta-worker/`):
- Generic collection hosting and REST endpoint provider.
- Loads collections on startup, listens for schema changes via NATS JetStream.
- Owns database migrations (Flyway), workflow execution, search indexing.
- Entry: `kelta-worker/src/main/java/io/kelta/WorkerApplication.java`

**Auth** (`kelta-auth/`):
- Internal OIDC provider, identity brokering, MFA.
- Spring Authorization Server for OAuth2. Federated user mapping from external IdPs.
- Entry: `kelta-auth/src/main/java/io/kelta/auth/AuthApplication.java`

**Runtime Libraries** (`kelta-platform/runtime/`):
- `runtime-core` — Collection model, query engine, storage, validation, flow execution
- `runtime-events` — Shared event classes (PlatformEvent<T>)
- `runtime-jsonapi` — JSON:API response formatting
- `runtime-module-core` — Core action handlers (CRUD, flows, decisions)
- `runtime-module-integration` — Integration module
- `runtime-module-schema` — Schema management module

**Admin UI** (`kelta-ui/app/`):
- Vite + React 19 + TypeScript. Builder UI for collections, fields, flows, permissions.
- Entry: `kelta-ui/app/src/main.tsx`

**AI Service** (`kelta-ai/`):
- AI-powered assistant for the platform using Anthropic Claude.
- Chat, proposals, token tracking, conversation history.
- Entry: `kelta-ai/src/main/java/io/kelta/ai/AiApplication.java`

**Frontend SDK** (`kelta-web/`):
- Monorepo: `sdk`, `components`, `plugin-sdk`

## Gateway Filter Chain (ordered)

| Order | Filter | Purpose |
|-------|--------|---------|
| -250 | TenantSlugExtractionFilter | Extract tenant from URL |
| -200 | TenantResolutionFilter | Resolve tenant ID |
| -100 | JwtAuthenticationFilter | Validate JWT |
| 0 | DynamicRouteLocator | Match route from RouteRegistry |
| — | RouteAuthorizationFilter | Cerbos permission check |
| — | HeaderTransformationFilter | Add tenant headers for worker |

Key files: `kelta-gateway/src/main/java/io/kelta/filter/`

## Worker Layers

- **Controllers**: `kelta-worker/src/main/java/io/kelta/controller/` — Admin REST endpoints
- **Dynamic Router**: `runtime-core/.../router/DynamicCollectionRouter.java` — Routes `/api/{collectionName}`
- **Services**: `kelta-worker/src/main/java/io/kelta/service/` — Business logic (CollectionLifecycleManager, CerbosAuthorizationService, SearchIndexService, S3StorageService)
- **Listeners**: `kelta-worker/src/main/java/io/kelta/listener/` — NATS subscribers (CollectionSchemaListener, SearchIndexListener, CerbosCacheInvalidationListener, SvixWebhookPublisher)
- **Data**: `kelta-worker/src/main/java/io/kelta/repository/` — JdbcTemplate + JPA repositories

## Runtime Core Layers

- **Model**: CollectionDefinition, FieldDefinition, FieldType, ReferenceConfig, ValidationRules — `runtime-core/.../model/`
- **Query Engine**: DefaultQueryEngine — pagination, sorting, filtering, field selection, virtual fields — `runtime-core/.../query/`
- **Storage**: PhysicalTableStorageAdapter — PostgreSQL with dynamic schema — `runtime-core/.../storage/`
- **Flow Engine**: Flow execution, node processing, branching — `runtime-core/.../flow/`
- **Modules**: Action handlers (CreateRecord, UpdateRecord, QueryRecords, DeleteRecord, TriggerFlow, Decision, LogMessage) — `runtime-module-core/.../module/core/`

## Frontend Layers (kelta-ui/app/)

- **Context Providers**: `src/context/` — AuthContext, ApiContext, TenantContext, CollectionStoreContext, ThemeContext, I18nContext, PluginContext
- **Pages**: `src/pages/` — 60+ page components
- **Components**: `src/components/` — 50+ reusable components
- **Hooks**: `src/hooks/` — Custom React hooks
- **Services**: `src/services/` — API integration layer

### Component layering — admin app vs. plugin library

`kelta-ui/app/src/components/` and `kelta-web/packages/components/src/` have grown overlapping families of components (data tables, filter builders, field renderers, forms). The consolidation strategy — which variant becomes the base, what features fold in, dependency order, and breaking-change risk for the public `@kelta/components` plugin API — is tracked in `.claude/docs/ui-consolidation-plan.md`. Consult that document before adding a new shared list/form/filter component on either side; reuse a unified component or extend it rather than forking a new variant.

## Data Flow

**HTTP Request (Client -> Gateway -> Worker):**
1. Client sends request (e.g., GET /api/contacts)
2. TenantSlugExtractionFilter extracts tenant from URL
3. TenantResolutionFilter resolves tenant ID
4. JwtAuthenticationFilter validates JWT
5. DynamicRouteLocator matches route from RouteRegistry
6. Request forwarded to Worker with tenant headers
7. DynamicCollectionRouter matches collection
8. QueryEngine executes with pagination/filtering
9. PhysicalTableStorageAdapter queries PostgreSQL
10. JSON:API response formatted via JsonApiResponseBuilder

**NATS Event Flow (Schema Change):**
1. Admin updates collection field via UI
2. Worker publishes `collection-changed` to subject `kelta.config.collection.changed` via PlatformEventPublisher
3. CollectionSchemaListener receives on all workers (broadcast)
4. CollectionLifecycleManager refreshes definition
5. Schema migration triggered (ALTER TABLE)
6. CollectionRegistry updated
7. Downstream: SearchIndexListener syncs OpenSearch, SvixWebhookPublisher notifies

## Key Abstractions

| Abstraction | Purpose | Location |
|-------------|---------|----------|
| CollectionDefinition | Metadata-driven object model | `runtime-core/.../model/CollectionDefinition.java` |
| PlatformEvent<T> | NATS event envelope (tenantId, correlationId, userId) | `runtime-events/.../event/` |
| TenantContext | ThreadLocal tenant isolation | `runtime-core/.../context/TenantContext.java` |
| BootstrapConfig | Gateway startup config (collections, routes, limits) | `kelta-gateway/.../config/BootstrapConfig.java` |

## Where to Add New Code

| What | Where |
|------|-------|
| New collection feature | `runtime-core/.../model/`, `.../query/`, `.../storage/` |
| New worker endpoint | `kelta-worker/.../controller/`, `.../service/` |
| New gateway filter | `kelta-gateway/.../filter/`, `.../config/` |
| New UI page | `kelta-ui/app/src/pages/<PageName>/` |
| New SDK feature | `kelta-web/packages/sdk/src/` |
| New AI feature | `kelta-ai/src/main/java/io/kelta/ai/` |
| Database migration | `kelta-worker/src/main/resources/db/migration/V{next}__description.sql` |

## CI/CD

- **PR checks** (`.github/workflows/ci.yml`): test-java (build runtime + test gateway, worker) + test-frontend → quality-gate
- **Deploy** (`.github/workflows/build-and-publish-containers.yml`): test → build-and-push (gateway, worker, UI images) → deploy → smoke-test
- Custom K8s runner for CI

## Kubernetes

| Resource | Value |
|----------|-------|
| Namespace | `kelta` |
| Gateway | `deployment/kelta-gateway` |
| Auth | `deployment/kelta-auth` |
| Worker | `deployment/kelta-worker` |
| AI | `deployment/kelta-ai` |
| Observability | Grafana + Tempo + Loki + Mimir in `observability` namespace |

```bash
kubectl logs -n kelta deployment/kelta-gateway --tail=200
kubectl logs -n kelta deployment/kelta-worker --tail=200
kubectl logs -n kelta deployment/kelta-worker --since=1h | grep -i "ERROR\|exception"
kubectl get pods -n kelta
kubectl describe pod -n kelta <pod-name>
```

## Cross-Cutting Concerns

- **Logging**: SLF4J + Logback with Logstash JSON encoder, structured parameterized logging
- **Tracing**: OpenTelemetry Java Agent v2.25.0 → Tempo (production, LGTM stack) / Jaeger (local dev)
- **Metrics**: Spring Boot OpenTelemetry starter → Mimir (production) / OTLP HTTP (local dev)
- **Auth**: JWT validation at gateway, Cerbos for fine-grained authorization
- **Multi-tenancy**: TenantContext (ThreadLocal), per-tenant PostgreSQL schemas, tenant-aware NATS events

## Autopilot loop

Two-machine pipeline that turns task briefs into merged PRs without human keystrokes. The **MacBook Pro** runs the planner agent (briefs → structured tasks) and the cockpit (`.claude/dispatcher/status.sh` refreshed every minute by launchd). **`worker-01`** (`craig@192.168.0.232`) runs the dispatcher (`.claude/dispatcher/dispatch.sh` under systemd) which claims work and spawns up to `MAX_PARALLEL` `claude -p` workers, each in its own `/var/lib/emf-wt/<id>` worktree + tmux session.

Tasks flow through [`emf-queue`](../../../emf-queue/README.md): `inbox/` (user briefs + auto-filed bugs) → `ready/` (planner-emitted) → `approved/` (user review) → `in-progress/` (atomic claim) → `done/` (merged) or `failed/` (retries exhausted).

Auto-merge is gated by the `autopilot` PR label: [`.github/workflows/auto-merge.yml`](../../.github/workflows/auto-merge.yml) enables squash auto-merge only when that label is present. Migrations are serialized by an `_active-migration` marker file at the `emf-queue` root — the dispatcher's eligibility filter refuses to claim a second `needs_migration: true` task while it exists. E2E failures auto-file `inbox/BUG-<run-id>.md` via `.github/workflows/post-deploy-validate.yml`; those bug tasks carry `auto_promote: true` and skip user review.

See [`.claude/dispatcher/README.md`](../dispatcher/README.md) for ops, install, and recovery.
