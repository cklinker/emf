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

**Write-response relationships contract** — POST/PATCH responses on
`/api/{collection}` (and the `/api/{parent}/{parentId}/{child}` sub-resource
variants) always echo the caller's JSON:API `relationships` block back in the
response document. `DynamicCollectionRouter#toJsonApiResourceObject` builds
relationships from REFERENCE/LOOKUP/MASTER_DETAIL field metadata, and
`mergeRequestRelationships` then fills in any caller-supplied relationship
names that don't map to a REFERENCE-typed field. Field-derived entries take
precedence; request-supplied entries fill the gaps. This keeps a stable
"follow-up by id" shape for `create_record` / `update_record` callers so they
don't need a second GET to discover related-record IDs.

**Read-side include resolution** — `GET /api/{collection}[/{id}]?include=<name>`
on `DynamicCollectionRouter#resolveIncludes` follows three resolution paths
per include name, in order: (1) collection-name has-many — the named
collection has an FK pointing to the primary; (2) collection-name belongs-to —
the primary has FK(s) pointing to the named collection (covers `created_by` /
`updated_by` → `users`); (3) **field-name lookup** — the include name is a
field on the primary collection with a non-null `referenceConfig`. The
field-name path covers both LOOKUP-typed fields and the legacy
"STRING-with-refConfig" form where the FK UUID is stored on `attributes`
(e.g. `availability.attributes.title`); it injects `relationships.<field>.data
= { type, id }` on each primary resource and hydrates the referenced rows
into top-level `included[]`. The raw UUID stays on `attributes` for
back-compat. FK values are deduplicated before the single `id IN (…)` query
to the target collection. A separate **transitive pass** then resolves
grandchild includes via already-resolved direct children (e.g.
`page-layouts ?include=layout-sections,layout-fields` queries `layout-fields`
by `sectionId IN (section ids)` after `layout-sections` resolves directly).
Includes are skipped silently when the target is unresolvable — `200` with
no `included` entry — never `5xx`.

**Pagination contract** — every paginated REST endpoint uses JSON:API
bracket syntax (`page[number]` / `page[size]`). Parsing lives in
`runtime-core/.../query/Pagination.fromParams`, which clamps `page[size]`
against `MAX_HTTP_PAGE_SIZE` (200) — separate from `MAX_PAGE_SIZE` (1000),
the absolute constructor ceiling that internal services (report execution,
data export, include resolution) build `Pagination` records against
directly. When the caller's `page[size]` exceeds the cap,
`DynamicCollectionRouter.toJsonApiListResponse` echoes the modification as
`meta.requestedPageSize=<caller value>` and `meta.pageSizeClamped=true`
so clients can detect the clamp without inferring it from `data.length`
vs. `meta.pageSize` — preventing the "200 + populated `totalCount` +
empty `data`" misread that previously cost debugging time. The same map
is also emitted under the legacy top-level `metadata` key (deprecated
alias retained for backward compatibility with pre-`meta` clients). List responses
also carry a `links` block (`self` / `prev` / `next`) built by
`runtime-jsonapi/.../PaginationLinks#build`; URLs are relative paths so
cached system-collection responses remain reusable across hosts and behind
load balancers. MCP tools (`query_collection`, `list_picklists`,
`list_approvals`) accept flat `pageNumber` / `pageSize` arguments and
translate them to the bracket form at the MCP→gateway boundary. Bounds and
response shape are documented in `.claude/docs/conventions.md`.

**Error response ownership** — every 4xx/5xx is wrapped in the JSON:API
`{"errors":[{status, code, title, detail, source?, meta?}]}` envelope. Three
construction sites:

| Layer | Class | Scope |
|-------|-------|-------|
| Gateway (reactive) | `kelta-gateway/.../error/GlobalErrorHandler` | auth (401), authz (403), rate-limit (429), missing-route (404), `ResponseStatusException`, generic (500) |
| Worker / runtime (servlet) | `kelta-platform/runtime/runtime-core/.../router/GlobalExceptionHandler` | bean validation (400), `ConstraintViolationException`, malformed body (`HttpMessageNotReadableException`), missing/mismatched params, `NoHandlerFoundException` / `NoResourceFoundException` (404), method/media-type not supported (405/415), platform `ValidationException` / `InvalidQueryException` / `UniqueConstraintViolationException`, generic (500) |
| Library | `kelta-platform/runtime/runtime-jsonapi/.../JsonApiResponseBuilder.error(...)` | helper for one-off error documents in controllers; the 3-arg overload derives `code` from `title` so older callers stay spec-compliant |

`source.pointer` (RFC 6901, e.g. `/data/attributes/name`) carries field-level
context; `source.parameter` is used for query/path-parameter errors.

**Orphan-column filtering on record reads/writes** —
`SchemaMigrationEngine.createDeprecateColumnMigration` only marks a deleted
field's column with a `DEPRECATED` comment; it never `ALTER TABLE ... DROP
COLUMN`. `PhysicalTableStorageAdapter` then keeps returning the stale column
via `SELECT *`. `DynamicCollectionRouter.toJsonApiResourceObject` is the gate
that keeps the public payload in sync with the live field set: a key only
reaches `data.attributes` if it has a live `FieldDefinition`, is one of the
framework-metadata keys (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`,
`tenantId`, `recordTypeId`), or is a `_currency_code` / `_longitude` /
`_latitude` companion of a still-live CURRENCY/GEOLOCATION field. Anything
else is treated as an orphan column left behind by a deleted field and
dropped. This is the projection layer for the deprecate-don't-drop schema
strategy — preserving data on the disk while hiding it from the API.

**NATS Event Flow (Schema Change):**
1. Admin updates collection field via UI
2. Worker publishes `collection-changed` to subject `kelta.config.collection.changed` via PlatformEventPublisher
3. CollectionSchemaListener receives on all workers (broadcast)
4. CollectionLifecycleManager refreshes definition
5. Schema migration triggered (ALTER TABLE)
6. CollectionRegistry updated
7. Downstream: SearchIndexListener syncs OpenSearch, SvixWebhookPublisher notifies

**Config-change NATS subjects** (broadcast, every pod consumes):

| Subject | Published by | Consumed by | Purpose |
|---------|--------------|-------------|---------|
| `kelta.config.collection.changed.<id>` | `CollectionConfigEventPublisher` (BeforeSaveHook) | `CollectionSchemaListener`, gateway route registry | Collection metadata changed |
| `kelta.config.flow.changed.<tenantId>` | `FlowConfigEventPublisher` (BeforeSaveHook) | `FlowEventListener` | Flow trigger cache refresh |
| `kelta.config.credential.changed.<id>` | `CredentialEventPublisher` (BeforeSaveHook) | `CredentialCacheInvalidationListener` | Credential vault cache |
| `kelta.config.domain.changed.<domainId>` | `CustomDomainEventPublisher` (called from `TenantDomainController`) | `CustomDomainCacheInvalidationListener` | Custom domain → tenant slug cache |
| `kelta.config.feature.changed.<tenantId>` | `SystemFeatureEventPublisher` (called from `GovernorLimitsController`) | `SystemFeatureCacheInvalidationListener` | Tenant limits / feature toggle caches |

Custom domains and tenant settings live in raw JDBC tables rather than
system collections, so the publishers are invoked directly from the
controller after the mutation rather than via the BeforeSaveHook registry.

## Key Abstractions

| Abstraction | Purpose | Location |
|-------------|---------|----------|
| CollectionDefinition | Metadata-driven object model | `runtime-core/.../model/CollectionDefinition.java` |
| PlatformEvent<T> | NATS event envelope (tenantId, correlationId, userId) | `runtime-events/.../event/` |
| TenantContext | ThreadLocal tenant isolation | `runtime-core/.../context/TenantContext.java` |
| BootstrapConfig | Gateway startup config (collections, routes, limits) | `kelta-gateway/.../config/BootstrapConfig.java` |
| CompositeUniqueConstraintService | Issues `CREATE UNIQUE INDEX` over multi-column tuples; constraint state lives in Postgres (no separate registry) | `runtime-core/.../storage/CompositeUniqueConstraintService.java` |
| MetadataDependencyService | Builds a per-tenant metadata dependency graph on demand (collections/fields/flows/layouts/rules/record-types/list-views/constraints/perms) for impact analysis + cycle detection; not persisted, so always current. Endpoints: `GET /api/metadata/impact`, `GET /api/metadata/graph` | `kelta-worker/.../service/MetadataDependencyService.java`, `.../dependency/MetadataDependencyGraph.java` |

### Unique constraints

Single-column uniqueness is declared on `FieldDefinition.unique()` and emitted
inline as a `UNIQUE` column in the table DDL. Composite (multi-column) unique
constraints are issued separately at runtime via
`POST /api/admin/collections/{name}/unique-constraints` (worker controller →
`CompositeUniqueConstraintService` → `CREATE UNIQUE INDEX uniq_<table>_<cols>`).
Index names are clamped to PostgreSQL's 63-char limit by
`PhysicalTableStorageAdapter.buildBoundedIdentifier`. Duplicate inserts on the
constrained tuple bubble up as `DuplicateKeyException` →
`UniqueConstraintViolationException` → JSON:API 409 via `GlobalExceptionHandler`;
the originating index name is parsed out of the Postgres error message so the
409 payload identifies the violated composite. The `create_unique_constraint`
MCP admin tool wraps the same endpoint.

### Field types and pgvector

`FieldType` (`runtime-core/.../model/FieldType.java`) is the canonical enum
covering every column kind the platform emits. The DDL mapping lives in two
parallel switches — `SchemaMigrationEngine.mapFieldTypeToSql` (ALTER-time)
and `PhysicalTableStorageAdapter.mapFieldTypeToSql` (CREATE-time). Both
overloads accept the surrounding `FieldDefinition` because some types are
parameterized by `fieldTypeConfig`.

Long-form text uses two distinct enum values that map to the same Postgres
`TEXT` column but stay semantically separate so the admin UI can pick its
editor: `TEXT` → plain textarea, `RICH_TEXT` → rich-text editor.
`SystemCollectionSeeder.mapFieldType` stores them as `"text"` and
`"rich_text"` in the `fields.type` column; `CollectionLifecycleManager.reverseMapFieldType`
inverts those.

`VECTOR` emits `vector(N)` and therefore requires the pgvector extension
(`CREATE EXTENSION IF NOT EXISTS vector`) on every tenant database. `N`
comes from `FieldDefinition.fieldTypeConfig` key `"dimension"` and is
clamped to `1..16000`; the default is `1536` (OpenAI text-embedding-3
small). The MCP `add_field` tool exposes a top-level `dimension` argument
that the gateway stamps into `fieldTypeConfig`. Until pgvector is
provisioned on a tenant DB, `CREATE TABLE` / `ALTER TABLE` for a VECTOR
column will fail at execution time — there is no startup probe.

### Email template resolution — tenant override → system default

System-level email templates are seeded under the sentinel `tenant_id = 'system'`
(the schema's `tenant_id` column is `NOT NULL` with an FK to `tenant`, so a
sentinel row is used instead of a real `NULL`). V133 seeds eight defaults
addressable by the stable `template_key` column (`user.invite`,
`user.password_reset`, …) and V141 seeds three more addressable by the
human-friendly `name` column (`password_reset`, `user_invite`, `welcome`).

`EmailRepository.findTemplateByKey(tenantId, key)` and
`EmailRepository.findTemplateByName(tenantId, name)` both query
`tenant_id IN (?, 'system')` and order tenant rows ahead of system rows, so a
tenant override (a row the tenant inserted with the same key/name) wins; the
system default is only returned when no tenant override exists. Callers don't
branch on the source — the same row shape is returned in either case.

### User invitation flow

`POST /api/internal/email/invite` (worker `InternalEmailController`) accepts
`{ email, tenantId, inviteToken }` and sends the invitation email using the
`user_invite` template (V141, name-based lookup). The controller resolves the
tenant display name, builds an `inviteLink` from `kelta.external-base-url` +
the token, substitutes `${inviteLink}` and `${tenantName}`, and queues delivery
via `DefaultEmailService.sendByName` — which honours the tenant's SMTP
credential override (or falls back to the platform default) the same way as
the rest of the email pipeline. `kelta-auth`'s `WorkerClient.sendInviteEmail`
calls this endpoint after a federated user is newly JIT-provisioned, so SSO
sign-ups receive a notification email in addition to their existing session.

### Scheduled flow cron handling

SCHEDULED flows carry their schedule in `triggerConfig.cron`. The worker bridges
that user-facing config to the `scheduled_job` table (which the
`ScheduledJobExecutorService` polls) through `FlowScheduleSyncHook` — a
`BeforeSaveHook` on the `flows` collection.

- **Format**: Spring's `CronExpression.parse` requires a 6-field expression
  (`seconds minutes hours dom month dow`). Users almost universally write the
  standard 5-field form (`0 */4 * * *`).
- **Normalization & validation (write path)**: `FlowScheduleSyncHook.beforeCreate`
  / `beforeUpdate` route `triggerConfig.cron` through `worker/util/CronExpressions`.
  A 5-field expression is normalized to 6-field by prepending `0 ` (seconds) and
  the rewritten value is returned as a `BeforeSaveResult.withFieldUpdates` so the
  stored `triggerConfig.cron` is always canonical. Anything still unparseable
  produces a `BeforeSaveResult.error("triggerConfig.cron", …)` which
  `DefaultQueryEngine` raises as a `ValidationException` → JSON:API 400 with
  `cron must be a 6-field Spring expression (with seconds); got '…'`. Silent
  drop is forbidden — historically the parse failure was swallowed in
  `afterCreate` and SCHEDULED flows with 5-field crons persisted with no
  `scheduled_job` row, which masked broken provider-refresh and ingest flows
  for hours.
- **Sync (read path)**: `afterCreate`/`afterUpdate` upsert the
  `scheduled_job` row using the normalized cron; `afterDelete` (or a flow type
  change away from SCHEDULED) removes it. The hook also normalizes defensively
  here so callers that bypass the lifecycle (seed-data ingestion writing
  directly to storage) still land valid rows.
- **Read-time call sites**: `ScheduledJobRepository.calculateNextRunAt` and the
  `/api/scheduled-jobs/{id}/resume` + `/api/scheduled-jobs/validate-cron`
  endpoints route through the same `CronExpressions.normalize`, so legacy
  5-field rows in `scheduled_job` resume cleanly and the UI's pre-save
  validation endpoint accepts the same input as the write path.
- **Status surface (per-flow)**: `FlowScheduleStatusController` exposes
  `GET /api/flows/{id}/schedule` and `GET /api/flows/{id}/runs` so the UI can
  show whether the flow is actually wired to the executor without `psql`
  access. `/schedule` returns `{cron, timezone, active, lastRunAt, lastStatus,
  nextRunAt}` from `scheduled_job` plus a derived `scheduleStatus`:
  `ACTIVE`/`PAUSED` for healthy rows, `NONE` for non-SCHEDULED flows, or
  `UNSYNCED` (with a `reason`) when a SCHEDULED flow has no `scheduled_job`
  row at all or has an active row with `next_run_at IS NULL` — both cases
  mean the executor will never pick it up. `/runs` returns recent
  `job_execution_log` rows (status, startedAt, completedAt, durationMs,
  errorMessage) joined via `scheduled_job` so the result is tenant-scoped.

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
