# EMF — Enterprise Microservice Framework
## Original Specification

### 1. Overview

**1.1 Product Name (working)**
EMF — Enterprise Microservice Framework

**1.2 Problem Statement**
Teams want to ship enterprise-grade, multi-tenant-ready microservices quickly with strong conventions, automatic API documentation, runtime-configurable resources, and an admin UI that can define and evolve data-backed collections without service restarts.

**1.3 Primary Goals**
- Developer speed / experience first (convention over configuration)
- Opinionated defaults for security, persistence, messaging, observability, and testing
- Runtime-configurable resources (collections) created/modified via UI → exposed as REST endpoints without restart
- Extensible at every layer (storage, authz, routing, validation, events, UI components)
- Kubernetes-friendly and production-grade (metrics, logging, tracing, health checks)
- Open source core, with a path to hosted paid service (24/7 operations)

**1.4 Non-Goals (initially)**
- GraphQL support (possible future extension)
- Compound documents (explicitly not needed due to HTTP/2)
- Full workflow engine (can be added as a domain service later)

---

### 2. Technology Stack

**2.1 Backend (Java)**
- Java 21+ (recommended)
- Spring Boot (Web, Security, Actuator)
- PostgreSQL (primary persistence)
- Kafka (event streaming)
- Redis (caching, distributed locks, ephemeral config acceleration)
- OpenAPI 3.2 auto-generation for all APIs

**2.2 Frontend**
- React + TypeScript
- UI is self-configuring: only needs server URL; fetches configuration via REST
- Shared libraries:
  - TypeScript SDK wrapper (resource discovery, CRUD, filtering/sorting/pagination)
  - Reusable React components (tables, forms, layouts, navigation)

**2.3 Deployment**
- Container-first
- Kubernetes helm charts / Kustomize templates
- Supports multi-service "domain microservices" architecture

---

### 3. System Architecture

**3.1 High-Level Components**

1. **Gateway / API Edge** (optional)
   - Can be internal or external (NGINX / Envoy / API Gateway)
   - Handles TLS, HTTP/2 termination, rate limiting (optional)

2. **Core Runtime Service ("Control Plane" runtime APIs)**
   - Manages definitions:
     - Collections (resources)
     - Fields & validation rules
     - Role-based policies
     - UI page definitions, menus, navigation
     - Provider configuration (OIDC issuers)
   - Publishes configuration changes/events to Kafka

3. **Domain Microservices (Data Plane services)**
   - Each domain service can:
     - Host multiple runtime-defined collections
     - Provide built-in CRUD routes for data-backed resources
     - Host custom endpoints via plugin modules
     - Host its own postgres database for collections
   - Subscribes to config changes via Kafka and applies without restart

4. **PostgreSQL**
   - Stores:
     - Collection definitions
     - UI configuration
     - AuthZ policies
     - Data for collections (either per-collection tables OR a hybrid approach)

5. **Kafka**
   - Event bus:
     - Collection changed
     - Schema migration applied
     - Security policy changed
     - Data lifecycle events (resource created/updated/deleted)

6. **Redis**
   - Cache compiled configs (routes, field policies)
   - Optional distributed coordination:
     - "migration lock"
     - "config publish lock"
   - Optional rate limiting token buckets

7. **React Admin/Builder UI**
   - Defines and manages:
     - Collections/resources
     - Fields & rules
     - API exposure and authz per route/field
     - UI pages and menus
     - Imports/exports/migrations
     - Plugin/module management (where allowed)

---

### 4. Data Model & Runtime Resource Strategy

**4.1 Supported Storage Modes (extensible)**

**Mode A — Physical Tables** (recommended default for enterprise performance)
- Each collection → real Postgres table
- Add/change fields → online schema migrations
- Pros: best query performance, easier BI/export, strong typing
- Cons: schema migration complexity

**Mode B — JSONB Document Store**
- Single table per service, store records as JSONB + indexed extracted columns
- Pros: easiest runtime evolution
- Cons: query complexity and performance tradeoffs

**Spec requirement:** Provide opinionated default but extensible.
- ✅ Default: Mode A (Physical Tables) with a built-in Migration Engine
- ✅ Option: Mode B via storage adapter interface

**4.2 Collection Definition Model (Control Plane)**

A collection definition contains:
- `collectionId` (UUID)
- `name` (string, unique within service)
- `version` (int, incremented per change)
- `storageMode` (TABLE | JSONB | CUSTOM)
- `primaryKey` (default id UUID)
- `timestamps` (createdAt/updatedAt enabled by default)
- `softDelete` (optional)
- `fields[]`
  - fieldName
  - dataType (string, int, decimal, boolean, date, datetime, json, enum, reference, etc.)
  - nullable
  - min/max/length
  - pattern (regex)
  - defaultValue
  - indexed (boolean)
  - unique (boolean)
  - immutable (boolean)
  - references (collection + relationship type)
- `apiConfig`
  - enabled operations (GET, LIST, POST, PUT, PATCH, DELETE)
  - route base path: `/api/{collectionName}`
  - field sets rules
- `authzConfig`
  - route-level roles
  - field-level roles (read/write)
- `eventsConfig`
  - emit lifecycle events to Kafka
- `auditConfig`
  - track changes, actor, requestId

**4.3 Applying Changes Without Restart**

Lifecycle:
1. UI calls Control Plane to create/update a collection definition
2. Control Plane:
   - Validates request
   - Persists definition in Postgres
   - Generates a Migration Plan
3. Migration Engine executes online schema changes (with locking + safe rollout)
4. Control Plane publishes `CollectionUpdated` event to Kafka
5. Domain services subscribe and refresh in-memory routing/authz/config caches dynamically

**Safety Guarantees:**
- Changes are versioned; services apply only increasing versions
- All migrations are idempotent and recorded in `schema_migrations`
- Backward-compatible upgrades supported where possible; destructive changes require explicit flags

---

### 5. API Standards

**5.1 API Conventions (JSON:API-inspired)**
- Content type: `application/json`
- Use a consistent envelope for list endpoints:
  - `data` array
  - `meta` for pagination
  - `links`
- Single resource:
  - `data` object
  - `links`
- Errors:
  - `errors[]` with code/message/details/traceId

No compound documents are required (explicit requirement).

**5.2 Resource CRUD Endpoints (generated)**

For a collection `customers`:
- `GET /api/customers`
- `GET /api/customers/{id}`
- `POST /api/customers`
- `PUT /api/customers/{id}`
- `PATCH /api/customers/{id}`
- `DELETE /api/customers/{id}`

**5.3 Query Features Required on ALL GET List APIs**

**Pagination:**
- `page[number]`, `page[size]`
- Response meta:
  - `meta.page.number`, `meta.page.size`, `meta.page.totalPages`, `meta.page.totalItems`

**Sorting:**
- `sort=field1,-field2`

**Field Sets:**
- `fields=fieldA,fieldB,fieldC`

**Filtering (any column):**
Use `filter[field][op]=value` pattern:
- equals: `filter[name][eq]=Alice`
- not equal: `filter[name][neq]=Alice`
- greater than: `filter[age][gt]=21`
- less than: `filter[age][lt]=65`
- null: `filter[deletedAt][isnull]=true`
- contains: `filter[email][contains]=@company.com`
- starts with: `filter[name][starts]=Al`
- ends with: `filter[name][ends]=son`
- case insensitive variants: `filter[name][icontains]=bob`, `istarts`, `iends`, `ieq`

**Rules:**
- Default operation is equals if not specified
- Validate ops based on data type
- Index recommendation engine (warn in logs/metrics when unindexed filters cause slow queries)

---

### 6. OpenAPI 3.2 Generation

**6.1 Requirements**
- Every route (generated + custom) produces an OpenAPI 3.2 spec
- Spec updates when collections/routes change at runtime

**6.2 Approach**
- Maintain an internal canonical "API Model" derived from:
  - Collection definitions
  - Custom module route metadata
  - Security model
- Expose:
  - `GET /openapi.json`
  - `GET /openapi.yaml`
- Hot reload spec on config events
- Include schema definitions for runtime collections:
  - Basic types + validation constraints
  - Field-level read/write permissions in vendor extensions (e.g., `x-authz-read-roles`)

---

### 7. Security

**7.1 Authentication (AuthN)**
- JWT Bearer tokens issued by external OIDC provider (Auth0, Okta, Cognito, etc.)
- Configurable allowed issuers/audiences via UI + REST:
  - issuer URL(s)
  - JWKS endpoints
  - audience
  - required claims mappings (e.g., roles claim path)

Backend uses:
- Spring Security Resource Server (JWT validation)
- Cached JWKS keys

**7.2 Authorization (AuthZ) — Role-Based with Field-Level Controls**

**Concepts:**
- **Role:** named permission group
- **Policy:** ties roles to:
  - route operations (LIST/GET/POST/PUT/PATCH/DELETE)
  - field read/write per resource
  - UI menu/page access

**Enforcement Points:**
- Route-level: block endpoint access
- Field-level:
  - On reads: remove unauthorized fields (field masking)
  - On writes: reject updates to unauthorized fields (validation error)

**Default Roles:**
- ADMIN (full)
- DEVELOPER (manage collections, config)
- USER (data usage)
- (Extensible)

**7.3 Multi-Tenancy** (optional but recommended for hosted model)
- Tenant identifier from JWT claim or header
- Row-level isolation:
  - `tenant_id` column per collection table (default)
  - enforced in query builder automatically

---

### 8. Observability and Operations

**8.1 Logging**
- Structured JSON logs
- Correlation IDs:
  - traceId, spanId, requestId
- Include actor identity (subject claim), tenantId, route, collection, version

**8.2 Metrics**
- Prometheus format via Spring Actuator + Micrometer
- Required metrics:
  - request latency by route
  - DB query latency
  - filter/sort usage counts
  - cache hit ratio (Redis + in-memory)
  - migration durations
  - authz denials count

**8.3 Tracing**
- OpenTelemetry instrumentation
- Export to OTLP (Jaeger/Tempo/etc.)

**8.4 Health Checks**
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
  - DB connectivity
  - Kafka connectivity
  - Redis connectivity
  - config sync status (must be on current version)

---

### 9. React UI Specification

**9.1 UI Bootstrapping (self-configured)**

UI requires only:
- `SERVER_BASE_URL`

On load:
- `GET /ui/config/bootstrap`
  - branding
  - enabled modules
  - menus/pages available for user roles
  - API discovery endpoint references

**9.2 Core UI Modules**

1. **Collections Designer**
   - Create/update collections
   - Add/remove fields
   - Set validation (min/max/length/pattern/nullable/default)
   - Define indexes/uniques
   - Enable/disable operations
   - Preview OpenAPI schema

2. **Authorization Manager**
   - Create roles
   - Assign route-level perms per collection
   - Assign field-level read/write perms
   - Assign UI page/menu access

3. **UI Builder**
   - Define pages:
     - table page
     - detail page
     - form page
     - custom layout page (composition)
   - Menu builder:
     - top menu items
     - left-side context menus based on top selection
     - visibility rules by role
   - Page routing:
     - pageId, path, layout, widgets, bindings to resources

4. **Import/Export**
   - Export collection definition + policies + pages as a package (JSON)
   - Import into another environment with mapping/validation
   - Show diff before apply

5. **Schema/Data Migration Tools**
   - Promote config packages between dev/qa/stage/prod
   - Optionally include data snapshots/seed data
   - Track applied versions

6. **Module/Plugin Manager**
   - Install/enable custom backend modules (constraints apply)
   - Register custom UI components/page widgets (front-end plugins)

**9.3 Reusable React Components (required)**

1. **DataTable**
   - Configurable columns
   - Filters UI for supported ops
   - Sorting on any displayed column
   - Pagination controls
   - Saved views (optional)

2. **ResourceForm**
   - Create/update/delete resource
   - Field validations from schema
   - Field-level auth aware (hide/disable)

3. **ResourceLayout**
   - Arrange multiple panels/widgets bound to resources
   - Links to user-defined pages

4. **Navigation Components**
   - Top menu + left subnav
   - Role-based visibility

All UI configuration is stored in DB and editable via UI.

---

### 10. TypeScript SDK Wrapper

**10.1 Discovery**
- `GET /api/_meta/resources`
  - list collections
  - fields, types, validation
  - supported operations
  - authz hints (what current user can do)

**10.2 SDK Features**
- `client.resource("customers").list({ filters, sort, page, fields })`
- `get(id)`
- `create(payload)`
- `update(id, payload)` (PUT)
- `patch(id, partial)`
- `delete(id)`
- Strong typing generated at build time optionally using OpenAPI + codegen
- Runtime-safe mode for fully dynamic (since collections can be created at runtime)

---

### 11. Extensibility Model

**11.1 Backend Extension Points (interfaces)**
- Storage adapter: table/jsonb/custom
- Validation engine: default + custom validators
- AuthZ provider: RBAC default; allow ABAC later
- Event publishing hooks
- Custom routes:
  - Implement common interface:
    - define route
    - input/output schemas
    - authz requirements
    - optional UI configuration contributions

**11.2 Delivery of Custom Code Modules**

Two supported paths:

**A) Git-Based Deployment** (recommended for enterprise)
- Repo contains:
  - custom backend modules
  - optional UI plugin package
- CI builds images; helm deploy to envs

**B) UI-based Module Upload** (hosted model)
- Restricted sandboxing policy required:
  - only signed modules
  - explicit enable/disable
  - versioning and rollback
- Operational guardrails:
  - resource limits
  - runtime isolation (separate classloader or sidecar)

---

### 12. Microservice Packaging & Domain Services

**12.1 Service Composition**
- A deployment may include:
  - `emf-control-plane`
  - `emf-domain-<domainName>` services (one or many)
  - `emf-ui` (static web app)
- Domain service can own:
  - its own collections
  - custom modules
  - domain-specific workflows/events

**12.2 Configuration Propagation**
- Control plane is source of truth
- Kafka topics:
  - `config.collection.changed`
  - `config.authz.changed`
  - `config.ui.changed`
- Domain services subscribe and apply config live

---

### 13. Schema & Data Migration Between Environments

**13.1 Requirements**
- Promote schema + UI config + auth policies between environments
- Promote data optionally (seed/reference/master data)
- Safe + repeatable + auditable

**13.2 "Package" Format**

`emf-package.json` includes:
- collections + versions
- authz roles/policies
- UI pages/menus
- OIDC provider allowlist config (optional; environment-specific override support)
- optional data payloads

**13.3 Tooling**
- CLI tool: `emfctl`
  - `emfctl export --service X --out pkg.json`
  - `emfctl import --in pkg.json --dry-run`
  - `emfctl apply --in pkg.json`
  - `emfctl promote dev -> qa` (using configured endpoints)
- Server endpoints also exist to support UI-based promotion flows

---

### 14. Testing Strategy (3 Layers)

**14.1 Unit Tests** (pre-merge)
- Pure Java unit tests (JUnit 5)
- Mock external dependencies

**14.2 Local Integration Tests** (pre-merge, containerized)
- Runs with Docker Compose/Testcontainers:
  - Postgres
  - Kafka
  - Redis
  - Service container
- Validates:
  - schema migrations
  - dynamic collection creation
  - authz enforcement
  - filtering/sorting/paging correctness

**14.3 End-to-End Tests** (post-deploy)
- Runs against deployed environment
- Includes UI flows:
  - create collection → CRUD data → modify schema → verify no downtime
  - role-based menu visibility + field masking
  - import/export across environments

---

### 15. Runtime Availability / No Restart Requirement

**15.1 Requirements**
- Creating/modifying collections must not require restart
- No disruption in service

**15.2 Mechanisms**
- Routing is dynamic:
  - A base controller handles generated routes and delegates to runtime registry
  - Registry stores:
    - compiled schema
    - query builders
    - validators
    - authz policy maps
- Updates are atomic:
  - build new registry snapshot → swap reference (copy-on-write)
- Migrations:
  - Apply online where possible
  - For destructive changes:
    - require compatibility plan (e.g., add new column first, backfill, switch reads/writes)

---

### 16. Kubernetes Optimization

**16.1 Required K8s Features**
- Readiness/liveness probes via Actuator
- Horizontal Pod Autoscaling friendly metrics
- Stateless services (config in DB/Kafka)
- Graceful shutdown:
  - stop accepting traffic
  - complete inflight requests
  - commit Kafka offsets safely

**16.2 Helm Chart Defaults**
- resource requests/limits
- env vars for:
  - DB, Kafka, Redis
  - OIDC issuer allowlist
  - service identity
- secrets management compatible (K8s secrets / external secret operator)

---

### 17. Core REST Endpoints (Control Plane)

**Collections**
- `GET /control/collections`
- `POST /control/collections`
- `GET /control/collections/{id}`
- `PUT /control/collections/{id}`
- `POST /control/collections/{id}/export`
- `POST /control/collections/import`

**AuthZ / Roles**
- `GET /control/roles`
- `POST /control/roles`
- `PUT /control/policies`

**UI Config**
- `GET /ui/config/bootstrap`
- `GET /ui/pages`
- `POST /ui/pages`
- `PUT /ui/pages/{id}`
- `PUT /ui/menus/top`
- `PUT /ui/menus/side`

**Resource Discovery**
- `GET /api/_meta/resources`
- `GET /openapi.json`

---

### 18. Opinionated Defaults

The framework should "just work" with minimal config:
- Postgres physical tables by default
- CRUD + query features enabled automatically
- OpenAPI always enabled
- JWT validation always enabled (requires configuring issuers)
- Default roles and admin bootstrap
- Actuator + metrics + tracing enabled
- Kafka lifecycle events enabled (configurable)

---

### 19. Open Source / Hosted Service Considerations

**19.1 OSS Core**
- Core runtime, UI builder, SDK, default adapters

**19.2 Hosted Offering Adds**
- Multi-tenant management UI
- Billing hooks
- Automated backups and restore
- Signed plugin marketplace
- SLA monitoring and incident tooling

---

### 20. Deliverables and Milestones (suggested)

1. **MVP Runtime CRUD + Collection Designer**
   - Create collection → physical table → CRUD endpoints → OpenAPI

2. **AuthN/AuthZ + Field masking**

3. **UI Builder + menus + pages**

4. **Import/Export + env promotion tooling**

5. **Plugin system**

6. **Hardening for hosted 24/7 ops**
   - multi-tenancy, backups, rate limiting, audit trails
