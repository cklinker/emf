Enterprise Micro service Framework

1. Overview

1.1 Product Name (working)

EMF — Enterprise Microservice Framework

1.2 Problem Statement

Teams want to ship enterprise-grade, multi-tenant-ready microservices quickly with strong conventions, automatic API documentation, runtime-configurable resources, and an admin UI that can define and evolve data-backed collections without service restarts.

1.3 Primary Goals
	•	Developer speed / experience first (convention over configuration).
	•	Opinionated defaults for security, persistence, messaging, observability, and testing.
	•	Runtime-configurable resources (collections) created/modified via UI → exposed as REST endpoints without restart.
	•	Extensible at every layer (storage, authz, routing, validation, events, UI components).
	•	Kubernetes-friendly and production-grade (metrics, logging, tracing, health checks).
	•	Open source core, with a path to hosted paid service (24/7 operations).

1.4 Non-Goals (initially)
	•	GraphQL support (possible future extension).
	•	Compound documents (explicitly not needed due to HTTP/2 and your direction).
	•	Full workflow engine (can be added as a domain service later).

⸻

2. Technology Stack

2.1 Backend (Java)
	•	Java 21+ (recommended)
	•	Spring Boot (Web, Security, Actuator)
	•	PostgreSQL (primary persistence)
	•	Kafka (event streaming)
	•	Redis (caching, distributed locks, ephemeral config acceleration)
	•	OpenAPI 3.2 auto-generation for all APIs

2.2 Frontend
	•	React + TypeScript
	•	UI is self-configuring: only needs server URL; fetches configuration via REST
	•	Shared libraries:
	•	TypeScript SDK wrapper (resource discovery, CRUD, filtering/sorting/pagination)
	•	Reusable React components (tables, forms, layouts, navigation)

2.3 Deployment
	•	Container-first
	•	Kubernetes helm charts / Kustomize templates
	•	Supports multi-service “domain microservices” architecture

⸻

3. System Architecture

3.1 High-Level Components
	1.	Gateway / API Edge (optional)
	•	Can be internal or external (NGINX / Envoy / API Gateway)
	•	Handles TLS, HTTP/2 termination, rate limiting (optional)
	2.	Core Runtime Service (“Control Plane” runtime APIs)
	•	Manages definitions:
	•	Collections (resources)
	•	Fields & validation rules
	•	Role-based policies
	•	UI page definitions, menus, navigation
	•	Provider configuration (OIDC issuers)
	•	Publishes configuration changes/events to Kafka
	3.	Domain Microservices (Data Plane services)
	•	Each domain service can:
	•	Host multiple runtime-defined collections
	•	Provide built-in CRUD routes for data-backed resources
	•	Host custom endpoints via plugin modules
	 *.     Host its own postgres database for collections
	•	Subscribes to config changes via Kafka and applies without restart
	4.	PostgreSQL
	•	Stores:
	•	Collection definitions
	•	UI configuration
	•	AuthZ policies
	•	Data for collections (either per-collection tables OR a hybrid approach)
	5.	Kafka
	•	Event bus:
	•	Collection changed
	•	Schema migration applied
	•	Security policy changed
	•	Data lifecycle events (resource created/updated/deleted)
	6.	Redis
	•	Cache compiled configs (routes, field policies)
	•	Optional distributed coordination:
	•	“migration lock”
	•	“config publish lock”
	•	Optional rate limiting token buckets
	7.	React Admin/Builder UI
	•	Defines and manages:
	•	Collections/resources
	•	Fields & rules
	•	API exposure and authz per route/field
	•	UI pages and menus
	•	Imports/exports/migrations
	•	Plugin/module management (where allowed)

⸻

4. Data Model & Runtime Resource Strategy

You need runtime create/modify resources with zero restart. The critical design choice is how data is stored.

4.1 Supported Storage Modes (extensible)

Mode A — Physical Tables (recommended default for enterprise performance)
	•	Each collection → real Postgres table.
	•	Add/change fields → online schema migrations.
	•	Pros: best query performance, easier BI/export, strong typing.
	•	Cons: schema migration complexity.

Mode B — JSONB Document Store
	•	Single table per service, store records as JSONB + indexed extracted columns.
	•	Pros: easiest runtime evolution.
	•	Cons: query complexity and performance tradeoffs.

Spec requirement: Provide opinionated default but extensible.
✅ Default: Mode A (Physical Tables) with a built-in Migration Engine.
✅ Option: Mode B via storage adapter interface.

4.2 Collection Definition Model (Control Plane)

A collection definition contains:
	•	collectionId (UUID)
	•	name (string, unique within service)
	•	version (int, incremented per change)
	•	storageMode (TABLE | JSONB | CUSTOM)
	•	primaryKey (default id UUID)
	•	timestamps (createdAt/updatedAt enabled by default)
	•	softDelete (optional)
	•	fields[]
	•	fieldName
	•	dataType (string, int, decimal, boolean, date, datetime, json, enum, reference, etc.)
	•	nullable
	•	min/max/length
	•	pattern (regex)
	•	defaultValue
	•	indexed (boolean)
	•	unique (boolean)
	•	immutable (boolean)
	•	references (collection + relationship type)
	•	apiConfig
	•	enabled operations (GET, LIST, POST, PUT, PATCH, DELETE)
	•	route base path: /api/{collectionName}
	•	field sets rules
	•	authzConfig
	•	route-level roles
	•	field-level roles (read/write)
	•	eventsConfig
	•	emit lifecycle events to Kafka
	•	auditConfig
	•	track changes, actor, requestId

4.3 Applying Changes Without Restart

Lifecycle
	1.	UI calls Control Plane to create/update a collection definition.
	2.	Control Plane:
	•	Validates request
	•	Persists definition in Postgres
	•	Generates a Migration Plan
	3.	Migration Engine executes online schema changes (with locking + safe rollout)
	4.	Control Plane publishes CollectionUpdated event to Kafka
	5.	Domain services subscribe and refresh in-memory routing/authz/config caches dynamically.

Safety Guarantees
	•	Changes are versioned; services apply only increasing versions.
	•	All migrations are idempotent and recorded in schema_migrations.
	•	Backward-compatible upgrades supported where possible; destructive changes require explicit flags.

⸻

5. API Standards

5.1 API Conventions (JSON:API-inspired)
	•	Content type: application/json
	•	Use a consistent envelope for list endpoints:
	•	data array
	•	meta for pagination
	•	links 
	•	Single resource:
	•	data object
     *.     links
	•	Errors:
	•	errors[] with code/message/details/traceId

No compound documents are required (explicit requirement).

5.2 Resource CRUD Endpoints (generated)

For a collection customers:
	•	GET /api/customers
	•	GET /api/customers/{id}
	•	POST /api/customers
	•	PUT /api/customers/{id}
	•	PATCH /api/customers/{id}
	•	DELETE /api/customers/{id}

5.3 Query Features Required on ALL GET List APIs

Pagination
	•	page[number], page[size]
	•	Response meta:
	•	meta.page.number, meta.page.size, meta.page.totalPages, meta.page.totalItems

Sorting
	•	sort=field1,-field2

Field Sets
	•	fields=fieldA,fieldB,fieldC

Filtering (any column)

Use filter[field][op]=value pattern:
	•	equals: filter[name][eq]=Alice
	•	not equal: filter[name][neq]=Alice
	•	greater than: filter[age][gt]=21
	•	less than: filter[age][lt]=65
	•	null: filter[deletedAt][isnull]=true
	•	contains: filter[email][contains]=@company.com
	•	starts with: filter[name][starts]=Al
	•	ends with: filter[name][ends]=son
	•	case insensitive variants: filter[name][icontains]=bob, istarts, iends, ieq

Rules:
	*	Default operation is equals if not specified
	•	Validate ops based on data type
	•	Index recommendation engine (warn in logs/metrics when unindexed filters cause slow queries)

⸻

6. OpenAPI 3.2 Generation

6.1 Requirements
	•	Every route (generated + custom) produces an OpenAPI 3.2 spec.
	•	Spec updates when collections/routes change at runtime.

6.2 Approach
	•	Maintain an internal canonical “API Model” derived from:
	•	Collection definitions
	•	Custom module route metadata
	•	Security model
	•	Expose:
	•	GET /openapi.json
	•	GET /openapi.yaml
	•	Hot reload spec on config events.
	•	Include schema definitions for runtime collections:
	•	Basic types + validation constraints
	•	Field-level read/write permissions in vendor extensions (e.g., x-authz-read-roles)

⸻

7. Security

7.1 Authentication (AuthN)
	•	JWT Bearer tokens issued by external OIDC provider (Auth0, Okta, Cognito, etc.)
	•	Configurable allowed issuers/audiences via UI + REST:
	•	issuer URL(s)
	•	JWKS endpoints
	•	audience
	•	required claims mappings (e.g., roles claim path)

Backend uses:
	•	Spring Security Resource Server (JWT validation)
	•	Cached JWKS keys

7.2 Authorization (AuthZ) — Role-Based with Field-Level Controls

Concepts
	•	Role: named permission group
	•	Policy: ties roles to:
	•	route operations (LIST/GET/POST/PUT/PATCH/DELETE)
	•	field read/write per resource
	•	UI menu/page access

Enforcement Points
	•	Route-level: block endpoint access
	•	Field-level:
	•	On reads: remove unauthorized fields (field masking)
	•	On writes: reject updates to unauthorized fields (validation error)

Default Roles
	•	ADMIN (full)
	•	DEVELOPER (manage collections, config)
	•	USER (data usage)
	•	(Extensible)

7.3 Multi-Tenancy (optional but recommended for hosted model)
	•	Tenant identifier from JWT claim or header
	•	Row-level isolation:
	•	tenant_id column per collection table (default)
	•	enforced in query builder automatically

⸻

8. Observability and Operations

8.1 Logging
	•	Structured JSON logs
	•	Correlation IDs:
	•	traceId, spanId, requestId
	•	Include actor identity (subject claim), tenantId, route, collection, version

8.2 Metrics
	•	Prometheus format via Spring Actuator + Micrometer
	•	Required metrics:
	•	request latency by route
	•	DB query latency
	•	filter/sort usage counts
	•	cache hit ratio (Redis + in-memory)
	•	migration durations
	•	authz denials count

8.3 Tracing
	•	OpenTelemetry instrumentation
	•	Export to OTLP (Jaeger/Tempo/etc.)

8.4 Health Checks
	•	Liveness: /actuator/health/liveness
	•	Readiness: /actuator/health/readiness
	•	DB connectivity
	•	Kafka connectivity
	•	Redis connectivity
	•	config sync status (must be on current version)

⸻

9. React UI Specification

9.1 UI Bootstrapping (self-configured)

UI requires only:
	•	SERVER_BASE_URL

On load:
	•	GET /ui/config/bootstrap
	•	branding
	•	enabled modules
	•	menus/pages available for user roles
	•	API discovery endpoint references

9.2 Core UI Modules
	1.	Collections Designer
	•	Create/update collections
	•	Add/remove fields
	•	Set validation (min/max/length/pattern/nullable/default)
	•	Define indexes/uniques
	•	Enable/disable operations
	•	Preview OpenAPI schema
	2.	Authorization Manager
	•	Create roles
	•	Assign route-level perms per collection
	•	Assign field-level read/write perms
	•	Assign UI page/menu access
	3.	UI Builder
	•	Define pages:
	•	table page
	•	detail page
	•	form page
	•	custom layout page (composition)
	•	Menu builder:
	•	top menu items
	•	left-side context menus based on top selection
	•	visibility rules by role
	•	Page routing:
	•	pageId, path, layout, widgets, bindings to resources
	4.	Import/Export
	•	Export collection definition + policies + pages as a package (JSON)
	•	Import into another environment with mapping/validation
	•	Show diff before apply
	5.	Schema/Data Migration Tools
	•	Promote config packages between dev/qa/stage/prod
	•	Optionally include data snapshots/seed data
	•	Track applied versions
	6.	Module/Plugin Manager
	•	Install/enable custom backend modules (constraints apply)
	•	Register custom UI components/page widgets (front-end plugins)

9.3 Reusable React Components (required)
	1.	DataTable
	•	Configurable columns
	•	Filters UI for supported ops
	•	Sorting on any displayed column
	•	Pagination controls
	•	Saved views (optional)
	2.	ResourceForm
	•	Create/update/delete resource
	•	Field validations from schema
	•	Field-level auth aware (hide/disable)
	3.	ResourceLayout
	•	Arrange multiple panels/widgets bound to resources
	•	Links to user-defined pages
	4.	Navigation Components
	•	Top menu + left subnav
	•	Role-based visibility

All UI configuration is stored in DB and editable via UI.

⸻

10. TypeScript SDK Wrapper

10.1 Discovery
	•	GET /api/_meta/resources
	•	list collections
	•	fields, types, validation
	•	supported operations
	•	authz hints (what current user can do)

10.2 SDK Features
	•	client.resource("customers").list({ filters, sort, page, fields })
	•	get(id)
	•	create(payload)
	•	update(id, payload) (PUT)
	•	patch(id, partial)
	•	delete(id)
	•	Strong typing generated at build time optionally using OpenAPI + codegen.
	•	Runtime-safe mode for fully dynamic (since collections can be created at runtime).

⸻

11. Extensibility Model

11.1 Backend Extension Points (interfaces)
	•	Storage adapter: table/jsonb/custom
	•	Validation engine: default + custom validators
	•	AuthZ provider: RBAC default; allow ABAC later
	•	Event publishing hooks
	•	Custom routes:
	•	Implement common interface:
	•	define route
	•	input/output schemas
	•	authz requirements
	•	optional UI configuration contributions

11.2 Delivery of Custom Code Modules

Two supported paths:

A) Git-Based Deployment (recommended for enterprise)
	•	Repo contains:
	•	custom backend modules
	•	optional UI plugin package
	•	CI builds images; helm deploy to envs

B) UI-based Module Upload (hosted model)
	•	Restricted sandboxing policy required:
	•	only signed modules
	•	explicit enable/disable
	•	versioning and rollback
	•	Operational guardrails:
	•	resource limits
	•	runtime isolation (separate classloader or sidecar)

⸻

12. Microservice Packaging & Domain Services

12.1 Service Composition
	•	A deployment may include:
	•	emf-control-plane
	•	emf-domain-<domainName> services (one or many)
	•	emf-ui (static web app)
	•	Domain service can own:
	•	its own collections
	•	custom modules
	•	domain-specific workflows/events

12.2 Configuration Propagation
	•	Control plane is source of truth
	•	Kafka topics:
	•	config.collection.changed
	•	config.authz.changed
	•	config.ui.changed
	•	Domain services subscribe and apply config live.

⸻

13. Schema & Data Migration Between Environments

13.1 Requirements
	•	Promote schema + UI config + auth policies between environments
	•	Promote data optionally (seed/reference/master data)
	•	Safe + repeatable + auditable

13.2 “Package” Format

emf-package.json includes:
	•	collections + versions
	•	authz roles/policies
	•	UI pages/menus
	•	OIDC provider allowlist config (optional; environment-specific override support)
	•	optional data payloads

13.3 Tooling
	•	CLI tool: emfctl
	•	emfctl export --service X --out pkg.json
	•	emfctl import --in pkg.json --dry-run
	•	emfctl apply --in pkg.json
	•	emfctl promote dev -> qa (using configured endpoints)
	•	Server endpoints also exist to support UI-based promotion flows.

⸻

14. Testing Strategy (3 Layers)

14.1 Unit Tests (pre-merge)
	•	Pure Java unit tests (JUnit 5)
	•	Mock external dependencies

14.2 Local Integration Tests (pre-merge, containerized)
	•	Runs with Docker Compose/Testcontainers:
	•	Postgres
	•	Kafka
	•	Redis
	•	Service container
	•	Validates:
	•	schema migrations
	•	dynamic collection creation
	•	authz enforcement
	•	filtering/sorting/paging correctness

14.3 End-to-End Tests (post-deploy)
	•	Runs against deployed environment
	•	Includes UI flows:
	•	create collection → CRUD data → modify schema → verify no downtime
	•	role-based menu visibility + field masking
	•	import/export across environments

⸻

15. Runtime Availability / No Restart Requirement

15.1 Requirements
	•	Creating/modifying collections must not require restart
	•	No disruption in service

15.2 Mechanisms
	•	Routing is dynamic:
	•	A base controller handles generated routes and delegates to runtime registry.
	•	Registry stores:
	•	compiled schema
	•	query builders
	•	validators
	•	authz policy maps
	•	Updates are atomic:
	•	build new registry snapshot → swap reference (copy-on-write)
	•	Migrations:
	•	Apply online where possible
	•	For destructive changes:
	•	require compatibility plan (e.g., add new column first, backfill, switch reads/writes)

⸻

16. Kubernetes Optimization

16.1 Required K8s Features
	•	Readiness/liveness probes via Actuator
	•	Horizontal Pod Autoscaling friendly metrics
	•	Stateless services (config in DB/Kafka)
	•	Graceful shutdown:
	•	stop accepting traffic
	•	complete inflight requests
	•	commit Kafka offsets safely

16.2 Helm Chart Defaults
	•	resource requests/limits
	•	env vars for:
	•	DB, Kafka, Redis
	•	OIDC issuer allowlist
	•	service identity
	•	secrets management compatible (K8s secrets / external secret operator)

⸻

17. Core REST Endpoints (Control Plane)

Examples (names adjustable):

Collections
	•	GET /control/collections
	•	POST /control/collections
	•	GET /control/collections/{id}
	•	PUT /control/collections/{id}
	•	POST /control/collections/{id}/export
	•	POST /control/collections/import

AuthZ / Roles
	•	GET /control/roles
	•	POST /control/roles
	•	PUT /control/policies

UI Config
	•	GET /ui/config/bootstrap
	•	GET /ui/pages
	•	POST /ui/pages
	•	PUT /ui/pages/{id}
	•	PUT /ui/menus/top
	•	PUT /ui/menus/side

Resource Discovery
	•	GET /api/_meta/resources
	•	GET /openapi.json

⸻

18. Opinionated Defaults

The framework should “just work” with minimal config:
	•	Postgres physical tables by default
	•	CRUD + query features enabled automatically
	•	OpenAPI always enabled
	•	JWT validation always enabled (requires configuring issuers)
	•	Default roles and admin bootstrap
	•	Actuator + metrics + tracing enabled
	•	Kafka lifecycle events enabled (configurable)

⸻

19. Open Source / Hosted Service Considerations

19.1 OSS Core
	•	Core runtime, UI builder, SDK, default adapters

19.2 Hosted Offering Adds
	•	Multi-tenant management UI
	•	Billing hooks
	•	Automated backups and restore
	•	Signed plugin marketplace
	•	SLA monitoring and incident tooling

⸻

20. Deliverables and Milestones (suggested)
	1.	MVP Runtime CRUD + Collection Designer
	•	Create collection → physical table → CRUD endpoints → OpenAPI
	2.	AuthN/AuthZ + Field masking
	3.	UI Builder + menus + pages
	4.	Import/Export + env promotion tooling
	5.	Plugin system
	6.	Hardening for hosted 24/7 ops
	•	multi-tenancy, backups, rate limiting, audit trails

⸻

If you want, I can also generate:
	•	a domain model / schema diagram for the control-plane tables,
	•	a full OpenAPI outline for control endpoints,
	•	or a module-by-module backlog with epics and acceptance criteria (good for kicking off implementation).


Absolutely — below is a concrete domain model (tables + relationships) and a starter OpenAPI spec for the Control Plane + UI Config + Packaging + Discovery endpoints. I’ll also include the Data Plane CRUD pattern (runtime-generated) in a way that fits OpenAPI’s limitations.

One note for accuracy: as of my knowledge cutoff (Aug 2025), the latest widely-supported OpenAPI spec is 3.1.x. You can still implement your framework such that it outputs 3.2 when/if finalized, but today you’ll likely generate 3.1 and keep it forward-compatible (same JSON Schema alignment). I’ll write the spec as 3.1.0 with a clear “target 3.2” note.

⸻

1) Domain Model (Control Plane + UI + AuthZ + Migration)

1.1 Core concepts
	•	Service = a deployable runtime (domain microservice) that hosts collections/resources.
	•	Collection = a runtime-defined resource (like customers) with fields, API config, authz, and storage strategy.
	•	Field = schema + validation + indexing + authz overrides.
	•	Role / Policy = RBAC for routes/fields/pages/menus.
	•	UI Page/Menu = declarative UI definitions stored in DB.
	•	Package = export/import unit for promoting between environments.
	•	Migration = schema evolution plans and executions.

1.2 Logical ERD (Mermaid)

erDiagram
  SERVICE ||--o{ COLLECTION : hosts
  COLLECTION ||--o{ FIELD : has
  COLLECTION ||--o{ COLLECTION_VERSION : versions
  COLLECTION_VERSION ||--o{ FIELD_VERSION : includes
  ROLE ||--o{ POLICY : referenced_by
  COLLECTION ||--o{ ROUTE_POLICY : secured_by
  COLLECTION ||--o{ FIELD_POLICY : secured_by
  UI_PAGE ||--o{ UI_PAGE_POLICY : secured_by
  UI_MENU ||--o{ UI_MENU_ITEM : contains
  UI_MENU_ITEM ||--o{ UI_MENU_ITEM_POLICY : secured_by
  OIDC_PROVIDER ||--o{ SERVICE : allowed_in
  PACKAGE ||--o{ PACKAGE_ITEM : contains
  MIGRATION_RUN ||--o{ MIGRATION_STEP : includes

  SERVICE {
    uuid id PK
    string name "unique"
    string description
    string basePath "e.g. /api"
    string environment "dev/qa/stage/prod"
    timestamptz createdAt
    timestamptz updatedAt
  }

  COLLECTION {
    uuid id PK
    uuid serviceId FK
    string name "unique per service"
    string displayName
    int version "current applied version"
    string storageMode "TABLE|JSONB|CUSTOM"
    string tableName "if TABLE"
    bool softDelete
    bool timestamps
    bool multiTenant
    string tenantClaimPath "JWT claim path"
    jsonb apiConfig
    jsonb eventsConfig
    jsonb auditConfig
    bool isActive
    timestamptz createdAt
    timestamptz updatedAt
  }

  FIELD {
    uuid id PK
    uuid collectionId FK
    string name
    string displayName
    string dataType
    bool nullable
    bool immutable
    bool indexed
    bool unique
    int min
    int max
    int length
    string pattern
    string defaultValue
    jsonb enumValues
    jsonb reference "relationship metadata"
    jsonb validation "additional validation"
    int ordinal
    bool isActive
    timestamptz createdAt
    timestamptz updatedAt
  }

  COLLECTION_VERSION {
    uuid id PK
    uuid collectionId FK
    int version
    jsonb snapshot "full compiled definition"
    string status "DRAFT|APPLIED|FAILED|ROLLED_BACK"
    timestamptz createdAt
    string createdBy
  }

  FIELD_VERSION {
    uuid id PK
    uuid collectionVersionId FK
    jsonb snapshot
  }

  ROLE {
    uuid id PK
    uuid serviceId FK
    string name "unique per service"
    string description
    bool isSystem
    timestamptz createdAt
    timestamptz updatedAt
  }

  POLICY {
    uuid id PK
    uuid serviceId FK
    string name
    string description
    jsonb rules "ABAC-ready, RBAC default"
    timestamptz createdAt
    timestamptz updatedAt
  }

  ROUTE_POLICY {
    uuid id PK
    uuid collectionId FK
    string operation "LIST|GET|POST|PUT|PATCH|DELETE"
    jsonb allowRoles
    jsonb denyRoles
    jsonb conditions "optional"
  }

  FIELD_POLICY {
    uuid id PK
    uuid fieldId FK
    jsonb readRoles
    jsonb writeRoles
    jsonb maskStrategy "NONE|NULL|REDACT|HASH"
  }

  UI_PAGE {
    uuid id PK
    uuid serviceId FK
    string name
    string path "route in UI"
    string type "TABLE|FORM|DETAIL|CUSTOM"
    jsonb definition "layout/widgets/bindings"
    bool isActive
    timestamptz createdAt
    timestamptz updatedAt
  }

  UI_PAGE_POLICY {
    uuid id PK
    uuid pageId FK
    jsonb allowRoles
  }

  UI_MENU {
    uuid id PK
    uuid serviceId FK
    string name "TOP|SIDE context"
    jsonb context "e.g. top selection id"
    bool isActive
    timestamptz createdAt
    timestamptz updatedAt
  }

  UI_MENU_ITEM {
    uuid id PK
    uuid menuId FK
    string label
    string icon
    string targetType "PAGE|URL|RESOURCE"
    string targetRef
    int ordinal
    bool isActive
  }

  UI_MENU_ITEM_POLICY {
    uuid id PK
    uuid menuItemId FK
    jsonb allowRoles
  }

  OIDC_PROVIDER {
    uuid id PK
    uuid serviceId FK
    string issuerUrl
    string jwksUrl
    string audience
    jsonb claimMappings "roles path, tenant path, etc."
    bool isActive
    timestamptz createdAt
    timestamptz updatedAt
  }

  PACKAGE {
    uuid id PK
    uuid serviceId FK
    string name
    string versionTag
    string sourceEnv
    string targetEnv
    jsonb manifest
    timestamptz createdAt
    string createdBy
  }

  PACKAGE_ITEM {
    uuid id PK
    uuid packageId FK
    string itemType "COLLECTION|ROLE|POLICY|UI_PAGE|UI_MENU|OIDC_PROVIDER|DATA"
    uuid itemId
    jsonb payload "for portability"
  }

  MIGRATION_RUN {
    uuid id PK
    uuid collectionId FK
    int fromVersion
    int toVersion
    string status "PLANNED|RUNNING|SUCCEEDED|FAILED"
    jsonb plan
    timestamptz startedAt
    timestamptz finishedAt
    string executedBy
  }

  MIGRATION_STEP {
    uuid id PK
    uuid migrationRunId FK
    int stepNo
    string kind "SQL|BACKFILL|INDEX|VALIDATION"
    jsonb detail
    string status
    timestamptz startedAt
    timestamptz finishedAt
    string error
  }

1.3 Physical modeling notes (Postgres)
	•	Use uuid PKs everywhere.
	•	Use jsonb for “definition payloads” so you can evolve without migration churn.
	•	Store a compiled snapshot of each collection version (collection_version.snapshot) to ensure deterministic replays/rollbacks.
	•	Consider citext extension for case-insensitive comparisons (helps ieq, icontains).
	•	Multi-tenancy: if enabled per collection, add tenant_id column (type text or uuid) and auto-apply filter from JWT claim.

⸻

2) OpenAPI Spec (Control Plane + UI + Packages + Discovery)

2.1 Structure
	•	Control Plane endpoints manage definitions (collections, fields, authz, OIDC providers, packages, migrations).
	•	UI endpoints serve bootstrap + page/menu configs.
	•	Discovery endpoint enumerates runtime resources (used by the TS SDK + UI).
	•	Data Plane CRUD endpoints are runtime-generated; OpenAPI can expose:
	•	a discovery endpoint for dynamic schemas
	•	plus optionally “example paths” or vendor extension describing templated endpoints

Below is a starter OpenAPI 3.1.0 YAML you can generate/expand. (Your generator can output JSON too.)

2.2 OpenAPI YAML (starter)

openapi: 3.1.0
info:
  title: EMF Control Plane API
  version: 0.1.0
  description: >
    Control plane APIs for runtime-defined collections, UI configuration, RBAC policies, OIDC provider config,
    packaging (export/import), and schema migrations. Target output: OpenAPI 3.2 when available; current spec is 3.1.

servers:
  - url: https://{host}
    variables:
      host:
        default: api.example.com

security:
  - bearerAuth: []

tags:
  - name: Collections
  - name: Roles
  - name: Policies
  - name: OIDC
  - name: UI
  - name: Packages
  - name: Migrations
  - name: Discovery

paths:
  /control/collections:
    get:
      tags: [Collections]
      summary: List collections
      parameters:
        - $ref: "#/components/parameters/PageNumber"
        - $ref: "#/components/parameters/PageSize"
        - $ref: "#/components/parameters/Sort"
        - $ref: "#/components/parameters/Fields"
        - $ref: "#/components/parameters/Filter"
      responses:
        "200":
          description: Collection list
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_Collection"
        default:
          $ref: "#/components/responses/Error"
    post:
      tags: [Collections]
      summary: Create a collection (resource)
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/CollectionCreateRequest"
      responses:
        "201":
          description: Created
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Collection"
        default:
          $ref: "#/components/responses/Error"

  /control/collections/{collectionId}:
    get:
      tags: [Collections]
      summary: Get a collection by id
      parameters:
        - $ref: "#/components/parameters/CollectionId"
      responses:
        "200":
          description: Collection
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Collection"
        default:
          $ref: "#/components/responses/Error"
    put:
      tags: [Collections]
      summary: Replace collection definition (creates new version)
      parameters:
        - $ref: "#/components/parameters/CollectionId"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/CollectionUpdateRequest"
      responses:
        "200":
          description: Updated (new version created; may trigger migration)
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Collection"
        default:
          $ref: "#/components/responses/Error"
    delete:
      tags: [Collections]
      summary: Deactivate a collection (soft disable)
      parameters:
        - $ref: "#/components/parameters/CollectionId"
      responses:
        "204":
          description: Deactivated
        default:
          $ref: "#/components/responses/Error"

  /control/collections/{collectionId}/fields:
    get:
      tags: [Collections]
      summary: List fields for a collection
      parameters:
        - $ref: "#/components/parameters/CollectionId"
      responses:
        "200":
          description: Fields
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_Field"
        default:
          $ref: "#/components/responses/Error"
    post:
      tags: [Collections]
      summary: Add a field (creates new collection version)
      parameters:
        - $ref: "#/components/parameters/CollectionId"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/FieldCreateRequest"
      responses:
        "201":
          description: Field created
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Field"
        default:
          $ref: "#/components/responses/Error"

  /control/collections/{collectionId}/fields/{fieldId}:
    put:
      tags: [Collections]
      summary: Update field definition (creates new collection version)
      parameters:
        - $ref: "#/components/parameters/CollectionId"
        - $ref: "#/components/parameters/FieldId"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/FieldUpdateRequest"
      responses:
        "200":
          description: Updated field
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Field"
        default:
          $ref: "#/components/responses/Error"
    delete:
      tags: [Collections]
      summary: Deactivate field (creates new collection version)
      parameters:
        - $ref: "#/components/parameters/CollectionId"
        - $ref: "#/components/parameters/FieldId"
      responses:
        "204":
          description: Deactivated
        default:
          $ref: "#/components/responses/Error"

  /control/roles:
    get:
      tags: [Roles]
      summary: List roles
      responses:
        "200":
          description: Roles
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_Role"
        default:
          $ref: "#/components/responses/Error"
    post:
      tags: [Roles]
      summary: Create role
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/RoleCreateRequest"
      responses:
        "201":
          description: Role created
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Role"
        default:
          $ref: "#/components/responses/Error"

  /control/policies:
    get:
      tags: [Policies]
      summary: List policies
      responses:
        "200":
          description: Policies
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_Policy"
        default:
          $ref: "#/components/responses/Error"
    post:
      tags: [Policies]
      summary: Create policy
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/PolicyCreateRequest"
      responses:
        "201":
          description: Policy created
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Policy"
        default:
          $ref: "#/components/responses/Error"

  /control/collections/{collectionId}/authz:
    put:
      tags: [Policies]
      summary: Set route + field authorization for a collection
      parameters:
        - $ref: "#/components/parameters/CollectionId"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/CollectionAuthzUpdateRequest"
      responses:
        "200":
          description: Applied authz configuration
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/CollectionAuthz"
        default:
          $ref: "#/components/responses/Error"

  /control/oidc/providers:
    get:
      tags: [OIDC]
      summary: List allowed OIDC providers
      responses:
        "200":
          description: Providers
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_OidcProvider"
        default:
          $ref: "#/components/responses/Error"
    post:
      tags: [OIDC]
      summary: Add allowed OIDC provider
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/OidcProviderCreateRequest"
      responses:
        "201":
          description: Provider created
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/OidcProvider"
        default:
          $ref: "#/components/responses/Error"

  /ui/config/bootstrap:
    get:
      tags: [UI]
      summary: UI bootstrap config (menus, pages, feature flags for current user)
      responses:
        "200":
          description: Bootstrap config
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/UiBootstrap"

  /ui/pages:
    get:
      tags: [UI]
      summary: List UI pages
      responses:
        "200":
          description: Pages
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_UiPage"
    post:
      tags: [UI]
      summary: Create UI page
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/UiPageCreateRequest"
      responses:
        "201":
          description: Created page
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/UiPage"

  /ui/pages/{pageId}:
    put:
      tags: [UI]
      summary: Update UI page
      parameters:
        - $ref: "#/components/parameters/PageId"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/UiPageUpdateRequest"
      responses:
        "200":
          description: Updated page
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/UiPage"

  /ui/menus:
    get:
      tags: [UI]
      summary: List menus (top + side)
      responses:
        "200":
          description: Menus
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_UiMenu"

  /ui/menus/{menuId}:
    put:
      tags: [UI]
      summary: Update menu definition
      parameters:
        - $ref: "#/components/parameters/MenuId"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/UiMenuUpdateRequest"
      responses:
        "200":
          description: Updated menu
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/UiMenu"

  /control/packages/export:
    post:
      tags: [Packages]
      summary: Export a configuration package
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/PackageExportRequest"
      responses:
        "200":
          description: Package payload (JSON)
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Package"

  /control/packages/import:
    post:
      tags: [Packages]
      summary: Import a configuration package
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/PackageImportRequest"
      responses:
        "200":
          description: Import result (dry-run or applied)
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/PackageImportResult"
        default:
          $ref: "#/components/responses/Error"

  /control/migrations/plan:
    post:
      tags: [Migrations]
      summary: Generate a migration plan for a proposed collection update
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/MigrationPlanRequest"
      responses:
        "200":
          description: Migration plan
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/MigrationPlan"
        default:
          $ref: "#/components/responses/Error"

  /control/migrations/runs:
    get:
      tags: [Migrations]
      summary: List migration runs
      responses:
        "200":
          description: Migration runs
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListResponse_MigrationRun"
        default:
          $ref: "#/components/responses/Error"

  /api/_meta/resources:
    get:
      tags: [Discovery]
      summary: Discover runtime resources and their schemas (for UI/SDK)
      responses:
        "200":
          description: Resource metadata
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ResourceDiscovery"

  /openapi.json:
    get:
      tags: [Discovery]
      summary: Get generated OpenAPI (service-specific; includes runtime collections)
      responses:
        "200":
          description: OpenAPI document
          content:
            application/json:
              schema:
                type: object

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  parameters:
    CollectionId:
      name: collectionId
      in: path
      required: true
      schema: { type: string, format: uuid }
    FieldId:
      name: fieldId
      in: path
      required: true
      schema: { type: string, format: uuid }
    PageId:
      name: pageId
      in: path
      required: true
      schema: { type: string, format: uuid }
    MenuId:
      name: menuId
      in: path
      required: true
      schema: { type: string, format: uuid }

    PageNumber:
      name: page[number]
      in: query
      schema: { type: integer, minimum: 1, default: 1 }
    PageSize:
      name: page[size]
      in: query
      schema: { type: integer, minimum: 1, maximum: 500, default: 25 }
    Sort:
      name: sort
      in: query
      schema:
        type: string
        description: Comma-separated fields. Prefix with '-' for descending.
    Fields:
      name: fields
      in: query
      schema:
        type: string
        description: Comma-separated field list (fieldsets).

    Filter:
      name: filter
      in: query
      schema:
        type: string
      description: >
        Filtering syntax is dynamic. Recommended pattern:
        filter[field][op]=value (e.g., filter[age][gt]=21). Encode as query params.

  responses:
    Error:
      description: Error response
      content:
        application/json:
          schema:
            $ref: "#/components/schemas/ErrorResponse"

  schemas:
    ListMeta:
      type: object
      properties:
        page:
          type: object
          properties:
            number: { type: integer }
            size: { type: integer }
            totalPages: { type: integer }
            totalItems: { type: integer }

    ErrorResponse:
      type: object
      properties:
        errors:
          type: array
          items:
            $ref: "#/components/schemas/ErrorItem"
      required: [errors]

    ErrorItem:
      type: object
      properties:
        code: { type: string }
        message: { type: string }
        details: { type: object, additionalProperties: true }
        traceId: { type: string }
      required: [code, message]

    Collection:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        displayName: { type: string }
        version: { type: integer }
        storageMode: { type: string, enum: [TABLE, JSONB, CUSTOM] }
        softDelete: { type: boolean }
        timestamps: { type: boolean }
        multiTenant: { type: boolean }
        apiConfig: { type: object, additionalProperties: true }
        eventsConfig: { type: object, additionalProperties: true }
        auditConfig: { type: object, additionalProperties: true }
        isActive: { type: boolean }
        fields:
          type: array
          items: { $ref: "#/components/schemas/Field" }
      required: [id, name, version, storageMode, isActive]

    Field:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        displayName: { type: string }
        dataType: { type: string }
        nullable: { type: boolean }
        immutable: { type: boolean }
        indexed: { type: boolean }
        unique: { type: boolean }
        min: { type: integer }
        max: { type: integer }
        length: { type: integer }
        pattern: { type: string }
        defaultValue: { type: string }
        enumValues: { type: array, items: { type: string } }
        reference: { type: object, additionalProperties: true }
        validation: { type: object, additionalProperties: true }
        ordinal: { type: integer }
        isActive: { type: boolean }
      required: [id, name, dataType, isActive]

    CollectionCreateRequest:
      type: object
      properties:
        name: { type: string, minLength: 1 }
        displayName: { type: string }
        storageMode: { type: string, enum: [TABLE, JSONB, CUSTOM], default: TABLE }
        softDelete: { type: boolean, default: false }
        timestamps: { type: boolean, default: true }
        multiTenant: { type: boolean, default: false }
        tenantClaimPath: { type: string, description: "JWT claim path if multiTenant" }
        apiConfig: { type: object, additionalProperties: true }
        eventsConfig: { type: object, additionalProperties: true }
        auditConfig: { type: object, additionalProperties: true }
        fields:
          type: array
          items: { $ref: "#/components/schemas/FieldCreateRequest" }
      required: [name]

    CollectionUpdateRequest:
      allOf:
        - $ref: "#/components/schemas/CollectionCreateRequest"
        - type: object
          properties:
            expectedVersion:
              type: integer
              description: "Optimistic concurrency control; must match current version."

    FieldCreateRequest:
      type: object
      properties:
        name: { type: string }
        displayName: { type: string }
        dataType: { type: string }
        nullable: { type: boolean, default: true }
        immutable: { type: boolean, default: false }
        indexed: { type: boolean, default: false }
        unique: { type: boolean, default: false }
        min: { type: integer }
        max: { type: integer }
        length: { type: integer }
        pattern: { type: string }
        defaultValue: { type: string }
        enumValues: { type: array, items: { type: string } }
        reference: { type: object, additionalProperties: true }
        validation: { type: object, additionalProperties: true }
        ordinal: { type: integer }
      required: [name, dataType]

    FieldUpdateRequest:
      allOf:
        - $ref: "#/components/schemas/FieldCreateRequest"
        - type: object
          properties:
            expectedVersion:
              type: integer
              description: "Optional field-level OCC if you version fields independently."

    Role:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        description: { type: string }
        isSystem: { type: boolean }
      required: [id, name]

    RoleCreateRequest:
      type: object
      properties:
        name: { type: string }
        description: { type: string }
      required: [name]

    Policy:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        description: { type: string }
        rules: { type: object, additionalProperties: true }
      required: [id, name, rules]

    PolicyCreateRequest:
      type: object
      properties:
        name: { type: string }
        description: { type: string }
        rules: { type: object, additionalProperties: true }
      required: [name, rules]

    CollectionAuthz:
      type: object
      properties:
        collectionId: { type: string, format: uuid }
        routePolicies:
          type: array
          items: { $ref: "#/components/schemas/RoutePolicy" }
        fieldPolicies:
          type: array
          items: { $ref: "#/components/schemas/FieldPolicy" }
      required: [collectionId]

    CollectionAuthzUpdateRequest:
      $ref: "#/components/schemas/CollectionAuthz"

    RoutePolicy:
      type: object
      properties:
        operation: { type: string, enum: [LIST, GET, POST, PUT, PATCH, DELETE] }
        allowRoles: { type: array, items: { type: string } }
        denyRoles: { type: array, items: { type: string } }
        conditions: { type: object, additionalProperties: true }
      required: [operation, allowRoles]

    FieldPolicy:
      type: object
      properties:
        fieldId: { type: string, format: uuid }
        readRoles: { type: array, items: { type: string } }
        writeRoles: { type: array, items: { type: string } }
        maskStrategy: { type: string, enum: [NONE, NULL, REDACT, HASH], default: NONE }
      required: [fieldId]

    OidcProvider:
      type: object
      properties:
        id: { type: string, format: uuid }
        issuerUrl: { type: string }
        jwksUrl: { type: string }
        audience: { type: string }
        claimMappings: { type: object, additionalProperties: true }
        isActive: { type: boolean }
      required: [id, issuerUrl, isActive]

    OidcProviderCreateRequest:
      type: object
      properties:
        issuerUrl: { type: string }
        jwksUrl: { type: string }
        audience: { type: string }
        claimMappings: { type: object, additionalProperties: true }
      required: [issuerUrl]

    UiBootstrap:
      type: object
      properties:
        branding: { type: object, additionalProperties: true }
        currentUser:
          type: object
          additionalProperties: true
        menus:
          type: array
          items: { $ref: "#/components/schemas/UiMenu" }
        pages:
          type: array
          items: { $ref: "#/components/schemas/UiPage" }
        features: { type: object, additionalProperties: true }
      required: [menus, pages]

    UiPage:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        path: { type: string }
        type: { type: string, enum: [TABLE, FORM, DETAIL, CUSTOM] }
        definition: { type: object, additionalProperties: true }
        isActive: { type: boolean }
      required: [id, name, path, type, isActive]

    UiPageCreateRequest:
      type: object
      properties:
        name: { type: string }
        path: { type: string }
        type: { type: string, enum: [TABLE, FORM, DETAIL, CUSTOM] }
        definition: { type: object, additionalProperties: true }
      required: [name, path, type, definition]

    UiPageUpdateRequest:
      allOf:
        - $ref: "#/components/schemas/UiPageCreateRequest"
        - type: object
          properties:
            expectedVersion: { type: integer }

    UiMenu:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string, description: "TOP or SIDE or custom contexts" }
        context: { type: object, additionalProperties: true }
        items:
          type: array
          items: { $ref: "#/components/schemas/UiMenuItem" }
        isActive: { type: boolean }
      required: [id, name, isActive]

    UiMenuItem:
      type: object
      properties:
        id: { type: string, format: uuid }
        label: { type: string }
        icon: { type: string }
        targetType: { type: string, enum: [PAGE, URL, RESOURCE] }
        targetRef: { type: string }
        ordinal: { type: integer }
        visibilityRoles: { type: array, items: { type: string } }
        isActive: { type: boolean }
      required: [id, label, targetType, targetRef, isActive]

    UiMenuUpdateRequest:
      type: object
      properties:
        name: { type: string }
        context: { type: object, additionalProperties: true }
        items:
          type: array
          items: { $ref: "#/components/schemas/UiMenuItem" }
        isActive: { type: boolean }
      required: [name, items]

    Package:
      type: object
      properties:
        name: { type: string }
        versionTag: { type: string }
        sourceEnv: { type: string }
        targetEnv: { type: string }
        manifest: { type: object, additionalProperties: true }
        items:
          type: array
          items: { $ref: "#/components/schemas/PackageItem" }
      required: [name, versionTag, items]

    PackageItem:
      type: object
      properties:
        itemType: { type: string, enum: [COLLECTION, ROLE, POLICY, UI_PAGE, UI_MENU, OIDC_PROVIDER, DATA] }
        itemId: { type: string, format: uuid }
        payload: { type: object, additionalProperties: true }
      required: [itemType, payload]

    PackageExportRequest:
      type: object
      properties:
        include:
          type: array
          items: { type: string, enum: [COLLECTIONS, ROLES, POLICIES, UI, OIDC, DATA] }
        collectionIds:
          type: array
          items: { type: string, format: uuid }
        includeData:
          type: boolean
          default: false
      required: [include]

    PackageImportRequest:
      type: object
      properties:
        dryRun: { type: boolean, default: true }
        package:
          $ref: "#/components/schemas/Package"
        envOverrides:
          type: object
          additionalProperties: true
      required: [package]

    PackageImportResult:
      type: object
      properties:
        dryRun: { type: boolean }
        applied: { type: boolean }
        changes:
          type: array
          items: { type: object, additionalProperties: true }
        warnings:
          type: array
          items: { type: string }
        errors:
          type: array
          items: { type: string }
      required: [dryRun, applied]

    MigrationPlanRequest:
      type: object
      properties:
        collectionId: { type: string, format: uuid }
        proposedDefinition:
          $ref: "#/components/schemas/CollectionUpdateRequest"
      required: [collectionId, proposedDefinition]

    MigrationPlan:
      type: object
      properties:
        fromVersion: { type: integer }
        toVersion: { type: integer }
        steps:
          type: array
          items: { $ref: "#/components/schemas/MigrationStep" }
        warnings:
          type: array
          items: { type: string }
      required: [fromVersion, toVersion, steps]

    MigrationRun:
      type: object
      properties:
        id: { type: string, format: uuid }
        collectionId: { type: string, format: uuid }
        fromVersion: { type: integer }
        toVersion: { type: integer }
        status: { type: string, enum: [PLANNED, RUNNING, SUCCEEDED, FAILED] }
        startedAt: { type: string, format: date-time }
        finishedAt: { type: string, format: date-time }
      required: [id, collectionId, status]

    MigrationStep:
      type: object
      properties:
        stepNo: { type: integer }
        kind: { type: string, enum: [SQL, BACKFILL, INDEX, VALIDATION] }
        detail: { type: object, additionalProperties: true }
      required: [stepNo, kind, detail]

    ResourceDiscovery:
      type: object
      properties:
        resources:
          type: array
          items: { $ref: "#/components/schemas/DiscoveredResource" }
      required: [resources]

    DiscoveredResource:
      type: object
      properties:
        name: { type: string }
        basePath: { type: string }
        operations:
          type: array
          items: { type: string, enum: [LIST, GET, POST, PUT, PATCH, DELETE] }
        schema:
          type: object
          description: JSON Schema describing fields + validation
          additionalProperties: true
        authzHints:
          type: object
          additionalProperties: true
      required: [name, basePath, operations]

    ListResponse_Collection:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/Collection" }
        meta: { $ref: "#/components/schemas/ListMeta" }
      required: [data]

    ListResponse_Field:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/Field" }
      required: [data]

    ListResponse_Role:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/Role" }
      required: [data]

    ListResponse_Policy:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/Policy" }
      required: [data]

    ListResponse_OidcProvider:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/OidcProvider" }
      required: [data]

    ListResponse_UiPage:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/UiPage" }
      required: [data]

    ListResponse_UiMenu:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/UiMenu" }
      required: [data]

    ListResponse_MigrationRun:
      type: object
      properties:
        data:
          type: array
          items: { $ref: "#/components/schemas/MigrationRun" }
      required: [data]


⸻

3) How to Represent Runtime CRUD APIs in OpenAPI

Because collections are created dynamically, you have two realistic strategies:

Strategy A (recommended): serve a full generated OpenAPI per service at runtime
	•	GET /openapi.json always returns the current spec, including all collections and their schemas.
	•	This keeps OpenAPI “normal” and allows codegen, docs, etc.

Strategy B: keep static OpenAPI plus discovery
	•	Keep OpenAPI stable and describe CRUD as a templated capability.
	•	Use /api/_meta/resources for actual schemas/routes.
	•	Your TS SDK can rely on discovery for runtime typing.

In practice, you’ll do A (primary) and still keep discovery because the UI/SDK need permissions + field-level auth hints.

⸻

4) Next step: choose your storage default and versioning rules (I’ll assume reasonable defaults)

If you want, I can refine the domain model into:
	•	exact Postgres DDL (tables, indexes, constraints),
	•	recommended indexes for filtering ops,
	•	and the exact payload format for uiPage.definition and apiConfig (so UI builder + runtime match cleanly).

Tell me whether your opinionated default is:
	1.	Physical tables per collection (best performance), or
	2.	JSONB store (fastest evolution),
and I’ll tailor the migration model + query builder schema accordingly.