# Kelta

**The enterprise application platform that covers everything you need to build, automate, and scale.**

Kelta is a metadata-driven platform that lets you define your entire data model, business logic, security, and integrations at runtime — no redeployments, no migrations, no waiting on engineering. Build enterprise-grade applications in hours, not months.

Built for engineering leaders and developers at growing companies who need the power of platforms like Salesforce without the complexity, cost, or vendor lock-in.

---

## Platform Capabilities

### Dynamic Schema Engine | Complete

Define your data model entirely at runtime. Create collections (tables), add fields, define relationships, and set validation rules — all through the API or admin UI. The platform handles storage, indexing, and API generation automatically.

- **27 field types** covering every enterprise need: primitives (string, integer, double, boolean), date/time, structured data (JSON, arrays), picklists (single and multi-select), currency (with companion currency code column), percent, auto-number (sequential identifiers like `CASE-0001`), phone, email, URL, rich text, encrypted (AES-256-GCM at the application layer), external ID (unique indexed), geolocation (latitude/longitude), and relationship fields
- **Relationships**: LOOKUP (optional FK, ON DELETE SET NULL) and MASTER_DETAIL (required FK, ON DELETE CASCADE) with automatic foreign key management at the database level
- **Validation**: Field-level rules (min/max value, min/max length, regex pattern, nullable) plus collection-level custom validation rules using a formula expression language
- **Computed fields**: FORMULA (expression-based, no physical column) and ROLLUP_SUMMARY (aggregate subqueries across child records — COUNT, SUM, MIN, MAX, AVG)
- **Schema versioning**: Immutable snapshots of collection and field definitions tracked across changes
- **Auto-number sequences**: Configurable prefix and padding backed by PostgreSQL sequences

---

### Auto-Generated REST API | Complete

Every collection automatically gets a fully compliant JSON:API endpoint. No boilerplate, no code generation — just define your schema and the API is live.

- **Full CRUD**: `GET` (list + detail), `POST`, `PATCH`, `DELETE` for every collection at `/{tenant}/{collection}`
- **Relationship includes**: `?include=related1,related2` with multi-level transitive resolution and Redis caching
- **Sparse fieldsets**: `?fields[type]=field1,field2` to reduce payload size
- **Filtering**: `?filter[field][operator]=value` with operators for equality, comparison, and pattern matching
- **Sorting**: `?sort=field,-field` for ascending/descending
- **Pagination**: `?page[number]=1&page[size]=20`
- **Sub-resources**: `/{parent}/{parentId}/{child}` for parent-child traversal
- **Dynamic route registration**: Collection schema changes publish Kafka events that update gateway routes in real-time — no restart required

---

### Global Full-Text Search | Complete

Search across all your data with a single query. PostgreSQL `tsvector`-backed full-text search with automatic indexing.

- Search endpoint: `GET /api/_search?q={query}` with prefix matching and relevance ranking via `ts_rank`
- Per-field `searchable` flag controls which fields are indexed
- Search index automatically maintained via Kafka record change events
- Tenant-isolated via PostgreSQL Row Level Security on the search index table
- Async bulk re-index capability when searchable fields change

---

### Authentication & Identity | Complete

Bring your own identity provider. Kelta supports any OIDC-compliant provider out of the box, with Keycloak as the default.

- **Multi-provider support**: Configure multiple OIDC providers per tenant with independent claim mapping
- **Dynamic JWKS resolution**: The gateway resolves the correct JWKS URI by JWT issuer claim at runtime — cached in Redis with 15-minute TTL, fallback to OIDC discovery
- **Claim mapping**: Configurable mappings for roles, email, username, and name claims per provider
- **Login tracking**: Every authentication attempt recorded with timestamps and IP addresses
- **Service accounts**: Internal gateway-to-worker communication uses dedicated OAuth2 client credentials

---

### Role-Based Access Control | Complete

A multi-layer permission model that gives you fine-grained control over who can see and do what. Enforcement powered by the Cerbos Policy Engine (see below).

- **Profiles** (one per user): Named permission bundles with system permissions (15 types including `VIEW_SETUP`, `MANAGE_USERS`, `API_ACCESS`, `VIEW_ALL_DATA`, `MODIFY_ALL_DATA`), object permissions (`canCreate`, `canRead`, `canEdit`, `canDelete`, `canViewAll`, `canModifyAll` per collection), and field permissions (`VISIBLE`, `READ_ONLY`, `HIDDEN` per field per collection)
- **Permission Sets** (additive, many per user): Same permission types as profiles, assignable directly to users or inherited through groups
- **Groups**: Users belong to multiple groups, groups can be nested, and groups can carry permission sets that cascade to all members
- **Resolution**: Most-permissive-wins (OR logic) across all applicable permission sets and profiles
- **Custom rules**: Per-profile ABAC rules with CEL expressions for fine-grained conditional access control
- **7 built-in profiles**: System Administrator, Standard User, Read Only, Marketing User, Contract Manager, Solution Manager, Minimum Access

> **TODO**
> - Field-level security (HIDDEN fields) is resolved but not yet stripped from API responses
> - Permission enforcement defaults to disabled (`permissions-enabled=false`) — needs production-ready default

---

### Cerbos Policy Engine | Complete

Fine-grained authorization powered by Cerbos PDP, providing ABAC policy evaluation at every layer of the platform.

- **Automatic policy generation**: `CerbosPolicyGenerator` produces per-tenant Cerbos policies — derived roles and resource policies for system features, collections, fields, and records — all generated from the RBAC model above
- **Policy sync**: `CerbosPolicySyncService` pushes policies to the Cerbos Admin API whenever profiles or permissions change, with bootstrap seeding on startup via `CerbosPolicySeeder`
- **Gateway enforcement**: `RouteAuthorizationFilter` calls Cerbos for every authenticated request to enforce object-level CRUD permissions at the API gateway
- **Worker enforcement**: `CerbosRecordAuthorizationAdvice` and `CerbosFieldSecurityAdvice` interceptors enforce record-level and field-level security at the service layer
- **Circuit breaker**: Fail-closed design — after 3 consecutive Cerbos failures, the circuit opens for 10 seconds (all checks denied). 2-second timeout per gRPC call. Auto-recovers when Cerbos becomes available
- **Custom ABAC rules**: `CustomRuleController` at `/api/admin/profiles/{profileId}/custom-rules` provides full CRUD for custom rules per profile, converted to CEL conditions in Cerbos policies
- **Authorization testing**: `AuthorizationTestController` at `/api/admin/authorization/test` for debugging permission decisions, with `PolicyTestPanel` in the admin UI

---

### Data Security | Complete

Enterprise-grade data protection at every layer.

- **Field-level encryption**: AES-256-GCM encryption at the application layer for `ENCRYPTED` field types. Per-tenant key derivation via HMAC-SHA256 over a master key. Transparent to API consumers.
- **Row Level Security**: PostgreSQL RLS policies on all system tables, enforced via `app.current_tenant_id` session variable set per request
- **Schema isolation**: Tenant-specific data tables live in dedicated PostgreSQL schemas
- **Audit trails**: Setup changes, security events, and login history all recorded with full before/after tracking and shipped to OpenSearch for searchable retention

---

### Multi-Tenancy & Governor Limits | Complete

Built for multi-tenant SaaS from the ground up. Every request is scoped to a tenant, every resource is quota-controlled.

- **URL-based resolution**: `/{tenantSlug}/api/...` — the gateway extracts the slug, resolves to a tenant ID, and injects `X-Tenant-ID` headers
- **Slug-to-ID caching**: Caffeine in-process cache refreshed from the worker's tenant map endpoint
- **Per-tenant governor limits** with real-time usage tracking:

| Resource | Default Limit |
|----------|--------------|
| API calls per day | 100,000 |
| Storage | 10 GB |
| Users | 100 |
| Collections | 200 |
| Fields per collection | 500 |
| Workflows | 50 |
| Reports | 200 |

- Daily API call counts tracked in Redis with 48-hour TTL
- Governor limits dashboard with live usage indicators in the admin UI
- Limits configurable per tenant via API

---

### Visual Flow Builder | Complete (with TODOs)

A full state-machine automation engine inspired by AWS Step Functions, with a visual drag-and-drop designer.

**8 State Types:**

| Type | Purpose |
|------|---------|
| Task | Execute an action handler with retry policies and error catching |
| Choice | Conditional branching with operators (StringEquals, NumericGreaterThan, BooleanEquals, IsPresent, And, Or, Not) |
| Parallel | Run branches concurrently and merge results |
| Map | Iterate over arrays with configurable `maxConcurrency` |
| Wait | Pause for duration, until timestamp, or until external event |
| Pass | Data transformation and injection |
| Succeed | Terminal success |
| Fail | Terminal failure with error code and cause |

**Data Flow**: Full JSONPath support — every state transition processes `InputPath` (select) -> Execute -> `ResultPath` (place) -> `OutputPath` (forward).

**15 Built-In Action Handlers:**
- Data: Field Update, Create Record, Update Record, Delete Record, Query Records, Create Task, Log Message
- Communication: Email Alert, Send Notification
- Integration: HTTP Callout, Outbound Message, Invoke Script, Publish Event, Trigger Flow
- Flow Control: Decision (conditional branching), Delay (deferred execution)

**Execution Model:**
- Asynchronous by default with immediate `executionId` return
- Synchronous mode for before-save flows that modify records pre-commit
- Durable execution: every state transition persisted to DB, survives pod restarts
- Configurable thread pool and flow-level timeout (default 3600s)
- Retry with exponential backoff, catch-and-redirect error handling
- Flow versioning with publish lifecycle and version history
- Cancel, retry, and execution detail inspection via API

**Visual Designer:**
- React Flow-based canvas with drag-and-drop node composition
- Steps palette with all state types grouped by category (Task, Choice, Control, Terminal)
- Properties panel for every state and action type (30 property editors)
- Trigger configuration (inline editing)
- Test execution dialog with custom input
- Execution history tab with visual step timeline and detail drilldown
- Debug tab for runtime inspection

**Triggers:**

| Trigger | Status |
|---------|--------|
| Record-Triggered (on create/update/delete with filter formulas) | Working |
| API / Webhook (`POST /api/webhooks/{flowId}` with request body mapping, header capture) | Working |
| Scheduled (cron expression with timezone) | TODO |
| Kafka-Triggered (key/message filter with dynamic consumer) | TODO |

**Workflow Migration:**
- `WorkflowMigrationController` migrates legacy workflow rules to modern flow definitions — migrate individual rules or all at once via `/api/admin/migrate-workflow-rules`

> **TODO**
> - SCHEDULED trigger: No cron runner wired — flows with cron expressions are not dispatched
> - KAFKA_TRIGGERED: No dynamic Kafka consumer created per flow
> - Wait state resume: `resumeExecution()` is not implemented — long-duration Wait states persist as WAITING but never resume

---

### Rate Limiting | Complete

Dual-layer rate limiting protects the platform and individual tenants.

- **Per-tenant sliding window**: Redis INCR with fixed-window TTL per route, configurable limits, returns remaining count and retry-after headers
- **Governor limit daily quota**: Daily API call counter in Redis per tenant, enforced against the configured governor limit
- **IP-based rate limiting**: Additional rate limiting by client IP
- **Graceful degradation**: If Redis is unreachable, requests are allowed through to prevent outages

---

### Observability & Monitoring | Complete

A purpose-built observability stack with 7 fully implemented monitoring pages in the admin UI.

**Distributed Tracing:**
- OpenTelemetry agent -> Jaeger collector -> OpenSearch pipeline
- Request/response body capture with sensitive field sanitization (password, token, etc.)
- Trace correlation across gateway and worker spans
- Request log search with filters for method, status, path, traceId, userId, and time range

**Log Aggregation:**
- Custom Logback appender (`OpenSearchLogAppender`) ships logs to OpenSearch via bulk NDJSON
- Batched writes with configurable batch size and flush interval
- MDC enrichment with traceId, spanId, tenantId, userId
- Log search with level, service, and query text filtering

**Metrics Dashboard:**
- Request count over time (histogram charts)
- Error rate (4xx/5xx percentage)
- Latency percentiles: P50, P75, P95, P99
- Top endpoints by request count with latency breakdown
- Top error paths with status code analysis
- Auth failures and rate-limited request counts
- 24-hour summary cards

**Audit:**
- Setup audit trail (who changed what configuration and when, with before/after values) via `OpenSearchAuditService`
- Security audit log (permission changes, access violations)
- Login history (all authentication attempts with timestamps and IP)
- User activity page with per-user request and audit event analysis
- Audit data queryable via `OpenSearchQueryService`

**Prometheus Metrics:**
- `kelta_worker_request_total{collection, method, status}`
- `kelta_worker_request_duration_seconds{collection, method}`
- `kelta_worker_error_total{collection, error_type}`
- Active collection gauge for HPA autoscaling

**Observability Settings:**
- Configurable per-tenant retention periods (default: 30 days traces/logs, 90 days audit)
- Enforced via OpenSearch ISM policies
- Admin UI page for managing observability configuration

---

### Integration | Complete

Connect Kelta to your existing systems with HTTP callouts, webhooks, event streaming, embedded analytics, and outbound webhook delivery.

**Working:**
- **HTTP Callout**: Generic HTTP requests from flows with header, method, and body templating via merge fields
- **Outbound Message**: Structured webhook payloads (XML/JSON) to external systems
- **Kafka Event Publishing**: Publish custom events to arbitrary Kafka topics from any flow
- **Inbound Webhooks**: Flows with AUTOLAUNCHED type get dedicated `POST /api/webhooks/{flowId}` endpoints with request body mapping and header capture (content-type, user-agent)
- **Outbound Webhooks (Svix)**: Outbound webhook delivery via Svix with event type mapping (`collection.created`, `collection.updated`, etc.), multi-tenant isolation, and management portal UI. `SvixWebhookPublisher` bridges Kafka config events to Svix. `SvixTenantLifecycleHook` initializes a Svix application per tenant on creation.
- **Embedded Analytics (Apache Superset)**: Embedded dashboards with guest token authentication, automatic dataset sync from collections, and tenant-isolated Superset management. `SupersetTenantLifecycleHook` initializes Superset resources on tenant creation. See Embedded Analytics section below.
- **Merge Field Templating**: DataPath system for dot-notation traversal across relationship boundaries

> **TODO**
> - Email delivery: `EmailService` SPI exists but only has a no-op logging implementation — no SMTP, SendGrid, or other email sender is wired
> - Script execution: `ScriptExecutor` SPI exists but only has a no-op logging implementation — no JavaScript engine (GraalVM, Nashorn, etc.)
> - Connected Apps: OAuth client registration data model exists but no OAuth 2.0 authorization server endpoints (`/oauth/token`, `/oauth/authorize`)

---

### Embedded Analytics | Complete

Embed interactive dashboards and reports directly into the platform using Apache Superset.

- **Guest token generation**: `SupersetController` at `/api/superset/guest-token` generates scoped guest tokens for embedding dashboards with tenant-level row-level security
- **Dashboard discovery**: `GET /api/superset/dashboards` lists available dashboards for the current tenant
- **Dataset sync**: `POST /api/superset/datasets/sync` synchronizes collection schemas to Superset datasets. `SupersetCollectionSyncListener` triggers sync automatically when collections change via Kafka events
- **Tenant isolation**: `SupersetTenantService` manages per-tenant Superset resources. `SupersetTenantLifecycleHook` provisions Superset access on tenant creation
- **Frontend embedding**: `SupersetEmbed` component renders dashboards inline. `AnalyticsPage` and `DashboardPage` provide dedicated analytics views in the admin UI

---

### Extensibility & Modules | Partial

Extend the platform with custom action handlers, before-save hooks, and frontend components.

**Working:**
- **Module SPI**: `KeltaModule` interface with `getActionHandlers()`, `getBeforeSaveHooks()`, `onStartup(ModuleContext)` lifecycle
- **3 built-in compile-time modules**: Core Actions (8 handlers), Integration (7 handlers), Schema Lifecycle (hooks for tenant, collection, field, user, and profile lifecycle)
- **Module lifecycle management**: Install, enable, disable, uninstall per tenant with status tracking and Kafka event propagation across pods
- **ModuleContext**: Provides QueryEngine, CollectionRegistry, FormulaEvaluator, and extensible service map to modules
- **Frontend Plugin SDK** (`@kelta/plugin-sdk`): `BasePlugin` abstract class with `init`/`mount`/`unmount` lifecycle, `ComponentRegistry` for custom field renderers and page components

> **TODO**
> - Runtime JAR loading: Module lifecycle works but action handler execution returns placeholder responses — real ClassLoader-based JAR loading deferred until S3 storage and sandboxed ClassLoaders are implemented
> - Plugin SDK host wiring: The SDK package exists and is well-defined but the admin UI does not yet query the `ComponentRegistry` to discover and render custom components

---

### Record Types | Planned

Support for collection subtypes with type-specific picklist values and page layouts.

**Exists:**
- Data model with `record_type` and `record_type_picklist_value` tables
- Full CRUD via standard collection API
- Record Type editor in the Collection Detail page

> **TODO**
> - No runtime enforcement: Record type is not checked during record create/update to restrict picklist values or apply type-specific field defaults
> - No page layout association: Record type does not drive which page layout is rendered

---

### Reports & Dashboards | Planned (UI Complete)

**The full UI is built** — a multi-step report builder (Source -> Columns -> Filters -> Group By -> Sort) and a dashboard editor with metric cards, bar charts, data tables, and recent records widgets.

> **TODO**
> - No backend query execution engine: Report configurations are stored but nothing reads the `columns`/`filters`/`groupings` JSON to produce actual query results
> - No chart data endpoint: Dashboard widgets cannot fetch live aggregated data
> - No report export processor (CSV/PDF)

---

### Approval Processes | Planned (UI Complete)

**The full UI is built** — approval process definitions with entry criteria, step configuration, assignee rules, and field updates on submit/approve/reject/recall.

> **TODO**
> - No submit-for-approval workflow logic: No endpoint to submit a record for approval
> - No approve/reject action endpoint
> - No record locking during active approval
> - No `SUBMIT_FOR_APPROVAL` action handler for flows

---

### Scheduled Jobs | Planned (UI Complete)

**The full UI is built** — scheduled job management with cron expression configuration, timezone selection, execution history, and run-now capability.

> **TODO**
> - No cron runner: Nothing reads `cron_expression` from the `scheduled_jobs` table to dispatch jobs
> - No job type dispatchers for FLOW, SCRIPT, or REPORT_EXPORT job types

---

### Bulk Data Operations | Planned (UI Complete)

**The full UI is built** — bulk job definitions for INSERT/UPDATE/UPSERT/DELETE operations with batch size configuration and progress tracking.

> **TODO**
> - No backend processor: No worker thread processes batch imports or exports
> - No file upload/download for bulk data payloads

---

### Notes & Attachments | Partial

Attach notes and files to any record in the system.

**Working:**
- Notes: Full CRUD via standard collection API
- Attachments: S3 presigned download URLs generated via `S3Presigner`, automatically enriched in JSON:API responses via `@ControllerAdvice`
- Relationship includes: `?include=attachments` and `?include=notes` supported

> **TODO**
> - No upload endpoint: `S3StorageService` only generates presigned download URLs — no presigned PUT URL or multipart upload handler

---

### Configuration Packages | Partial (UI Complete)

**The full UI is built** — export wizard with multi-select for collections, roles, policies, and UI config; import with dry-run preview and conflict resolution; package history.

> **TODO**
> - All backend endpoints missing: No `PackageController` or `PackageService` — the UI calls `/api/packages/export`, `/api/packages/import/preview`, `/api/packages/import`, and `/api/packages/history` but none exist
> - Package item types defined: COLLECTION, FIELD, ROLE, POLICY, ROUTE_POLICY, FIELD_POLICY, OIDC_PROVIDER, UI_PAGE, UI_MENU, UI_MENU_ITEM

---

### Page Builder | Partial

A visual drag-and-drop page editor for building custom pages.

**Working:**
- Page editor with canvas, component palette (Text, Image, Button, List View, Custom), property panel, and preview mode
- Runtime page renderer that resolves components from the `ComponentRegistry`
- CRUD for page definitions via standard collection API

> **TODO**
> - Missing `slug` field in system collection definition (UI queries by slug but field doesn't exist)
> - Missing `published` flag in system collection definition
> - No server-side component resolution

---

### Developer Experience | Complete

Everything developers need to build on top of Kelta.

- **TypeScript SDK** (`@kelta/sdk`): `KeltaClient` with auto-discovery, token management (refresh + validation), and configurable retry with exponential backoff
- **Fluent Query Builder**: `QueryBuilder<T>` for filtering, pagination, sorting, field selection, and includes
- **Admin Client**: Typed access to collections, fields, roles, policies, and webhooks
- **Error taxonomy**: `ValidationError`, `AuthenticationError`, `AuthorizationError`, `NotFoundError`, `ServerError`, `NetworkError`
- **OpenAPI type generation**: `generateTypesFromUrl()` and `generateTypesFromSpec()`
- **Zod schemas** for all API response types
- **65+ admin and end-user UI pages** — all fully implemented with real data fetching, forms with validation, and loading/error/empty states
- **Page layout system**: Drag-and-drop layout editor with field palette, property panel, conditional visibility rules, related list configuration, and mobile preview
- **End-user application shell**: Dedicated `EndUserShell` with top navigation, global search, and purpose-built pages for object browsing (list, detail, form), custom pages, and an app home dashboard
- **Internationalization (i18n)**: `I18nContext` with `useTranslation` hook supporting multiple languages
- **Theme system**: Dark/light mode toggle via `ThemeContext`, persisted in user preferences
- **Accessibility**: Skip links, ARIA live regions, table keyboard navigation, global keyboard shortcuts with `KeyboardShortcutsHelp` dialog
- **OpenTelemetry instrumentation**: Frontend telemetry via OpenTelemetry SDK for end-to-end tracing

---

### Operational Excellence | Complete

Production-ready infrastructure patterns baked into the platform.

- **Graceful shutdown**: In-flight flow executions persisted to DB on SIGTERM for resume after restart
- **Optimistic locking**: Scheduled tasks use `last_scheduled_run` column to prevent duplicate execution across pods
- **Multi-layer caching**: Caffeine (in-process) -> Redis -> worker backend, with Kafka-driven cache invalidation
- **Dual-layer rate limiting**: Per-tenant fixed-window (Redis) + governor limit daily quota
- **Dynamic route management**: Gateway routes updated in real-time via Kafka events — no restart on schema changes
- **Security headers**: `SecurityHeadersFilter` adds standard security headers (X-Content-Type-Options, X-Frame-Options, etc.) to all responses
- **Health endpoints**: `/actuator/health` with Redis and Kafka connectivity checks, worker reachability monitoring from gateway
- **Prometheus metrics**: Request counts, latency histograms, error counters, and active collection gauges for HPA autoscaling
- **Kubernetes-native**: Deployed via ArgoCD with standard deployment manifests

---

## Architecture

| Component | Technology | Role |
|-----------|-----------|------|
| Gateway | Java 21, Spring Cloud Gateway (reactive) | API ingress: JWT auth, tenant resolution, rate limiting, Cerbos authorization, JSON:API processing |
| Worker | Java 21, Spring Boot 3.2 | Business logic engine: CRUD, workflows, flows, schema management, integrations |
| Admin UI | React 19, Vite, TypeScript | Full-featured admin console and end-user app runtime |
| SDK | TypeScript | Client library with typed access to all APIs |
| Database | PostgreSQL 15 | Primary data store (99 Flyway migrations) |
| Cache | Redis 7 | Rate limiting, caching (routes, permissions, JSON:API responses) |
| Messaging | Kafka 3.7 (KRaft) | Event streaming (record changes, config changes, module events) |
| Identity | Keycloak 23 (or any OIDC provider) | Authentication and identity management |
| Authorization | Cerbos PDP | Fine-grained ABAC policy evaluation with per-tenant resource policies |
| Observability | OpenSearch, Jaeger, OpenTelemetry | Traces, logs, audit events, metrics |
| Analytics | Apache Superset | Embedded dashboards with guest token auth and automatic dataset sync |
| Webhooks | Svix | Outbound webhook delivery with event type mapping and tenant isolation |

---

## Enterprise Readiness

What SMB and larger enterprises need from an application platform, and where Kelta stands.

### Delivered

| Capability | How Kelta Delivers It |
|------------|----------------------|
| **Multi-Tenancy** | URL-based tenant routing, PostgreSQL schema isolation, Row Level Security on every table |
| **Fine-Grained Access Control** | RBAC profiles + permission sets + groups, enforced by Cerbos PDP at gateway and service layers |
| **Audit & Compliance** | Setup audit trails, security audit logs, login history — all shipped to OpenSearch with configurable retention |
| **SSO / Identity Federation** | Multi-provider OIDC support with per-provider claim mapping; Keycloak default, any compliant provider supported |
| **API-First Architecture** | Every collection auto-generates a JSON:API endpoint with filtering, sorting, pagination, includes, and sparse fieldsets |
| **Workflow Automation** | Visual Flow Builder with 15 action handlers, 8 state types, retry/catch error handling, and durable execution |
| **Data Encryption** | AES-256-GCM field-level encryption with per-tenant key derivation; PostgreSQL RLS for row-level isolation |
| **Rate Limiting & Quotas** | Dual-layer rate limiting (per-route + daily governor quota) with configurable per-tenant limits |
| **Embedded Analytics** | Apache Superset integration with guest tokens, automatic dataset sync, and tenant-isolated dashboards |
| **Webhook Integrations** | Inbound webhooks (flow triggers) and outbound webhooks (Svix) with event type mapping and HMAC verification |
| **Full-Text Search** | PostgreSQL tsvector-backed search with per-field indexing control, Kafka-driven index maintenance |
| **Observability** | OpenTelemetry tracing, OpenSearch log aggregation, Prometheus metrics, 7 monitoring pages in admin UI |
| **Extensibility** | Module SPI for backend extensions, Plugin SDK for frontend customization, Kafka event streaming for integration |
| **Internationalization** | Multi-language support with translation framework |
| **Accessibility** | WCAG patterns: skip links, ARIA live regions, keyboard navigation, screen reader support |
| **Dynamic Schema** | Runtime data model changes with no deployments — create collections, fields, relationships, and validation rules on the fly |
| **Low-Code Builder Tools** | Visual flow designer, page layout editor, page builder, collection wizard, field editor |
| **Governor Limits** | Per-tenant resource quotas (API calls, storage, users, collections, fields) with real-time usage tracking |

### In Progress

| Capability | Current State | What Remains |
|------------|--------------|-------------|
| **Email / Notifications** | SPI defined, no-op implementation | Wire SMTP, SendGrid, or SES provider |
| **Approval Workflows** | Full UI built | Backend submit/approve/reject logic, record locking |
| **Scheduled Automation** | Full UI built, cron config stored | Cron dispatcher to trigger flows and jobs on schedule |
| **Bulk Data Operations** | Full UI built | Backend batch processor, file upload/download |
| **Report Execution** | Full UI built, config stored | Query engine to execute report definitions, export to CSV/PDF |
| **OAuth Server (Connected Apps)** | Data model exists | Authorization server endpoints (`/oauth/token`, `/oauth/authorize`) |
| **Server-Side Scripting** | SPI defined, no-op implementation | GraalVM or equivalent script engine |
| **File Uploads** | S3 download URLs work | Presigned PUT URL generation, upload endpoint |
| **Configuration Packages** | Full UI built | Backend export/import/preview/history endpoints |
| **Record Type Enforcement** | Data model and UI exist | Runtime picklist restriction and layout association |

### Enterprise Gaps to Address

| Capability | Why Enterprises Need It |
|------------|------------------------|
| **SCIM Provisioning** | Automated user/group sync from identity providers — eliminates manual user management |
| **High Availability Documentation** | Documented HA architecture, failover procedures, and RTO/RPO guarantees for compliance reviews |
| **Data Export & Backup** | Scheduled data exports, point-in-time recovery documentation, and tenant data portability |
| **SLA & Uptime Commitments** | Formal SLA documentation with uptime targets, incident response procedures, and escalation paths |
| **IP Allowlisting** | Restrict API access to approved IP ranges per tenant for network-level security |
| **Sandbox Environments** | Isolated development/staging environments with metadata promotion between them |
| **Change Sets / CI-CD for Metadata** | Versioned metadata deployments between environments (partially addressed by Configuration Packages) |
| **Data Masking** | Mask sensitive fields in non-production environments for safe development and testing |
| **Delegated Administration** | Allow tenant admins to manage their own users, roles, and configuration without platform-level access |

---

## TODO Summary

### High Priority — Core Platform Gaps

- [ ] **Field-level security enforcement**: HIDDEN field permissions are resolved but not stripped from API responses
- [ ] **Flow SCHEDULED trigger**: Wire a cron runner that dispatches flows based on their cron expressions
- [ ] **Flow KAFKA_TRIGGERED trigger**: Wire dynamic Kafka consumers per flow with key/message filtering
- [ ] **Flow Wait state resume**: Implement `resumeExecution()` to resume flows paused in Wait states
- [ ] **Permission enforcement default**: Change `permissions-enabled` to default to `true` for production deployments

### Medium Priority — Integration & Data Gaps

- [ ] **Email delivery**: Implement a real `EmailService` (SMTP, SendGrid, or SES) to replace the no-op logging stub
- [ ] **Script execution**: Implement a real `ScriptExecutor` (GraalVM or similar) to replace the no-op logging stub
- [ ] **Attachment uploads**: Add presigned PUT URL generation to `S3StorageService` and expose an upload endpoint
- [ ] **Configuration packages backend**: Implement `PackageController` and `PackageService` with export, import preview, import, and history endpoints
- [ ] **Scheduled job runner**: Build a cron-based job dispatcher that reads `scheduled_jobs` and dispatches FLOW, SCRIPT, and REPORT_EXPORT jobs
- [ ] **Page Builder fields**: Add `slug` and `published` fields to the `ui-pages` system collection definition

### Lower Priority — Advanced Features

- [ ] **Report execution engine**: Build a query builder that reads report config (columns, filters, groupings) and produces query results
- [ ] **Dashboard data endpoints**: Serve live aggregated data to dashboard widget types
- [ ] **Approval workflow engine**: Implement submit/approve/reject logic with record locking and field updates
- [ ] **Bulk job processor**: Build a batch processor for INSERT/UPDATE/UPSERT/DELETE operations with file upload
- [ ] **Connected Apps OAuth server**: Implement `/oauth/token` and `/oauth/authorize` endpoints that validate against the `connected_apps` table
- [ ] **Runtime module JAR loading**: Implement ClassLoader-based JAR loading with S3 storage and sandboxing
- [ ] **Plugin SDK host wiring**: Wire `ComponentRegistry` into the admin UI to discover and render custom field renderers and page components
- [ ] **Record type enforcement**: Enforce picklist value restrictions and field defaults based on record type during create/update

### Enterprise Readiness

- [ ] **SCIM provisioning**: Implement SCIM 2.0 endpoints for automated user/group sync from identity providers
- [ ] **HA documentation**: Document high availability architecture, failover, and RTO/RPO guarantees
- [ ] **Data export & backup**: Build scheduled data export and tenant data portability tooling
- [ ] **Sandbox environments**: Support isolated dev/staging environments with metadata promotion
