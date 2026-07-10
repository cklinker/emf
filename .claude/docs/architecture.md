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

Order values below are the live `getOrder()` returns from source (lower runs first):

| Order | Filter | Purpose |
|-------|--------|---------|
| -310 | CustomDomainFilter | Map custom domain → tenant |
| -300 | TenantSlugExtractionFilter | Extract tenant slug from URL |
| -200 | TenantResolutionFilter | Resolve slug → tenant ID |
| -150 | IpRateLimitFilter | Per-IP rate limiting |
| -100 | JwtAuthenticationFilter | Validate JWT |
| -99 | PatAuthenticationFilter | Validate PAT (`klt_`) as JWT alternative |
| -50 | RateLimitFilter / UserIdentityResolutionFilter | Per-tenant rate limit; user identity |
| -40 | TenantIpAllowlistFilter | Per-tenant IP allowlist on `/api/**` (fail-open; `MANAGE_TENANTS` bypass) |
| 0 | RouteAuthorizationFilter | Cerbos object-level permission check (DynamicRouteLocator then forwards to worker) |
| 50 | HeaderTransformationFilter | Inject `X-Tenant-*` headers for worker |
| 100 | SecurityHeadersFilter | Response security headers |
| 200 | SecurityAuditFilter | Record final response status |

Cross-cutting (off main path): `ObservabilityContextFilter (-90)`, `HttpBodyCaptureFilter
(-80)`, `SystemCollectionResponseCacheFilter (-10)`, `RequestLoggingFilter (MAX)`. `?include=`
resolution is done by `IncludeResolver` (`jsonapi` package), not a numbered filter; read-side
FLS is enforced in the worker (`CerbosFieldSecurityAdvice`), not the gateway.

Key files: `kelta-gateway/src/main/java/io/kelta/gateway/{filter,auth,authz,ratelimit}/`

### Authorizing a new endpoint (read before adding one)

Cerbos enforcement is **collection/record-scoped, not blanket**. Concretely:

- **Collection API routes** (`/{tenant}/{collection}`): the gateway `RouteAuthorizationFilter`
  (order 0) runs the per-resource Cerbos object check, and the worker
  `CerbosRecordAuthorizationAdvice` + FLS advices add record/field checks.
  - **Cerbos `collectionId` keying (important).** The `collection` policy CEL (gateway) and
    the `record`/`field` policies (worker advices) key `R.attr.collectionId` on **different
    identifiers**, because their checkers pass different values. The gateway's
    `RouteAuthorizationFilter.checkObjectPermission` passes `route.getId()` = the bootstrap
    `collection.id` (a **UUID**), so `CerbosPolicyGenerator.generateCollectionPolicy` keys the
    collection CEL on the UUID. The worker `CerbosRecordAuthorizationAdvice` and
    `CerbosFieldSecurityAdvice` take `collectionId` from the **URL path segment** (the collection
    **name**), and record-share widening joins by name — so `generateRecordPolicy` and
    `generateFieldPolicy` key their CEL on the **name**. `generateRecordPolicy` therefore takes a
    `collectionIdToName` map and translates the per-collection + custom-rule CEL literals to names
    while still looking up object permissions by the stored UUID. Get this wrong in one direction
    and per-collection allow rules silently never match: a UUID-keyed record CEL denies every
    record to non-`VIEW_ALL_DATA` profiles (they'd see empty lists everywhere).
- **Static routes** (`/api/admin/**`, `/api/me/**`, `/api/_search/**`, `/api/metrics/**`,
  `/api/operations`): `RouteAuthorizationFilter` **skips** ids starting `static-` — they get only
  the blanket `API_ACCESS` system-permission check. The worker advices **exclude** `/api/admin/`.
  So a new `/api/admin/...` endpoint is, by default, reachable by **any** authenticated user with
  API access. To put a *specific* permission on it, **enforce it inside the controller/service**
  (see "Worker-side system-permission check" below — inject `CerbosPermissionResolver` and
  check `profile_system_permission`). **`POST /api/operations`** (atomic ops) is a static route, so
  the gateway's per-collection Cerbos verb check doesn't run on it — instead
  `AtomicOperationsController` mirrors that check server-side: per operation it maps the verb
  (`add→create`, `update→edit`, `remove→delete`), resolves the collection UUID, and calls
  `CerbosAuthorizationService.checkCollectionAccess` (object-level `collection` resource, **UUID**-keyed,
  identical to the gateway), denying the whole batch (403, fail-closed) before executing if any op is
  unauthorized. The `IdentityCollectionGuardHook` still guards identity-collection writes underneath
  (see "Delegated administration" below).
- **Approval writes** (`/api/approvals/{submit,{id}/approve|reject|recall}`): the acting user
  is ONLY the gateway-stamped `X-User-Id` (an email — `IdentityHeaderStripFilter` strips
  client-supplied values, `HeaderTransformationFilter` re-stamps from the validated
  principal), resolved to the `platform_user` UUID via `UserIdResolver` in
  `ApprovalController.resolveActingUser` (fail-closed 403 when absent/unresolvable). Body
  `userId`/`submittedBy` are accepted on the wire but **inert** (the pre-hardening spoof
  path). Authorization stays in `ApprovalService` (`assigned_to`/`submitted_by` UUID
  comparison). `GET /api/me/identity` exposes the same resolution to the frontend (the JWT
  `sub` is the UUID only on direct-login; the auth-code flow mints the email).
- **User preference writes** (`/api/user-ui-preferences`, generic route): owner-guarded by
  `UserPreferenceGuardHook` (BeforeSaveHook, order −100) — the row's `userId` must equal the
  caller's canonical UUID (`X-User-Id` → `UserIdResolver`; fail-closed on unresolvable,
  internal tier admitted). Reads remain tenant-scoped only (see concerns.md).
- **Delegated administration** (`/api/admin/delegated*`, security feature): full admins define
  `delegated-admin-scopes` (V157) so listed non-admins manage users within limits without
  `MANAGE_USERS`. `DelegatedAdminScopeController` (`MANAGE_DELEGATED_ADMINS`) owns scope CRUD;
  `DelegatedUserAdminController` (`/api/admin/delegated`) resolves each caller's fresh
  `DelegatedAdminService.effectiveScope` (unions active scopes, re-filters out now-privileged
  profiles/permsets at request time) and enforces it in-controller (field whitelist, in-scope
  profile/permset checks, self-edit block). Its validated writes bind `DelegatedWriteContext`.
  Defense in depth: the wildcard **`IdentityCollectionGuardHook`** (BeforeSaveHook, incl. the new
  `beforeDelete` SPI) blocks any *identified* HTTP write to `users`/`user-permission-sets`/
  `group-memberships`/`delegated-admin-scopes` that lacks `MANAGE_USERS`/`MODIFY_ALL_DATA` (or
  `MANAGE_DELEGATED_ADMINS` for scopes) unless a `DelegatedWriteContext` is bound — this is what
  makes the delegated path safe against the generic collection route and `/api/operations`. The
  gateway `IdentityHeaderStripFilter` (order −400) strips client-supplied `X-User-Email`/
  `X-User-Profile-Id`/`X-User-Profile-Name`/`X-Cerbos-Scope`/`X-User-Type` at the chain head so the
  worker never trusts a forged identity header on any path.
- **Portal identity** (telehealth slice 1, V167): `platform_user.user_type ∈ INTERNAL|PORTAL`.
  Portal users are **passwordless** — no `user_credential` row exists (the form-login query
  inner-joins it, structurally excluding them); their only entry is the magic-link flow on
  kelta-auth (`/portal/login`, `/portal/login/request`, `/portal/login/verify` — permitAll pages;
  single-use SHA-256-hashed tokens in `portal_login_token`, 15-min PORTAL_LOGIN / 7-day
  PORTAL_INVITE, enumeration-safe uniform responses, per-user rate limit). Verified logins get a
  normal auth session; the JWT carries a `user_type` claim which the gateway re-stamps as
  `X-User-Type` (`HeaderTransformationFilter`; pre-claim tokens read as INTERNAL).
  `POST /api/admin/users/portal-invite` (existing `/api/admin/**` static route,
  `MANAGE_USERS` in-controller) creates the user with the seeded **Portal User** profile
  (API_ACCESS only) and enforces the `maxPortalUsers` governor; re-posting an existing portal
  email re-issues the invite link. Record-level access for portal users is granted per record via
  `ParticipantShareSupport` → `record_share` (the existing Cerbos share-widening path).
- **Chat** (telehealth slice 2, V168): `/api/chat/**` is a static route — ALL authz is
  in-controller (`ChatController`): participant membership for reads/sends (actor fail-closed
  from gateway-stamped `X-User-Id` → `UserIdResolver` + `X-User-Type`; body-supplied sender
  ignored), INTERNAL-only queue views/claims, `MANAGE_CHAT` for `view=all` + cross-assignment.
  The four chat system collections deliberately carry NO object-permission rows, so the
  generic JSON:API routes admit only `VIEW_ALL_DATA`/`MODIFY_ALL_DATA` holders — and
  `ChatMessageHook` re-validates writes on that path too (participant sender, open
  conversation, messages immutable). Writes go through `QueryEngine` (hooks +
  record.changed fire); the hooks publish ids-only `kelta.chat.message/conversation.*`
  events. WebSocket: `chat.join` is verified against `/internal/chat/.../members`
  (reactive `ChatMembershipClient`, 30s cache, fail-closed) before the session enters the
  per-conversation routing index (20 joins/session); `ChatMessageBridge` fans only to
  joined sessions — never the tenant-wide collection broadcast. To keep that true even
  though chat writes emit generic `record.changed` (QueryEngine path → flows/search/audit),
  `RealtimeBridge` SKIPS `chat-*` collections and the WS `subscribe` action rejects them
  (chat.join is the only socket channel for chat). Message bodies never leave the
  authorized HTTP path (not in NATS chat payloads, WS events, or search indexes).
- **Telehealth scheduling** (slice 4, V169): `/api/telehealth/**` is a static route — authz
  in-controller (`TelehealthController`): portal actors act on themselves (body-supplied
  `portalUserId` ignored for portal callers), providers on their own appointments,
  `view=provider` staff-only; user types re-validated server-side. Booking runs under a
  per-provider `pg_advisory_xact_lock` + `SlotService` re-check in one transaction
  (race-safe, no DB extensions) and writes via `QueryEngine` (`AppointmentHook` validates
  the window + grants the portal participant `record_share`). The scheduling collections
  carry NO object-permission rows (admin-only generic JSON:API), like chat.
  `GET /api/telehealth/visits/{token}` is an **unauthenticated path** (signed HMAC visit
  token → live-row re-check → fresh single-use portal login token → 302 to the auth
  verify). Reminders: `AppointmentReminderSweep` (atomic UPDATE-claim, multi-pod safe).
- **Analytics endpoints** (`/api/reports/{id}/execute|export`, `/api/dashboards/{id}/data`,
  `/api/dashboards/{id}/components/{cid}/data`): static routes, so gated **in-controller** —
  `ReportExecutionController`/`DashboardDataController.requireAnalyticsAccess` requires a granted
  **`VIEW_ANALYTICS`** (or `MANAGE_REPORTS`, the authoring permission) via the standard
  `CerbosPermissionResolver` + `BootstrapRepository.findProfileSystemPermissions` check;
  fail-closed 403 on missing identity. The gate runs *before* each endpoint's try/catch (a
  `ResponseStatusException` thrown inside would be swallowed into a 500). Scheduled report
  delivery calls `ReportExecutionService` at the service layer and is deliberately ungated
  (system-trust tier, same contract as masking's null principal).
- **End-user analytics viewer** (slice 3): `/app/dashboards/:id` consumes the pass-through
  dashboard-data contract — `POST /api/dashboards/{id}/data` returns widget payloads keyed by
  componentId with NO layout metadata; the client joins them onto `dashboard-components` rows
  (positions/spans/config) fetched via the generic JSON:API route. Per-viewer masking is
  server-side; a masked-field rejection surfaces as that widget's `error` string, rendered
  verbatim.
- **New top-level path**: a brand-new `/api/<x>/**` segment must be registered as a static
  route in `kelta-gateway/.../service/RouteConfigService.registerStaticRoutes()` (and
  `config/RouteInitializer.registerStaticRoutes()`) or the gateway returns **404**. Sub-paths
  under an existing segment need no new route.
- **Public (unauthenticated) endpoint**: to expose a path with no JWT, add its prefix to
  `kelta.gateway.security.unauthenticated-paths` (all methods) or `public-paths` (GET/HEAD only)
  in the gateway `application.yml`, *and* register its worker static route. Example: mass-email
  campaign tracking — `CampaignTrackingController` at `/api/track/**` (static route `track` +
  `unauthenticated-paths`), authenticated solely by the HMAC token in the link, not the session.
  The endpoint resolves its own tenant from the verified token and records events under
  `TenantContext.withTenant`. Campaign management itself is a normal `/api/admin/campaigns`
  endpoint gated in-controller on `MANAGE_CAMPAIGNS` (the DB-lookup pattern above).
- **Sandbox/promotion/package routes** (2026-07-04): `/api/environments/**` and
  `/api/promotions/**` (static routes `environments`/`promotions`) are gated in-controller on
  **`MANAGE_SANDBOXES`**; `/api/packages/**` (static route `packages`) on
  **`CUSTOMIZE_APPLICATION`**. All three use the `MigrationController` pattern
  (`CerbosPermissionResolver.getProfileId` + `BootstrapRepository.findProfileSystemPermissions`,
  fail-closed 403). Creator/approver/executor identity comes from the gateway-forwarded
  `X-User-Id` header — never from the request body. Cross-tenant work inside these services
  uses explicit `TenantContext.callWithTenant(<uuid>, …)`; **never `runAsPlatform`** — no RLS
  policy matches the `__platform__` sentinel, so reads under it silently return zero rows
  (see concerns.md).

**Worker-side system-permission check — how (use the existing pattern, don't reinvent):**
- Enforce a specific system permission **in the controller/service** with a DB lookup against
  `profile_system_permission` (`profile_id`, `permission_name`, `granted = true`). The reusable
  callable building block is `BootstrapRepository.findProfileSystemPermissions(profileId)` (also
  used by `UserPermissionsController`); `SupersetGuestTokenService.hasSystemPermission` shows the
  same query inline but is `private` — copy the pattern, don't call it. On deny, throw
  `ResponseStatusException(HttpStatus.FORBIDDEN, …)` (existing admin controllers tend to *degrade*
  on missing identity rather than hard-deny, so write the explicit deny yourself). The worker
  does **not** make a Cerbos call for system permissions — Cerbos `system_feature` policies back
  the *gateway's* blanket `API_ACCESS` check; per-endpoint admin gating is the DB check above.
- Identity: inject **`CerbosPermissionResolver`** and read `getProfileId(request)`,
  `getEmail(request)`, `getProfileName(request)`, `getTenantId(request)`, `hasIdentity(request)`.
  The gateway's `RouteAuthorizationFilter.forwardWithHeaders` sets `X-User-Profile-Id`,
  `X-User-Email`, `X-User-Profile-Name`, `X-Cerbos-Scope` on **every** forwarded request —
  including `/api/admin/**` — so `profileId` **is** available; don't re-resolve it.
- Permission **names** are `profile_system_permission.permission_name` values; the canonical
  catalog lives in the frontend `SystemPermissionChecklist.tsx` (`VIEW_SETUP`, `MANAGE_USERS`,
  `API_ACCESS`, `VIEW_ALL_DATA`, …) — no Java enum. A new permission only gates once it is
  granted on the relevant profiles (rows in `profile_system_permission`).

### Tenant context in the worker

Per-request tenant is **already bound** by `kelta-worker/.../filter/TenantContextFilter` from
the gateway's `X-Tenant-ID` / `X-Tenant-Slug` headers (as `ScopedValue`s). In a worker
controller, **read** `TenantContext` (or accept `@RequestHeader("X-Tenant-ID")`) — do **not**
call `TenantContext.runWithTenant(...)` on a request path; that's for background / cross-tenant
jobs. RLS then scopes every query automatically.

## Worker Layers

- **Controllers**: `kelta-worker/src/main/java/io/kelta/controller/` — Admin REST endpoints
- **Dynamic Router**: `runtime-core/.../router/DynamicCollectionRouter.java` — Routes `/api/{collectionName}`
- **Services**: `kelta-worker/src/main/java/io/kelta/service/` — Business logic (CollectionLifecycleManager, CerbosAuthorizationService, SearchIndexService, S3StorageService)
- **Listeners**: `kelta-worker/src/main/java/io/kelta/listener/` — NATS subscribers (CollectionSchemaListener, SearchIndexListener, CerbosCacheInvalidationListener, SvixWebhookPublisher)
- **Data**: `kelta-worker/src/main/java/io/kelta/repository/` — JdbcTemplate + JPA repositories

## Runtime Core Layers

- **Model**: CollectionDefinition, FieldDefinition, FieldType, ReferenceConfig, ValidationRules — `runtime-core/.../model/`
- **Query Engine**: DefaultQueryEngine — pagination, sorting, filtering, field selection, virtual fields — `runtime-core/.../query/`
- **Storage**: `StorageAdapter` SPI — `DispatchingStorageAdapter` (`@Primary`) routes each op to the adapter backing the target collection, keyed by `storageConfig().adapterConfig().get("adapterType")` (absent/unknown → `PhysicalTableStorageAdapter`, the PostgreSQL dynamic-schema default). External backends implement the `ExternalStorageAdapter` marker (so the dispatcher can collect them as `List<ExternalStorageAdapter>` without a circular bean ref) and override `storageType()`. Consumers that inject `PhysicalTableStorageAdapter` concretely (e.g. unique-constraint checks) bypass routing. `ExternalRestStorageAdapter` (`storageType=external-rest`) maps CRUD/query to a remote REST API over the tiny injectable `RestExecutor` HTTP seam (config in `adapterConfig`: `baseUrl`/`path`/`dataPath`/`idAttribute`/`bearerToken`); pagination + sort + `EQ` filters push down best-effort. `ExternalJdbcStorageAdapter` (`storageType=external-jdbc`) maps CRUD/query to SQL on a foreign table via an injectable `ExternalJdbcConnectionProvider` (config: `jdbcUrl`/`table`/`idColumn`); values bind as params and all identifiers are pattern-validated before interpolation. — `runtime-core/.../storage/`
- **Flow Engine**: Flow execution, node processing, branching — `runtime-core/.../flow/`
- **Modules**: Action handlers (CreateRecord, UpdateRecord, QueryRecords, DeleteRecord, TriggerFlow, Decision, LogMessage) — `runtime-module-core/.../module/core/`

## Frontend Layers (kelta-ui/app/)

- **Context Providers**: `src/context/` — AuthContext, ApiContext, TenantContext, CollectionStoreContext, ThemeContext, I18nContext, PluginContext
- **Pages**: `src/pages/` — 60+ page components
- **Components**: `src/components/` — 50+ reusable components
- **Hooks**: `src/hooks/` — Custom React hooks
- **Services**: `src/services/` — API integration layer

### Component layering — admin app vs. plugin library

`kelta-ui/app/src/components/` and `kelta-web/packages/components/src/` have grown overlapping families of components (data tables, filter builders, field renderers, forms). The rule (see `conventions.md` → Component reuse): **reuse the unified `@kelta/components` variant or extend it — never fork a new app-side variant.** This protects the public `@kelta/components` plugin API. Consult `conventions.md` before adding a new shared list/form/filter component on either side.

### Record detail path — one `RecordShell` for both stacks (unified record)

There is now **one record-detail path**. Both the end-user runtime (`/:tenant/app/o/:collection/:id`, `ObjectDetailPage`) and the admin Resource Browser (`/:tenant/resources/:collection/:id`, `ResourceDetailPage`) are **thin `variant` wrappers over `RecordShell`** (`kelta-ui/app/src/components/record/RecordShell.tsx`): `RecordShell` owns the page skeleton (loading/status branches + breadcrumb → header → body(+rail) → tab bar → below-tabs → dialogs), variant chrome is passed as slots, and the field body renders through `RecordDetailBody` (`LayoutFieldSections` when the layout has sections, else a variant fallback). The shared `DetailTabBar` drives related lists (with inline CRUD) + Notes/Attachments/System tabs for both. A fix to view/inline-edit/related-CRUD/rules/optimistic-locking lands once and shows in both stacks. Do **not** reintroduce a per-stack detail body. (The list pages — `ObjectListPage`/`ResourceListPage` — are not yet converged; they share `ObjectDataTable` but keep separate page shells.)

**Offline replica path (end-user only).** When `OfflineProvider` is mounted — it wraps the `EndUserShell` subtree — the shared data hooks route through a tenant-scoped IndexedDB replica: online reads write through to the store and offline reads serve it (`useCollectionRecords`/`useRecord`/`usePageDataSources`), and offline writes queue to an outbox (`useRecordMutation` → `engine.queue`) that flushes on reconnect (`SyncEngine.sync`). Admin pages render outside the provider (`useOffline()` → `undefined`), so their reads/writes stay online-only and unchanged. See `conventions.md` → offline hooks.

## Data Flow

**Tenant-slug resolution (cold-cache safe).** `TenantSlugExtractionFilter` resolves the URL slug via
`GatewayCacheManager.resolveTenantSlug`, backed by a Caffeine cache refreshed from the worker
(`/internal/tenants/slug-map`) every 60s. On a **cache miss** it now **lazily fetches** the map from
the worker (bounded 2s), merges it, and negative-caches a still-missing slug (mirroring
`resolveCustomDomain`) — so a valid tenant resolves immediately during the cold-cache window
(gateway restart, or a refresh that failed while the worker was briefly unreachable) instead of
returning `404 TENANT_NOT_FOUND` for every `/{slug}/api/**` request (which strands the UI before it
can even reach login). A fetch error never negative-caches a valid slug; the next request retries.

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

The field-name path and the always-on relationship serialization both require the field's
`referenceConfig` to be present. `CollectionLifecycleManager.loadFieldsFromDb` builds it from the
`reference_target` column, but many LOOKUP/MASTER_DETAIL fields were persisted with a **null
`reference_target`** (only `reference_collection_id` set) — those silently lost their
`referenceConfig`, so the worker serialized a raw FK-id attribute and `?include=<field>` resolved
nothing. The loader now **derives the target name from `reference_collection_id`** (via
`resolveReferenceTarget`) when `reference_target` is blank, so such fields serialize as relationships
and resolve includes. **Consumer contract:** clients request a *forward* lookup by the **field name**
(`?include=title`), NOT the target collection name (`?include=titles`); the end-user list/detail/
related views build `?include=` from reference field names accordingly (see
`ObjectListPage/listIncludes.ts`). Resolving a forward lookup's display value also needs the **target
collection to have a display field** (`collection.display_field_id`), else the included row has no
label to show.

**Read-side field-level security** — `CerbosFieldSecurityAdvice`
(`@ControllerAdvice`, order 10, after record-level authz) strips fields the
caller cannot `read` from outgoing JSON:API responses. For the primary `data`
and every `included[]` resource (grouped by type — JSON:API flattens all
include depths into one array, so one batched Cerbos check per type covers
them), it removes denied keys from **both** `attributes` and **to-one
`relationships`** (lookup / master-detail fields are serialized into
`relationships.<field>.data = { type, id }`, so an attributes-only strip would
leak a hidden FK's id). Has-many inverse relationships (`data` is a list) are
not collection fields and are preserved. System audit fields (`createdAt`,
`updatedAt`, `createdBy`, `updatedBy`) are never stripped; `meta` (pagination)
is untouched. Metadata/admin paths (`/api/collections`, `/api/admin/**`, etc.)
are skipped. Gated by `kelta.gateway.security.permissions-enabled`.

**Data masking** — layered on the same advice, strictly **after** stripping (a
HIDDEN field is never masked into existence). A field opts in via
`fieldTypeConfig.masking` (`{type: FULL|LAST4|EMAIL|CUSTOM, maskChar?,
customPattern?}`, string-typed `FieldType`s only — `FieldMaskingService`);
per-profile exposure is the 4th `profile_field_permission.visibility` value
**`MASKED`**, which `CerbosPolicyGenerator` compiles to an `EFFECT_DENY` on the
new **`unmask`** action *and* `write` (so `CerbosFieldWriteSecurityAdvice`
strips an echoed placeholder before it can overwrite the stored value — no new
write-path code). The advice resolves the masked set via one extra batched,
cached Cerbos check (`RecordMaskingService`) **only when the collection has
masking config** (zero cost elsewhere), replaces attribute values with their
redacted form, and stamps `meta.maskedFields` per record. The field-access
cache key includes the action (`tenant:profile:collection:action`) — a cached
`read` allow-list must never satisfy a `write`/`unmask` check.
`BootstrapRepository.SELECT_PROFILE_FIELD_PERMISSIONS` COALESCE-joins
field/collection to translate the UI's stored row UUIDs into the names the
advices check against Cerbos; `FieldPermissionSyncHook` (on
`profile-field-permissions`) triggers policy re-sync + multi-pod cache
eviction via the existing `kelta.cerbos.policies.changed.<tenantId>` subject.
Side channels: `MaskedFieldPredicateInterceptor` rejects list requests whose
`filter[...]`/`sort` reference a field masked for the requester with one
byte-identical 403 (`MASKED_FIELD_PREDICATE` — uniform so the error itself is
no oracle), and record-change events from collections with masking config are
flagged `containsMaskedFields`, which makes the gateway `RealtimeBridge` omit
record `data` from WebSocket fan-out (clients refetch through the masked
JSON:API path). **Non-advice egress** (PR 2) calls `RecordMaskingService.maskRows`
at its own boundary keyed to the requesting user: `DataExportService` (CSV/JSON —
the requester is threaded into the `@Async` job via `ExportPrincipal`) and
`ReportExecutionService` (execute + CSV/PDF — requester resolved in
`ReportExecutionController` via `CerbosPermissionResolver` into a
`MaskingPrincipal`); reports additionally **reject** a filter/sort/group-by on a
field masked for the executor (each leaks plaintext via predicate/ordering/group
key). A `null`/system principal (scheduled report delivery, background export) is
a documented **system-trust tier** that skips masking — same contract as flows
and other non-request contexts. The **search index** excludes masking-configured
fields at index time (field-config level, not per-viewer — the shared
`search_content` tsvector + `display_value` have no requester identity;
`SearchIndexService`), and `EmbeddingOnWriteHook` skips embedding a
masking-configured source field (an embedded plaintext would be semantically
searchable); `FieldConfigEventPublisher` rebuilds the collection index when a
field's masking config toggles. Still deferred to v2: **dashboards** (shared
Superset widget cache is not per-viewer) and the system-tier contexts above —
see `concerns.md`.

**Field history** — value changes to fields with `trackHistory=true` are captured
by `FieldHistoryHook` (worker `listener/`, wildcard `BeforeSaveHook`, order 900):
`afterCreate`/`afterUpdate` diff old vs new, `beforeDelete` re-reads the record for
final values, all written to `field_history` via `FieldHistoryRepository`. Reads go
through the `field-history` read-only **system collection** (`/api/field-history`,
gateway static route) — not a bespoke controller. Because a history row's
`oldValue`/`newValue` carry the *referenced* collection field's value, the generic
`CerbosFieldSecurityAdvice` (which keys off the row's own `field-history` type) can't
protect them; `FieldHistorySecurityAdvice` (`@Order(20)`, runs after it) resolves each
row's referenced collection+field and **drops FLS-denied rows / redacts MASKED old+new
values** per requester, fail-closed on an unresolvable collection. The end-user record
`ActivityTimeline` (`/app/o`) folds these rows into its feed.

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
| MetadataDependencyService | Builds a per-tenant metadata dependency graph on demand (collections/fields/flows/layouts/rules/record-types/list-views/constraints/perms) for impact analysis + cycle detection; not persisted, so always current. Endpoints: `GET /api/metadata/impact`, `GET /api/metadata/graph`, `POST /api/metadata/deployment-plan`. The deployment plan topologically orders a change set (`MetadataDependencyGraph.topologicalOrder`, Kahn — a node's dependencies precede it) so it can be applied safely; nodes in a cycle are flagged, not ordered. Edge direction: from dependent → dependency | `kelta-worker/.../service/MetadataDependencyService.java`, `.../dependency/MetadataDependencyGraph.java` |
| EmbeddingService | SPI turning text → a fixed-dimension normalized vector for semantic search over `VECTOR` fields. Default `HashingEmbeddingService` (feature-hashing, dependency-free, deterministic) is registered `@ConditionalOnMissingBean` so a deployment can plug in an external/local embedding model. `toVectorLiteral` renders a pgvector `[...]` literal for `<=>` queries | `runtime-core/.../embedding/EmbeddingService.java`, `.../embedding/HashingEmbeddingService.java` |
| ConfigHealthAnalyzer | Scans a tenant's config for anti-patterns via pluggable `HealthRule` beans (circular master-detail — reuses the dependency graph; collections without layouts/fields; orphan fields; flows without error handling; over-permissive profiles) and returns a 0–100 score + findings. On-demand, read-only. Endpoint: `GET /api/config-health` | `kelta-worker/.../health/ConfigHealthAnalyzer.java`, `.../health/rule/*` |
| PageRenderService | Resolves a published custom page to a **versioned render contract** (`PageRenderContract{version,slug,title,path,variables,dataSources,tree}` — `version "2.0"`, slice 1g) for the end-user shell. Looks up the `ui-pages` record by `slug` that is `published`+`active` in the tenant (via `QueryEngine`; RLS-scoped) and **projects its `config` JSON** as a **pure pass-through**: `tree` is the whole `config` map verbatim (FE reads `tree.components`), and the page-level `variables`/`dataSources` are surfaced as sibling fields (empty for legacy pages). **It resolves no `$bind` bindings and executes no `dataSources` server-side** — that would fetch records outside the JSON:API path and bypass Cerbos/FLS; the client resolves them over the authorized API. Draft/unknown slugs → 404. Endpoint: `GET /api/pages/{slug}/render` (gateway static route `/api/pages/**`). **Authz:** published+active+tenant is the base visibility gate. **Per-page restriction (slice 1h):** a page may set `config.access.requiredPermission` — `render(slug, profileId)` then checks that system permission via `BootstrapRepository.findProfileSystemPermissions(profileId)` (the **same in-controller `profile_system_permission` DB lookup `/api/admin/**` controllers use — NOT a Cerbos PDP call**); `profileId` is resolved by `CerbosPermissionResolver.getProfileId` from the gateway-forwarded `X-User-Profile-Id`. A denial returns `Optional.empty()` → **404, not 403** (deliberate: routed through the same branch as an unknown slug so a restricted page's existence is not leaked). Absent key ⇒ no permission lookup, behavior unchanged | `kelta-worker/.../service/PageRenderService.java`, `.../controller/PageRenderController.java` |
| ChangesService (offline sync, Rec 2B) | Offline-sync changes feed: `GET /api/{collection}/_changes?since=<ISO>` returns record **deletions** since the cursor + a fresh cursor. Deletions come from `record_tombstone` (V143), written by the wildcard `RecordTombstoneHook` (after-delete, user collections only) — so the hot delete path keeps hard-delete semantics (no soft-delete). **Upserts** are fetched by the client via the normal `GET /api/{collection}?filter[updatedAt][gt]=<cursor>&sort=updatedAt` (reuses the authorized, FLS-enforced filter path). `_changes` is a literal sub-path under the collection's existing route — no new gateway route | `kelta-worker/.../service/ChangesService.java`, `.../controller/ChangesController.java`, `.../listener/RecordTombstoneHook.java` |

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

**Governed agent runtime authz + audit.** `AgentRuntimeService` (kelta-ai) runs an
agent as a bounded tool-use loop. Authorization is layered: the agent definition
fixes the *allowed tool subset* (the loop offers only those and refuses any other
even if requested); each tool then calls the worker carrying the **invoking user's**
`X-User-Id` (runs require it), so the worker's per-record Cerbos + FLS advice applies
downstream — the agent never exceeds its caller's permissions. Every run, including
refusals (disabled agent, exhausted monthly quota), is written to `ai_agent_execution`
with its tool-call trace, token usage and status, surfaced at
`GET /api/ai/agents/{id}/executions`. Tool results are passed through
`PiiMaskingService` (email / SSN / 16-digit card / NANP phone → `[REDACTED_*]`)
before they reach the model or the audit trail — platform data the agent pulls is
masked; the caller's own prompt is not.

**Embed-on-write.** A VECTOR field may also carry `fieldTypeConfig` key
`"embeddingSource"` naming a text field on the same collection. The wildcard
`EmbeddingOnWriteHook` (worker, before-save, order 120) then auto-populates the
vector on create/update: it embeds the source text with the configured
`EmbeddingService` and writes the pgvector literal. It is a no-op unless the
source field is present in the payload (so partial updates that don't touch the
source keep the existing vector), respects a caller-supplied vector (no
overwrite), and skips with a warning if the field's `dimension` differs from the
provider's `dimensions()`. Without `embeddingSource`, vectors must be written
explicitly by the caller.

**Formula compute on write.** Read-side, FORMULA fields are computed on every
`get` by `DefaultQueryEngine.computeVirtualFields`. Write-side, the wildcard
`FormulaComputeHook` (worker, before-save, order 250) also evaluates every
FORMULA field on create/update so the computed value is visible to downstream
hooks and the response payload without an extra read. The hook merges the
record with the previous state (so partial updates see prior values), parses
each formula's expression into a `FormulaAst`, and topologically sorts the
FORMULA fields by inter-formula `FieldRef`s — Kahn's algorithm with
alphabetical tie-breaking so sibling fields resolve deterministically. Fields
that participate in a cycle (including self-references) are short-circuited:
they get `"#ERROR"` when their `fieldTypeConfig.returnType` is `TEXT`, `null`
for `NUMBER` / `BOOLEAN`. The same fallback rule applies to runtime evaluation
failures (parse error, missing function, division by zero), matching the
read-path behaviour. Formula values are *not* persisted — storage adapters
skip fields whose `FieldType.hasPhysicalColumn()` returns false — they only
ride along in the in-flight record map.

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

`EmailService.sendById(tenantId, to, templateId, vars, source, sourceId)` is the
exception: it resolves via `findTemplateByTenantAndId` (**tenant-scoped, no system
fallback**) so a caller selecting a template by id can only send that tenant's own
templates — the send endpoint below relies on this.

### Transactional send endpoint — `POST /api/email/send`

`EmailSendController` (gateway static route `static-email` → `/api/email/**`) is the
FE-reachable transactional-send path used by the `send_email` UI Quick Action. It is a
deliberate spam-surface lockdown: **no client-supplied body** (subject/body always come
from a stored `email_template` selected by `templateId` or `templateKey`), **gated on the
`MANAGE_EMAIL_TEMPLATES` system permission** (in-controller `profile_system_permission`
check via `BootstrapRepository.findProfileSystemPermissions` — `/api/*` gets only the
gateway's blanket `API_ACCESS`), and **rate-limited per tenant** (`≤ MAX_SENDS_PER_WINDOW`
`email_log` rows in a rolling `RATE_LIMIT_WINDOW_MINUTES` window → `429`). The recipient
`to` is already resolved by the caller (the FE reads `SendEmailConfig.recipientField` off
the record it is showing); `mergeContext` feeds `${var}` substitution. Sends are tagged
`source = "QUICK_ACTION"` in `email_log` and audit-logged to `security.audit`.

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

Canonical detail: [`ci-cd.md`](ci-cd.md). Summary:

- **PR checks** (`.github/workflows/ci.yml`): change-detection → test-java (build runtime + test gateway, worker, auth, ai, mcp) + test-frontend + integration-tests (Testcontainers) + e2e (Playwright) → quality-gate.
- **Deploy** (`.github/workflows/build-and-publish-containers.yml`): test → build-and-push to `harbor.rzware.com/emf/emf-*` (gateway, worker, worker-migrate, auth, ui, ai, mcp) → deploy (kustomize → ArgoCD) → smoke-test → auto-rollback on failure → post-deploy e2e.
- Custom self-hosted K8s runner for CI.

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
