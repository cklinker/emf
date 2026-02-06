# EMF Platform Specifications

## Table of Contents

- [Part 1: Current System Implementation](#part-1-current-system-implementation)
  - [1. Platform Overview](#1-platform-overview)
  - [2. Architecture](#2-architecture)
  - [3. Technology Stack](#3-technology-stack)
  - [4. Control Plane Service](#4-control-plane-service)
  - [5. API Gateway](#5-api-gateway)
  - [6. Runtime Framework](#6-runtime-framework)
  - [7. Frontend](#7-frontend)
  - [8. Infrastructure](#8-infrastructure)
  - [9. Current Limitations](#9-current-limitations)
- [Part 2: Enterprise Platform Functional Specification](#part-2-enterprise-platform-functional-specification)
  - [10. Vision and Goals](#10-vision-and-goals)
  - [11. Multi-Tenancy](#11-multi-tenancy)
  - [12. Metadata-Driven Object System](#12-metadata-driven-object-system)
  - [13. Workflow and Automation Engine](#13-workflow-and-automation-engine)
  - [14. Formula and Calculated Fields](#14-formula-and-calculated-fields)
  - [15. Role and Permission System](#15-role-and-permission-system)
  - [16. UI Framework and Page Builder](#16-ui-framework-and-page-builder)
  - [17. Reporting and Analytics](#17-reporting-and-analytics)
  - [18. Integration Platform](#18-integration-platform)
  - [19. Scripting and Extensibility](#19-scripting-and-extensibility)
  - [20. Data Import/Export and ETL](#20-data-importexport-and-etl)
  - [21. Audit, Compliance, and Governance](#21-audit-compliance-and-governance)
  - [22. Search and Querying](#22-search-and-querying)
  - [23. Notification System](#23-notification-system)
  - [24. File and Document Management](#24-file-and-document-management)
  - [25. Sandbox and Environment Management](#25-sandbox-and-environment-management)
  - [26. Marketplace and Plugin System](#26-marketplace-and-plugin-system)
  - [27. Migration Roadmap](#27-migration-roadmap)

---

# Part 1: Current System Implementation

## 1. Platform Overview

EMF (Enterprise Microservice Framework) is a monorepo platform for building, deploying, and managing domain microservices with dynamic schema management, multi-service authorization, and JSON:API support. It provides a control plane for centralized configuration, an API gateway for ingress and security, shared runtime libraries, and a React-based admin UI.

### 1.1 Repository Structure

```
emf/
  emf-platform/                 Core runtime libraries (Maven artifacts)
    runtime/
      runtime-core/             Collection definitions, query engine, storage adapters
      runtime-events/           Shared Kafka event classes
  emf-control-plane/            Central configuration management service
    app/                        Spring Boot application
  emf-gateway/                  API Gateway (auth, routing, rate limiting, JSON:API)
  emf-ui/                       React admin/builder UI
    app/
  emf-web/                      TypeScript SDK + React component packages
    packages/
      sdk/                      TypeScript API client
      components/               Reusable React components
      plugin-sdk/               Plugin development SDK
  sample-service/               Example domain service
  emfctl/                       CLI tool (placeholder, not yet implemented)
  emf-helm/                     Kubernetes Helm charts
    charts/
      emf-control-plane/
  docker/                       Docker build configs, Keycloak realm, Postgres init
  scripts/                      Dev and test utility scripts
  .github/workflows/            CI/CD pipeline
```

### 1.2 Version Information

| Component | Version |
|-----------|---------|
| EMF Platform | 1.0.0-SNAPSHOT |
| Java | 21 |
| Spring Boot (Control Plane) | 3.2.2 |
| Spring Boot (Gateway) | 3.2.1 |
| Spring Cloud | 2023.0.0 |
| PostgreSQL | 15 |
| Apache Kafka | 3.7.0 (KRaft mode) |
| Redis | 7 |
| Keycloak | 23.0 |

---

## 2. Architecture

### 2.1 High-Level Architecture

```
                                 Internet / Clients
                                        |
                               +--------v--------+
                               |   API Gateway   |   (Spring Cloud Gateway)
                               |  - JWT Auth     |   Port 8080
                               |  - Rate Limit   |
                               |  - JSON:API     |
                               |  - Routing      |
                               +---+----+----+---+
                                   |    |    |
                    +--------------+    |    +--------------+
                    |                   |                   |
           +--------v------+  +--------v--------+  +------v--------+
           | Control Plane |  | Domain Service 1|  | Domain Service N|
           | Port 8081     |  | (sample-service)|  |               |
           +-------+-------+  +--------+--------+  +------+--------+
                   |                   |                   |
         +---------+---------+---------+---------+---------+
         |                   |                   |
    +----v----+        +-----v-----+       +-----v-----+
    |Postgres |        |   Kafka   |       |   Redis   |
    |  :5432  |        |   :9094   |       |   :6379   |
    +---------+        +-----------+       +-----------+
```

### 2.2 Communication Patterns

- **Synchronous:** REST/HTTP between gateway and services, gateway and control plane
- **Asynchronous:** Kafka events for configuration propagation (control plane -> gateway, control plane -> domain services)
- **Caching:** Redis for JWKS keys, rate limiting state, and authorization config

### 2.3 Key Architectural Patterns

- **API Gateway Pattern** - Single entry point for all client traffic
- **Event-Driven Architecture** - Kafka for inter-service configuration updates
- **Database-per-Service** - Each domain service owns its data store
- **CQRS** - Control plane manages schema (write), domain services execute queries (read)
- **Immutable Versioning** - Collection schemas are versioned; every change creates a new snapshot
- **Soft Deletes** - Resources are marked inactive, never permanently deleted

---

## 3. Technology Stack

### 3.1 Backend (Java 21)

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.2.x |
| Gateway | Spring Cloud Gateway (reactive/Netty) |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Messaging | Spring Kafka |
| Caching | Spring Data Redis |
| Security | Spring Security + OAuth2 Resource Server (JWT) |
| Monitoring | Spring Actuator + Micrometer + OpenTelemetry |
| API Docs | springdoc-openapi (OpenAPI 3.1 + Swagger UI) |
| Logging | Logback + Logstash JSON encoder |
| Testing | JUnit 5, Mockito, jqwik (property-based), Testcontainers, H2 |

### 3.2 Frontend (TypeScript)

| Layer | Technology |
|-------|-----------|
| Framework | React 18.2 |
| Language | TypeScript 5.3 |
| Build | Vite |
| HTTP | Axios |
| Forms | React Hook Form |
| Validation | Zod |
| Server State | TanStack React Query |
| Testing | Vitest + Testing Library |
| Linting | ESLint + Prettier |

### 3.3 Infrastructure

| Concern | Technology |
|---------|-----------|
| Containers | Docker |
| Orchestration | Kubernetes (Helm charts) |
| Identity Provider | Keycloak (OIDC) |
| Database | PostgreSQL 15 |
| Message Broker | Apache Kafka 3.7.0 (KRaft) |
| Cache/Rate Limit | Redis 7 |
| CI/CD | GitHub Actions |

---

## 4. Control Plane Service

The control plane is the central configuration management service. It manages collections (dynamic schemas), fields, services, authorization, OIDC providers, UI configuration, and environment promotion packages.

### 4.1 Domain Entities

#### Service
Represents a registered domain microservice.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| name | String | Unique identifier |
| displayName | String | Human-readable name |
| description | String | Free-text |
| basePath | String | Default: `/api` |
| environment | String | development / staging / production |
| databaseUrl | String | JDBC connection string |
| active | Boolean | Soft-delete flag |
| createdAt | Timestamp | |
| updatedAt | Timestamp | |

#### Collection
A dynamic data entity definition, analogous to a database table or Salesforce custom object.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| serviceId | UUID | FK to Service |
| name | String | Unique identifier |
| displayName | String | Human-readable |
| description | String | |
| path | String | API path segment |
| storageMode | Enum | PHYSICAL_TABLE or JSONB_DOCUMENT |
| active | Boolean | Soft-delete |
| currentVersion | Integer | Increments on schema changes |
| createdAt | Timestamp | |
| updatedAt | Timestamp | |

**Relationships:** belongs to Service, has many Fields, has many CollectionVersions, has many RoutePolicies.

#### Field
An attribute definition on a collection, analogous to a column or Salesforce custom field.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| collectionId | UUID | FK to Collection |
| name | String | Unique per collection |
| displayName | String | |
| type | Enum | TEXT, INTEGER, BOOLEAN, DECIMAL, DATE, TIMESTAMP, REFERENCE, ARRAY |
| required | Boolean | |
| unique | Boolean | |
| indexed | Boolean | |
| defaultValue | JSONB | |
| referenceTarget | String | Target collection name (for REFERENCE type) |
| constraints | JSONB | min, max, pattern, etc. |
| order | Integer | Display order |
| active | Boolean | |
| description | String | |

#### CollectionVersion
Immutable snapshot of a collection schema at a point in time.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| collectionId | UUID | FK |
| version | Integer | Sequential |
| schema | JSONB | Full schema snapshot |
| createdAt | Timestamp | |

#### Role
Named role for RBAC.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| name | String | Unique |
| description | String | |

#### Policy
Authorization policy with an expression language.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Primary key |
| name | String | Unique |
| description | String | |
| expression | Text | CEL/SpEL expression |
| rules | JSONB | Structured rules |

#### RoutePolicy
Binds a Policy to a Collection + HTTP operation.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | PK |
| collectionId | UUID | FK |
| operation | String | GET, POST, PUT, PATCH, DELETE, LIST |
| policyId | UUID | FK |

#### FieldPolicy
Binds a Policy to a Field for read/write control.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | PK |
| fieldId | UUID | FK |
| policyId | UUID | FK |

#### OidcProvider
External OpenID Connect provider configuration.

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | PK |
| name | String | Unique |
| issuer | String | Unique, issuer URI |
| jwksUri | String | JWKS endpoint |
| clientId | String | |
| audience | String | Expected audience |
| rolesClaim | String | JWT claim for roles |
| rolesMapping | Text | JSON mapping external -> EMF roles |
| emailClaim | String | Default: `email` |
| usernameClaim | String | Default: `preferred_username` |
| nameClaim | String | Default: `name` |
| active | Boolean | |

#### UiPage / UiMenu / UiMenuItem
Admin UI page definitions, navigation menus, and their items. Pages store layout configuration as JSONB.

#### ConfigPackage / PackageItem
Bundles of configuration for environment promotion (export from dev, import to staging/prod).

#### MigrationRun / MigrationStep
Tracks schema migration planning and execution with step-level status.

### 4.2 REST API Endpoints

#### Service Management (`/control/services`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/services` | List services (paginated, filterable, sortable) |
| POST | `/control/services` | Create service |
| GET | `/control/services/{id}` | Get service by ID |
| PUT | `/control/services/{id}` | Update service |
| DELETE | `/control/services/{id}` | Soft-delete service |

#### Collection Management (`/control/collections`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/collections` | List collections (paginated, filterable) |
| POST | `/control/collections` | Create collection + version 1 |
| GET | `/control/collections/{id}` | Get collection by ID |
| PUT | `/control/collections/{id}` | Update collection, bump version |
| DELETE | `/control/collections/{id}` | Soft-delete collection |

#### Field Management (nested under collections)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/collections/{id}/fields` | List active fields |
| POST | `/control/collections/{id}/fields` | Add field, bump collection version |
| GET | `/control/collections/{id}/fields/{fid}` | Get field |
| PUT | `/control/collections/{id}/fields/{fid}` | Update field, bump version |
| DELETE | `/control/collections/{id}/fields/{fid}` | Deactivate field, bump version |

#### Authorization (`/control`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/roles` | List roles |
| POST | `/control/roles` | Create role |
| GET | `/control/policies` | List policies |
| POST | `/control/policies` | Create policy |
| POST | `/control/collections/{id}/authorization` | Set route + field policies |
| GET | `/control/collections/{id}/authorization` | Get authorization config |

#### OIDC Providers (`/control/oidc/providers`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/oidc/providers` | List OIDC providers |
| POST | `/control/oidc/providers` | Add provider |
| GET | `/control/oidc/providers/{id}` | Get provider |
| PUT | `/control/oidc/providers/{id}` | Update provider |
| DELETE | `/control/oidc/providers/{id}` | Deactivate provider |

#### UI Configuration (`/ui`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ui/config/bootstrap` | Bootstrap config (no auth required) |
| GET | `/ui/pages` | List pages |
| POST | `/ui/pages` | Create page |
| PUT | `/ui/pages/{id}` | Update page |
| GET | `/ui/menus` | List menus |
| PUT | `/ui/menus/{id}` | Update menu |

#### Package Management (`/control/packages`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/control/packages/history` | Export/import history |
| POST | `/control/packages/export` | Export config to package |
| POST | `/control/packages/import/preview` | Dry-run import preview |
| POST | `/control/packages/import` | Apply package import |

#### Schema Migrations (`/control/migrations`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/control/migrations/plan` | Generate migration steps |
| GET | `/control/migrations/history` | Migration history |
| GET | `/control/migrations/{id}` | Migration run details |

#### Discovery (`/api/_meta`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/_meta/resources` | All active collections with metadata |

#### Admin Dashboard (`/api/_admin`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/_admin/dashboard` | System health, metrics, recent errors |

### 4.3 Event Publishing

The control plane publishes Kafka events on configuration changes:

| Topic | Trigger |
|-------|---------|
| `emf.config.collection.changed` | Collection CRUD |
| `emf.config.field.changed` | Field changes (with version snapshot) |
| `emf.config.authorization.changed` | Policy/role changes |
| `emf.config.oidc.changed` | OIDC provider changes |
| `emf.config.ui.changed` | UI configuration changes |

All events contain: `eventId`, `eventType`, `correlationId`, `timestamp`, `payload`.

### 4.4 Database Migrations

Seven Flyway migrations establish the schema:

1. `V1` - Core tables (collection, field, collection_version, field_version, role, policy, route_policy, field_policy, oidc_provider, ui_page, ui_menu, ui_menu_item)
2. `V2` - Seed local Keycloak OIDC provider
3. `V3` - Seed default UI menus
4. `V4` - Add missing field columns
5. `V5` - Add service table, add service_id FK to collection
6. `V6` - Add OIDC claim mapping columns
7. `V7` - Add collection path column

---

## 5. API Gateway

The gateway is a Spring Cloud Gateway (reactive/Netty) application that serves as the single ingress point.

### 5.1 Implemented Capabilities

| Capability | Implementation |
|-----------|----------------|
| JWT Authentication | `JwtAuthenticationFilter` - validates tokens against OIDC provider JWKS |
| Principal Extraction | `PrincipalExtractor` - extracts sub, email, username, roles from JWT |
| Route Authorization | `RouteAuthorizationFilter` - enforces route-level policies per HTTP method |
| Field Authorization | `FieldAuthorizationFilter` - strips unauthorized fields from responses, blocks writes to protected fields |
| Rate Limiting | `RateLimitFilter` + `RedisRateLimiter` - token bucket per user, backed by Redis |
| Dynamic Routing | `DynamicRouteLocator` + `RouteRegistry` - routes built from control plane config |
| JSON:API Processing | `JsonApiParser`, `JsonApiDocument`, `ResourceObject`, `IncludeResolver` |
| Header Transformation | `HeaderTransformationFilter` - X-Request-ID, X-Correlation-ID, user info headers |
| Request Logging | `RequestLoggingFilter` - structured logging of all requests |
| Config Sync | `ConfigEventListener` - Kafka consumer updates routes, auth cache on changes |
| Health Checks | Custom indicators for Control Plane, Kafka, Redis connectivity |
| Error Handling | `GlobalErrorHandler` - consistent error responses |

### 5.2 Route Registry

The `RouteRegistry` maintains an in-memory, thread-safe map of route definitions, each containing:
- Path pattern (e.g., `/api/v1/service/collection/**`)
- Target service URI
- Allowed HTTP methods
- Rate limit configuration (requests/minute/user)
- Auth required flag

Routes are loaded from the control plane at startup via `RouteInitializer` and updated in real time through Kafka events.

### 5.3 JSON:API Support

The gateway implements JSON:API (jsonapi.org) document structure:
- `JsonApiDocument` - top-level container with data, included, links, meta, errors
- `ResourceObject` - id, type, attributes, relationships, links, meta
- `Relationship` / `ResourceIdentifier` - resource linkage
- `IncludeResolver` - resolves `?include=` parameters for related resources
- Full round-trip serialization fidelity (tested with `JsonApiRoundTripTest`)

---

## 6. Runtime Framework

### 6.1 Runtime Core (`emf-platform/runtime/runtime-core`)

A library providing the building blocks for domain services:

**CollectionDefinition** (Java record) - Immutable in-memory schema:
- name, displayName, description
- `List<FieldDefinition>` fields
- `StorageConfig` (PHYSICAL_TABLE or JSONB_DOCUMENT, table name, schema, PK column)
- `ApiConfig` (base path, enabled operations, page size)
- `AuthzConfig` (required roles, field-level policies)
- `EventsConfig` (Kafka enabled, topic, publish-on triggers)
- version, timestamps

**FieldType** enum: TEXT, INTEGER, BOOLEAN, DECIMAL, DATE, TIMESTAMP, REFERENCE, ARRAY, JSON, UUID

**StorageAdapter** interface with two implementations:
- `PhysicalTableStorageAdapter` - traditional RDBMS tables
- `JsonbStorageAdapter` - PostgreSQL JSONB document storage

**SchemaMigrationEngine** - handles column adds/removes, type conversions, reference constraints.

**Builder pattern** - `CollectionDefinitionBuilder` and `FieldDefinitionBuilder` for fluent construction.

### 6.2 Runtime Events (`emf-platform/runtime/runtime-events`)

Shared event model used by all services:
- `ConfigEvent<T>` - generic event envelope
- `CollectionChangedPayload`, `ServiceChangedPayload`, `AuthzChangedPayload`
- `ChangeType` enum: CREATED, UPDATED, DELETED
- `EventFactory` for constructing typed events

### 6.3 Sample Service

Demonstrates domain service implementation:
- Registers with control plane at startup
- Subscribes to Kafka config events
- Maintains in-memory collection definition cache
- Executes CRUD via `EventPublishingQueryEngine`
- Exposes test endpoints under `/test/collections`

---

## 7. Frontend

### 7.1 TypeScript SDK (`emf-web/packages/sdk`)

- Axios-based HTTP client with interceptors
- Auto-generated types from OpenAPI spec (`control-plane-api.yaml`)
- Service classes for each API domain (collections, fields, auth, OIDC, packages, etc.)
- JSON:API request/response transformers
- Error handling, retry logic, timeout configuration

### 7.2 React Components (`emf-web/packages/components`)

Reusable components for:
- Collection and field editors
- Authorization policy builder
- OIDC provider configuration
- UI page builder
- Menu editor
- Package import/export
- Migration planner/executor
- Dashboard widgets

### 7.3 Plugin SDK (`emf-web/packages/plugin-sdk`)

- Plugin interface definitions
- Component registration system
- Extension hooks
- Plugin lifecycle management

### 7.4 Admin UI (`emf-ui/app`)

React + TypeScript + Vite application providing:
- Service management
- Collection/field schema design
- Authorization configuration
- OIDC provider setup
- UI page and menu builder
- Package export/import
- Migration planning and execution
- System health dashboard

---

## 8. Infrastructure

### 8.1 Docker Compose (Local Development)

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| postgres | postgres:15-alpine | 5432 | Primary database |
| redis | redis:7-alpine | 6379 | Cache + rate limiting |
| kafka | apache/kafka:3.7.0 | 9094 | Event streaming |
| keycloak | keycloak:23.0 | 8180 | OIDC identity provider |
| emf-control-plane | custom build | 8081 | Configuration management |
| emf-gateway | custom build | 8080 | API gateway |
| sample-service | custom build | 8082 | Example domain service |
| kafka-ui | provectuslabs/kafka-ui | 8090 | Debug tool (optional) |
| redis-commander | redis-commander | 8091 | Debug tool (optional) |
| pgadmin | pgadmin4 | 8092 | Debug tool (optional) |

### 8.2 Helm Charts

Kubernetes deployment charts for `emf-control-plane` with:
- Deployments, Services, ConfigMaps, Secrets
- Ingress configuration
- Health/readiness probes
- Resource limits and scaling
- Optional StatefulSets for Postgres, Redis, Kafka

### 8.3 CI/CD (GitHub Actions)

Pipeline: Checkout -> Java/Node setup -> Validate -> Build -> Test (unit + integration with Testcontainers) -> Docker Build -> Publish -> Deploy

### 8.4 CLI Tool (`emfctl`)

Placeholder only. Intended for environment promotion and migration operations from the command line. Not yet implemented.

---

## 9. Current Limitations

These are gaps in the current implementation that the enterprise specification (Part 2) addresses:

| Area | Limitation |
|------|-----------|
| Multi-Tenancy | No tenant isolation; single-tenant only |
| Workflows | No workflow engine; no approval processes or automation |
| Formulas | No calculated or formula fields |
| Relationships | REFERENCE type exists but no cascading, no many-to-many, no self-referencing |
| Validation | Basic field constraints only (min, max, pattern); no cross-field or cross-record rules |
| UI Builder | Page config is raw JSONB; no drag-and-drop, no conditional visibility |
| Reporting | No report builder, no dashboards beyond system health |
| Search | No full-text search; no cross-collection querying |
| Notifications | No email, SMS, or in-app notification system |
| File Storage | No file/attachment support on records |
| Audit Trail | Timestamps only; no field-level change history |
| Scripting | No server-side scripting; no triggers or hooks |
| Integration | No webhook or external integration system |
| Import/Export | Config packages only; no data import/export (CSV, etc.) |
| Sandbox | No environment cloning or sandbox management |
| Marketplace | Plugin SDK exists but no marketplace or distribution |
| Localization | No i18n support |
| Versioning/Rollback | Collection versions exist but no rollback mechanism |
| API Limits | Rate limiting is per-user only; no per-tenant or per-org quotas |

---

# Part 2: Enterprise Platform Functional Specification

This section specifies the features needed to evolve EMF into a fully configurable enterprise application platform comparable to NetSuite or Salesforce. Each section maps capabilities to existing EMF concepts where applicable.

---

## 10. Vision and Goals

### 10.1 Vision

EMF becomes a **metadata-driven enterprise application platform** where:
- Administrators define data models, business rules, UI layouts, workflows, and integrations entirely through configuration -- no code required for standard operations.
- Developers extend the platform through a scripting engine, plugin system, and integration APIs for advanced requirements.
- Organizations operate isolated tenants with independent configurations, data, and user bases on shared infrastructure.

### 10.2 Design Principles

1. **Configuration over Code** - Every business concept (objects, fields, rules, layouts, workflows, reports) is defined as metadata and can be changed at runtime without deployment.
2. **Tenant Isolation** - Each tenant operates in a fully isolated namespace with independent schema, security, and data.
3. **Extensibility at Every Layer** - Server-side scripts, client-side plugins, webhooks, and APIs at every extension point.
4. **Auditability** - Every data change, configuration change, and user action is tracked.
5. **Governor Limits** - Enforce resource quotas per tenant to ensure platform stability.

### 10.3 Mapping to Current EMF Concepts

| Enterprise Concept | Current EMF Equivalent | Gap |
|-------------------|----------------------|-----|
| Custom Object | Collection | Needs relationships, validation rules, triggers |
| Custom Field | Field | Needs formula fields, roll-up summaries, picklists |
| Profile/Permission Set | Role + Policy | Needs object/field/record-level permissions, sharing rules |
| Page Layout | UiPage (JSONB config) | Needs drag-and-drop builder, conditional visibility |
| Workflow Rule | (none) | Entire subsystem needed |
| Report | (none) | Entire subsystem needed |
| Integration | (none) | Webhooks, REST connectors, middleware |
| App / Module | Service | Needs app packaging, installation, dependencies |

---

## 11. Multi-Tenancy

### 11.1 Tenant Model

```
Platform
  +-- Organization (tenant)
        +-- Users
        +-- Roles / Permission Sets
        +-- Custom Objects (Collections)
        +-- Apps (installed modules)
        +-- Configurations
        +-- Data (fully isolated)
```

#### 11.1.1 Tenant Entity

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| slug | String | URL-safe identifier (e.g., `acme-corp`) |
| name | String | Display name |
| edition | Enum | FREE, PROFESSIONAL, ENTERPRISE, UNLIMITED |
| status | Enum | ACTIVE, SUSPENDED, PROVISIONING, DECOMMISSIONED |
| settings | JSONB | Tenant-level configuration |
| limits | JSONB | Governor limits (API calls/day, storage GB, users, etc.) |
| createdAt | Timestamp | |

#### 11.1.2 Data Isolation Strategy

- **Schema-per-tenant** for PostgreSQL: each tenant gets its own database schema (`tenant_<slug>`).
- Tenant context resolved from JWT claim (`org_id`) or subdomain (`acme.emf.app`).
- Connection routing via `AbstractRoutingDataSource` or schema switching (`SET search_path`).
- All queries scoped to tenant automatically via Hibernate filters or interceptors.

#### 11.1.3 Tenant Provisioning

1. Create tenant record in platform schema.
2. Create database schema.
3. Run Flyway migrations for tenant schema.
4. Seed default roles, permissions, and admin user.
5. Install default apps/modules.
6. Send welcome email to tenant admin.

### 11.2 User Management

#### 11.2.1 User Entity

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | FK |
| email | String | Unique per tenant |
| username | String | |
| firstName | String | |
| lastName | String | |
| status | Enum | ACTIVE, INACTIVE, LOCKED, PENDING_ACTIVATION |
| locale | String | e.g., `en_US` |
| timezone | String | e.g., `America/New_York` |
| profileId | UUID | FK to Profile |
| permissionSets | List<UUID> | Additional permissions |
| lastLoginAt | Timestamp | |
| loginCount | Integer | |
| mfaEnabled | Boolean | |

#### 11.2.2 Authentication

- **SSO via OIDC** - Existing OIDC provider integration (extend to per-tenant provider config).
- **Local authentication** - Username/password with bcrypt hashing (new).
- **MFA** - TOTP (Google Authenticator) and SMS-based (new).
- **Session management** - Configurable session timeout, concurrent session limits.
- **Password policies** - Minimum length, complexity, rotation, history.

---

## 12. Metadata-Driven Object System

### 12.1 Enhanced Collection (Custom Object) Definition

Extend the current Collection entity with:

| New Field | Type | Description |
|-----------|------|-------------|
| tenantId | UUID | Tenant ownership |
| pluralLabel | String | e.g., "Accounts" |
| recordName | String | Field used as the record's display name |
| enableHistory | Boolean | Track field-level change history |
| enableSearch | Boolean | Include in global search index |
| enableSharing | Boolean | Enable record-level sharing rules |
| enableApprovals | Boolean | Enable approval workflows |
| enableActivities | Boolean | Enable tasks, events, notes |
| enableAttachments | Boolean | Enable file attachments |
| deploymentStatus | Enum | IN_DEVELOPMENT, DEPLOYED, DEPRECATED |
| icon | String | Icon identifier for UI |
| category | String | Grouping category (Sales, Finance, HR, etc.) |

### 12.2 Enhanced Field Types

Extend the current FieldType enum:

| New Type | Description |
|----------|-------------|
| PICKLIST | Single-select from defined values |
| MULTI_PICKLIST | Multi-select from defined values |
| FORMULA | Calculated from other fields (read-only) |
| ROLLUP_SUMMARY | Aggregate child records (COUNT, SUM, MIN, MAX) |
| AUTO_NUMBER | Auto-incrementing formatted string (e.g., `INV-{0000}`) |
| CURRENCY | Decimal with currency code |
| PERCENT | Decimal displayed as percentage |
| PHONE | Phone number with formatting |
| EMAIL | Email with validation |
| URL | URL with validation |
| RICH_TEXT | HTML content |
| ENCRYPTED | Encrypted at rest (PII, sensitive data) |
| LOOKUP | Relationship to another object (current REFERENCE, enhanced) |
| MASTER_DETAIL | Parent-child relationship with cascading delete |
| GEOLOCATION | Latitude/longitude pair |
| EXTERNAL_ID | Unique identifier from external system |
| POLYMORPHIC_LOOKUP | Lookup that can reference multiple object types |

### 12.3 Picklist Management

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| fieldId | UUID | FK to Field |
| values | JSONB | `[{value, label, isDefault, isActive, sortOrder, color}]` |
| isRestricted | Boolean | Only admin can add values |
| isDependentOn | UUID | FK to controlling picklist field |
| dependencyMapping | JSONB | Controlling value -> available dependent values |

### 12.4 Relationship Types

| Type | Behavior |
|------|----------|
| LOOKUP | Optional reference; no cascade; separate security |
| MASTER_DETAIL | Required reference; cascade delete; roll-up eligible; child inherits parent sharing |
| MANY_TO_MANY | Junction object auto-created; bidirectional navigation |
| SELF_REFERENCING | Lookup to same object (e.g., Employee.managerId -> Employee) |
| EXTERNAL_LOOKUP | Reference to record in external system |
| POLYMORPHIC | Single field references multiple object types (e.g., Activity.whatId -> Account OR Opportunity) |

### 12.5 Validation Rules

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| collectionId | UUID | FK |
| name | String | |
| active | Boolean | |
| formula | Text | Expression that evaluates to true/false |
| errorMessage | String | Shown when validation fails |
| errorField | String | Field to highlight |
| evaluateOn | Enum | CREATE, UPDATE, CREATE_AND_UPDATE |

Example formulas:
```
AND(ISBLANK(Email), ISBLANK(Phone))  -> "Either Email or Phone is required"
CloseDate < TODAY()                   -> "Close Date cannot be in the past"
Amount > 0                            -> "Amount must be positive"
REGEX(ZipCode, "\\d{5}(-\\d{4})?")  -> "Invalid ZIP code format"
```

### 12.6 Record Types

Allow a single collection to have multiple record types, each with:
- Its own page layout
- Its own picklist value subset
- Its own business process (workflow)
- Assigned to specific profiles

---

## 13. Workflow and Automation Engine

### 13.1 Workflow Rules

Declarative automation triggered by record events:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| collectionId | UUID | Which object |
| name | String | |
| active | Boolean | |
| triggerType | Enum | ON_CREATE, ON_UPDATE, ON_CREATE_OR_UPDATE, ON_DELETE |
| filterCriteria | JSONB | Conditions that must be true |
| reevaluate | Boolean | Re-evaluate when criteria no longer met |
| actions | List | Ordered list of actions to execute |

#### 13.1.1 Workflow Actions

| Action Type | Description |
|-------------|-------------|
| FIELD_UPDATE | Set a field value on the current or related record |
| EMAIL_ALERT | Send an email using a template |
| OUTBOUND_MESSAGE | Send SOAP/REST message to external system |
| CREATE_TASK | Create a task record assigned to a user/queue |
| INVOKE_SCRIPT | Execute a server-side script |

### 13.2 Approval Processes

Multi-step approval workflows:

```
Submit for Approval
  -> Step 1: Manager approval (auto-route based on org hierarchy)
  -> Step 2: Finance approval (if Amount > $10,000)
  -> Step 3: VP approval (if Amount > $100,000)
  -> Final: Approved / Rejected
```

| Component | Description |
|-----------|-------------|
| Entry Criteria | Formula determining which records enter the process |
| Approval Steps | Ordered list with assigned approver(s) and criteria |
| Approver Types | User, role, queue, manager hierarchy, related user field |
| Actions | On submit, on approval, on rejection, on recall |
| Parallel Approvals | Multiple approvers at same step (unanimous or first-response) |
| Delegation | Users can delegate approval authority |
| Escalation | Auto-escalate after configurable timeout |

### 13.3 Process Builder / Flow Engine

Visual, low-code automation tool for complex business processes:

| Concept | Description |
|---------|-------------|
| Trigger | Record change, schedule, platform event, button click, API call |
| Decision | Branching logic based on conditions |
| Assignment | Set field values |
| Action | Create/update/delete records, send emails, post to Kafka, call API |
| Loop | Iterate over a collection of records |
| Wait | Pause until a condition is met or time elapses |
| Subflow | Invoke another flow |
| Fault Handler | Error handling and retry logic |

#### 13.3.1 Flow Definition (stored as JSONB)

```json
{
  "name": "Opportunity Close Process",
  "trigger": {"type": "RECORD_UPDATE", "object": "opportunity", "criteria": "Stage = 'Closed Won'"},
  "steps": [
    {"type": "ASSIGNMENT", "target": "opportunity.Status", "value": "'Active'"},
    {"type": "CREATE_RECORD", "object": "invoice", "fields": {"accountId": "{!opportunity.AccountId}", "amount": "{!opportunity.Amount}"}},
    {"type": "EMAIL_ALERT", "template": "deal_closed", "to": "{!opportunity.OwnerId}"},
    {"type": "INVOKE_SCRIPT", "script": "post_close_processing"}
  ]
}
```

### 13.4 Scheduled Jobs

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| name | String | |
| cronExpression | String | Quartz cron |
| scriptId | UUID | Script to execute |
| flowId | UUID | Or flow to execute |
| active | Boolean | |
| lastRunAt | Timestamp | |
| nextRunAt | Timestamp | |
| status | Enum | IDLE, RUNNING, FAILED |

### 13.5 Platform Events

Custom Kafka topics that any automation can publish to or subscribe from:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | |
| name | String | Event name (e.g., `OrderPlaced`) |
| schema | JSONB | Event payload schema |
| retentionHours | Integer | How long events are retained |

---

## 14. Formula and Calculated Fields

### 14.1 Formula Engine

A formula engine that supports:

| Category | Functions |
|----------|----------|
| Text | `CONCATENATE`, `LEFT`, `RIGHT`, `MID`, `LEN`, `TRIM`, `UPPER`, `LOWER`, `SUBSTITUTE`, `TEXT`, `CONTAINS`, `REGEX` |
| Numeric | `ABS`, `CEILING`, `FLOOR`, `ROUND`, `MAX`, `MIN`, `MOD`, `SQRT`, `POWER`, `LOG` |
| Date/Time | `TODAY`, `NOW`, `DATE`, `YEAR`, `MONTH`, `DAY`, `DATEVALUE`, `DATETIMEVALUE`, `ADDMONTHS`, `DATEDIFF` |
| Logical | `IF`, `AND`, `OR`, `NOT`, `CASE`, `ISBLANK`, `ISNULL`, `NULLVALUE`, `BLANKVALUE` |
| Cross-Object | `VLOOKUP` (reference traversal), `PRIORVALUE` (in triggers) |
| Summary | `COUNT`, `SUM`, `MIN`, `MAX`, `AVG` (for roll-up summaries) |

### 14.2 Roll-Up Summary Fields

Defined on the parent side of a MASTER_DETAIL relationship:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| parentFieldId | UUID | The roll-up field on the parent |
| childCollectionId | UUID | The detail object |
| aggregateFunction | Enum | COUNT, SUM, MIN, MAX |
| aggregateField | String | Which child field to aggregate |
| filterCriteria | JSONB | Optional filter on child records |

Example: `Account.TotalRevenue = SUM(Opportunity.Amount WHERE Stage = 'Closed Won')`

---

## 15. Role and Permission System

### 15.1 Permission Model

```
User
  +-- Profile (exactly one)
  |     +-- Object Permissions (CRUD per object)
  |     +-- Field Permissions (read/edit per field)
  |     +-- App Permissions (which apps visible)
  |     +-- System Permissions (admin features)
  |     +-- Tab Permissions (which tabs visible)
  |     +-- Page Layout Assignment (per object per record type)
  +-- Permission Sets (zero or more, additive)
  |     +-- Same structure as Profile, extends permissions
  +-- Permission Set Groups (bundles of permission sets)
```

### 15.2 Object-Level Permissions

| Permission | Description |
|------------|-------------|
| CREATE | Can create records |
| READ | Can see records (subject to sharing) |
| EDIT | Can modify records (subject to sharing) |
| DELETE | Can delete records (subject to sharing) |
| VIEW_ALL | Can see all records regardless of sharing |
| MODIFY_ALL | Can edit/delete all records regardless of sharing |

### 15.3 Field-Level Security

| Permission | Description |
|------------|-------------|
| VISIBLE | Field is visible in UI and API |
| READ_ONLY | Can see but not modify |
| HIDDEN | Field is completely hidden |

Extend the existing FieldPolicy concept with explicit visible/read-only/hidden per profile.

### 15.4 Record-Level Security (Sharing Model)

| Mechanism | Description |
|-----------|-------------|
| Organization-Wide Default (OWD) | Base access level per object: Private, Public Read, Public Read/Write |
| Role Hierarchy | Users in higher roles see records owned by subordinates |
| Sharing Rules | Criteria-based or owner-based rules granting access to groups |
| Manual Sharing | Record owner grants access to specific users/groups |
| Teams | Groups of users with shared access to a set of records |
| Territory Management | Hierarchical territory-based access (for sales orgs) |

### 15.5 System Permissions

| Permission | Description |
|------------|-------------|
| MANAGE_USERS | Create/edit/deactivate users |
| CUSTOMIZE_APPLICATION | Modify objects, fields, layouts |
| MANAGE_SHARING | Configure sharing rules |
| MANAGE_WORKFLOWS | Create/edit workflows and flows |
| MANAGE_REPORTS | Create public reports and dashboards |
| API_ACCESS | Use REST/bulk APIs |
| MANAGE_INTEGRATIONS | Configure connected apps and webhooks |
| MANAGE_DATA | Import/export/mass delete |
| VIEW_SETUP | See setup/admin pages |
| MANAGE_SANDBOX | Create/refresh sandboxes |

---

## 16. UI Framework and Page Builder

### 16.1 Page Layout System

Replace raw JSONB config with a structured layout system:

| Concept | Description |
|---------|-------------|
| Page Layout | Arrangement of fields, sections, and related lists for a record detail page |
| Section | Grouping of fields with a header, 1 or 2 column layout |
| Related List | Child records shown at bottom of page (e.g., Contacts on Account) |
| Compact Layout | Fields shown in highlights panel and mobile view |
| Search Layout | Columns shown in list views and search results |
| Mini Page Layout | Subset of fields shown in hover popups |

#### 16.1.1 Layout Assignment

Layouts are assigned per object per record type per profile:

```
Object: Opportunity
  Record Type: Standard
    Profile: Sales User -> Sales Opportunity Layout
    Profile: Finance User -> Finance Opportunity Layout
  Record Type: Partner Deal
    Profile: Sales User -> Partner Deal Layout
```

### 16.2 Lightning-Style Component Framework

A component-based UI framework:

| Component Type | Description |
|---------------|-------------|
| Record Detail | Standard record view/edit page |
| Record List | Table/grid view with filtering and sorting |
| Form | Custom form with conditional field visibility |
| Dashboard | Grid of charts, metrics, and lists |
| Kanban | Drag-and-drop board (e.g., Opportunity stages) |
| Calendar | Date-based record view |
| Flow Screen | Step-by-step guided process UI |
| Custom Component | Developer-built React component |

### 16.3 App Builder

Drag-and-drop page composition:

| Feature | Description |
|---------|-------------|
| Region-Based Layout | Header, sidebar, main content, footer regions |
| Component Palette | Standard and custom components to drag in |
| Property Panel | Configure each component's data source, filters, visibility |
| Conditional Visibility | Show/hide components based on record data, user profile, or device |
| Dynamic Actions | Configure which buttons/actions appear based on criteria |
| Responsive Design | Desktop, tablet, mobile breakpoints |
| Preview Mode | Test the page as different user profiles |

### 16.4 List Views

| Feature | Description |
|---------|-------------|
| Filter Criteria | Field-based filters with AND/OR logic |
| Column Selection | Choose which fields to display |
| Sort Order | Multi-column sorting |
| Inline Editing | Edit records directly in the list |
| Mass Actions | Select multiple records and perform bulk operations |
| Charts | Optional chart visualization above the list |
| Sharing | Personal, team, or org-wide visibility |
| Pinned Lists | Pin frequently used list views |

---

## 17. Reporting and Analytics

### 17.1 Report Builder

| Report Type | Description |
|-------------|-------------|
| Tabular | Simple row/column listing |
| Summary | Grouped with subtotals |
| Matrix | Grouped by rows and columns (pivot table) |
| Joined | Multiple report blocks with different objects |

#### 17.1.1 Report Definition

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | |
| name | String | |
| description | String | |
| primaryObject | String | Base collection |
| relatedObjects | List | Joined collections |
| columns | List | Selected fields |
| filters | JSONB | Filter criteria |
| groupings | List | Row and column groupings |
| aggregates | List | SUM, COUNT, AVG, MIN, MAX per field |
| chartType | Enum | BAR, LINE, PIE, DONUT, FUNNEL, SCATTER, none |
| sortOrder | List | |
| scope | Enum | MY_RECORDS, MY_TEAM, ALL |
| folderId | UUID | Report folder |
| accessLevel | Enum | PRIVATE, SHARED, PUBLIC |

### 17.2 Dashboards

| Component | Description |
|-----------|-------------|
| Dashboard | Grid-based layout of dashboard components |
| Component | Displays data from a source report as a chart, gauge, metric, or table |
| Dynamic Dashboard | Runs as the viewing user (respects their data access) |
| Filters | Dashboard-level filters that propagate to all components |
| Refresh | Manual or scheduled refresh |
| Subscriptions | Email dashboard snapshots on a schedule |

### 17.3 Analytics Capabilities

| Feature | Description |
|---------|-------------|
| Cross-Object Reporting | Join data across related collections |
| Historical Trending | Track field values over time (e.g., pipeline changes) |
| Bucket Fields | Group continuous values into ranges |
| Custom Summary Formulas | Calculated fields within reports |
| Row-Level Formulas | Per-row calculations in reports |
| Report Snapshots | Scheduled report runs stored for trend analysis |
| Export | CSV, Excel, PDF export |
| Embedded Analytics | Embed report charts in record pages and dashboards |

---

## 18. Integration Platform

### 18.1 REST API Enhancements

| Feature | Description |
|---------|-------------|
| Composite API | Multiple operations in a single request |
| Batch API | Process up to 200 records per request |
| Streaming API | Real-time event stream (SSE or WebSocket) |
| Metadata API | CRUD for schema, layouts, workflows via API |
| Tooling API | Execute scripts, run tests, deploy changes via API |
| Bulk API | Async processing of millions of records (CSV upload) |
| GraphQL API | Flexible querying with field selection and nested relationships |

### 18.2 Webhooks (Outbound)

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | |
| name | String | |
| url | String | Target endpoint |
| events | List | Which events trigger it (e.g., `collection.record.created`) |
| collectionId | UUID | Optional: scope to specific collection |
| filterCriteria | JSONB | Optional: only fire when criteria met |
| headers | JSONB | Custom headers (auth tokens, etc.) |
| secret | String | HMAC signing secret |
| retryPolicy | JSONB | Max retries, backoff strategy |
| active | Boolean | |

### 18.3 Connected Apps (Inbound)

OAuth2 client registrations for external applications:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | |
| name | String | |
| clientId | String | |
| clientSecret | String (encrypted) | |
| redirectUris | List | |
| scopes | List | Allowed API scopes |
| ipRestrictions | List | Allowed IP ranges |
| rateLimits | JSONB | Per-app rate limits |
| active | Boolean | |

### 18.4 Pre-Built Connectors

| Connector | Protocol | Purpose |
|-----------|----------|---------|
| REST | HTTP/JSON | Generic REST API integration |
| SOAP | WSDL/XML | Legacy system integration |
| SFTP | SSH | File-based data exchange |
| Database | JDBC | Direct database connectivity |
| Email | SMTP/IMAP | Email integration |
| Kafka | Kafka Protocol | Event streaming |
| S3 | AWS SDK | Cloud storage |

### 18.5 Integration Flow Builder

Visual tool for building integrations:
- Source and target selection
- Field mapping with transformations
- Error handling and retry logic
- Scheduling (real-time, batch, cron)
- Monitoring dashboard with success/failure metrics

---

## 19. Scripting and Extensibility

### 19.1 Server-Side Scripts

A sandboxed scripting environment (GraalVM-based JavaScript or Groovy):

| Script Type | Trigger | Use Case |
|-------------|---------|----------|
| Before Trigger | Before record insert/update/delete | Validation, field population, cross-record checks |
| After Trigger | After record insert/update/delete | Cascading updates, notifications, integrations |
| Scheduled | Cron schedule | Batch processing, cleanup, data sync |
| API Endpoint | HTTP request to custom path | Custom REST endpoints |
| Email Handler | Inbound email received | Parse emails, create records |
| Event Handler | Platform event received | React to async events |
| Validation | Before save | Complex cross-field/cross-object validation |

#### 19.1.1 Script Context API

```javascript
// Available in all scripts
ctx.record         // Current record (in triggers)
ctx.oldRecord      // Previous values (in update triggers)
ctx.user           // Current user
ctx.tenant         // Current tenant
ctx.query(soql)    // Query records
ctx.insert(records)
ctx.update(records)
ctx.delete(records)
ctx.http.get(url)  // Make HTTP callouts
ctx.http.post(url, body)
ctx.email.send(to, template, data)
ctx.event.publish(name, payload)
ctx.log.info(message)
ctx.cache.get(key)
ctx.cache.set(key, value, ttl)
```

#### 19.1.2 Governor Limits (per script execution)

| Limit | Value | Purpose |
|-------|-------|---------|
| Query rows | 50,000 | Prevent full table scans |
| DML rows | 10,000 | Prevent runaway writes |
| HTTP callouts | 100 | Prevent external dependency explosion |
| CPU time | 10 seconds | Prevent infinite loops |
| Heap size | 12 MB | Prevent memory exhaustion |
| Query depth | 5 levels | Prevent deeply nested joins |

### 19.2 Client-Side Plugin System

Extend the existing `emf-web/packages/plugin-sdk`:

| Extension Point | Description |
|----------------|-------------|
| Custom Component | React component renderable in page builder |
| Custom Action | Button/action on record or list page |
| Custom Tab | New navigation tab |
| Field Renderer | Custom display for a field type |
| Validation Plugin | Client-side validation logic |
| Theme Plugin | Custom branding and styling |
| Dashboard Widget | Custom dashboard component |

### 19.3 Custom API Endpoints

Register tenant-scoped REST endpoints backed by scripts:

```
POST /api/v1/{tenant}/custom/calculate-pricing
  -> Executes "calculate_pricing" script
  -> Returns script output as JSON response
```

---

## 20. Data Import/Export and ETL

### 20.1 Data Import

| Feature | Description |
|---------|-------------|
| CSV Import | Upload CSV, map columns to fields, preview, execute |
| Excel Import | XLSX support with sheet selection |
| Upsert Mode | Insert or update based on external ID |
| Duplicate Detection | Match rules to prevent duplicate creation |
| Validation | Run all validation rules and triggers during import |
| Error Handling | Download error file with row-level error messages |
| Scheduled Import | SFTP or S3 source on a cron schedule |

### 20.2 Data Export

| Feature | Description |
|---------|-------------|
| CSV Export | Export any list view or report as CSV |
| Excel Export | XLSX with formatting |
| Full Backup | Weekly full data export (all objects, all records) |
| Scheduled Export | Automatic export to SFTP or S3 |
| Data Loader | Bulk API client tool for large-scale operations |

### 20.3 Mass Operations

| Operation | Description |
|-----------|-------------|
| Mass Update | Update a field on selected records |
| Mass Delete | Delete records matching criteria |
| Mass Transfer | Change record ownership |
| Mass Email | Send template emails to selected records |
| Merge Records | Merge duplicate records, preserving selected field values |

---

## 21. Audit, Compliance, and Governance

### 21.1 Audit Trail

#### 21.1.1 Field History Tracking

For collections with `enableHistory = true`:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| recordId | UUID | The record that changed |
| collectionId | UUID | |
| fieldName | String | Which field changed |
| oldValue | JSONB | Previous value |
| newValue | JSONB | New value |
| changedBy | UUID | User who made the change |
| changedAt | Timestamp | |
| changeSource | Enum | UI, API, WORKFLOW, SCRIPT, IMPORT |

#### 21.1.2 Setup Audit Trail

Track all configuration changes:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | |
| userId | UUID | Who made the change |
| action | String | e.g., "Created custom field 'Revenue' on Account" |
| section | String | e.g., "Customize > Account > Fields" |
| oldValue | JSONB | Previous configuration |
| newValue | JSONB | New configuration |
| timestamp | Timestamp | |

#### 21.1.3 Login History

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| userId | UUID | |
| loginTime | Timestamp | |
| sourceIp | String | |
| loginType | Enum | UI, API, OAUTH |
| status | Enum | SUCCESS, FAILED, LOCKED_OUT |
| browser | String | User agent |
| platform | String | OS |

### 21.2 Data Retention Policies

| Feature | Description |
|---------|-------------|
| Retention Rules | Per-collection rules for archiving/deleting old records |
| Archive Storage | Move old records to cold storage (cheaper) |
| Legal Hold | Prevent deletion of records under legal hold |
| Right to Erasure | GDPR-compliant data deletion workflows |
| Data Classification | Tag fields as PII, PHI, financial, etc. |

### 21.3 Compliance Features

| Feature | Description |
|---------|-------------|
| IP Restrictions | Restrict login/API access by IP range |
| Session Policies | Timeout, concurrent sessions, re-authentication |
| Encryption at Rest | Field-level encryption for sensitive data (ENCRYPTED field type) |
| Shield Platform Encryption | Tenant-managed encryption keys |
| Event Monitoring | Real-time monitoring of API usage, report exports, logins |
| Transaction Security | Policies that block or require MFA for sensitive operations |

---

## 22. Search and Querying

### 22.1 Full-Text Search

| Feature | Description |
|---------|-------------|
| Global Search | Search across all searchable collections from a single search bar |
| Object-Specific Search | Filter search to a specific collection |
| Search Layouts | Configure which fields appear in search results per collection |
| Indexing | PostgreSQL full-text search (tsvector/tsquery) or Elasticsearch |
| Stemming | Language-aware word stemming |
| Synonyms | Configurable synonym dictionaries |
| Search History | Track and suggest recent searches per user |
| Federated Search | Include external data sources in search results |

### 22.2 Structured Query Language (EMF-QL)

A query language similar to Salesforce SOQL:

```sql
SELECT Name, Amount, Account.Name
FROM Opportunity
WHERE Stage = 'Closed Won'
  AND CloseDate > LAST_MONTH
  AND Account.Industry IN ('Technology', 'Finance')
ORDER BY Amount DESC
LIMIT 100
```

Features:
- Cross-object queries via relationship traversal (dot notation)
- Aggregate functions (COUNT, SUM, AVG, MIN, MAX, GROUP BY, HAVING)
- Date literals (TODAY, YESTERDAY, LAST_WEEK, THIS_QUARTER, LAST_N_DAYS:30)
- Subqueries for semi-joins and anti-joins
- Polymorphic relationship support (TYPEOF)
- Compiled and cached query plans

### 22.3 List View Filters

| Operator | Example |
|----------|---------|
| equals | `Status = 'Active'` |
| not equals | `Status != 'Closed'` |
| greater than | `Amount > 10000` |
| less than | `CloseDate < TODAY` |
| contains | `Name CONTAINS 'Acme'` |
| starts with | `Name STARTS_WITH 'A'` |
| in | `Stage IN ('New', 'Working')` |
| between | `Amount BETWEEN 1000 AND 5000` |
| is blank / is not blank | `Email IS NOT BLANK` |

---

## 23. Notification System

### 23.1 Notification Channels

| Channel | Description |
|---------|-------------|
| In-App | Bell icon with notification panel; real-time via WebSocket |
| Email | Template-based emails with merge fields |
| SMS | Via Twilio or similar provider |
| Slack/Teams | Webhook-based notifications to chat platforms |
| Mobile Push | Push notifications to mobile app (future) |

### 23.2 Email Templates

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| tenantId | UUID | |
| name | String | |
| subject | String | Supports merge fields `{!Record.Name}` |
| body | Text | HTML with merge fields |
| relatedTo | String | Collection this template is for |
| folder | String | Organizational folder |
| isActive | Boolean | |

### 23.3 Notification Rules

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | |
| name | String | |
| trigger | JSONB | Event type + criteria |
| channels | List | Which channels to use |
| recipients | JSONB | Users, roles, related user fields, queues |
| templateId | UUID | Email template reference |
| throttle | JSONB | Rate limiting (e.g., max 1 per hour per user) |
| active | Boolean | |

---

## 24. File and Document Management

### 24.1 File Storage

| Feature | Description |
|---------|-------------|
| Attachment Field | New field type for attaching files to records |
| Storage Backend | S3-compatible object storage (AWS S3, MinIO) |
| Versioning | Track file versions with history |
| Virus Scanning | Scan uploads before storing |
| Size Limits | Per-file and per-tenant storage quotas |
| Content Types | Configurable allowed MIME types |
| Preview | In-browser preview for images, PDFs, Office docs |
| Sharing | Generate shareable links with expiration |

### 24.2 Document Generation

| Feature | Description |
|---------|-------------|
| Templates | DOCX/PDF templates with merge fields |
| Generation | Generate documents from record data |
| E-Signature | Integration with DocuSign/Adobe Sign |
| Storage | Auto-attach generated documents to records |

---

## 25. Sandbox and Environment Management

### 25.1 Environment Types

| Type | Description |
|------|-------------|
| Production | Live tenant environment |
| Full Sandbox | Complete copy of production (schema + data) |
| Partial Sandbox | Schema + subset of data (based on template) |
| Developer Sandbox | Schema only, no production data |
| Scratch Org | Temporary, disposable environment for CI/CD |

### 25.2 Environment Operations

| Operation | Description |
|-----------|-------------|
| Create | Provision a new sandbox from production or template |
| Refresh | Reset sandbox to match current production state |
| Seed | Populate sandbox with test data generators |
| Compare | Diff configuration between environments |
| Promote | Deploy configuration changes from sandbox to production |
| Destroy | Decommission a sandbox |

### 25.3 Change Sets (extending current Package system)

Extend the existing `ConfigPackage` / `PackageItem` model:

| Enhancement | Description |
|-------------|-------------|
| Dependency Resolution | Auto-include dependent components |
| Conflict Detection | Identify conflicting changes between environments |
| Rollback | Revert a deployed change set |
| Validation | Dry-run deployment with detailed error report |
| CI/CD Integration | API-driven deployment from build pipelines |
| Version Control Sync | Export/import from Git repositories |
| Selective Deploy | Cherry-pick individual components from a package |

---

## 26. Marketplace and Plugin System

### 26.1 App Marketplace

| Feature | Description |
|---------|-------------|
| App Listings | Browse and search available apps |
| App Packages | Bundled collections, fields, layouts, workflows, scripts, components |
| Installation | One-click install with dependency resolution |
| Upgrade | Version management with migration scripts |
| Uninstall | Clean removal of all app components |
| Ratings/Reviews | Community feedback |
| Publisher Portal | Third-party developers publish apps |
| Security Review | Automated and manual security scanning |

### 26.2 App Package Definition

```json
{
  "name": "emf-crm",
  "version": "2.1.0",
  "displayName": "EMF CRM",
  "description": "Full CRM functionality",
  "publisher": "EMF Labs",
  "dependencies": [
    {"name": "emf-contacts", "version": ">=1.0.0"}
  ],
  "components": {
    "collections": ["Account", "Contact", "Opportunity", "Lead", "Campaign"],
    "workflows": ["lead_conversion", "opportunity_stage_change"],
    "layouts": ["account_detail", "opportunity_kanban"],
    "scripts": ["lead_scoring", "territory_assignment"],
    "components": ["pipeline_chart", "activity_timeline"],
    "reports": ["pipeline_report", "win_rate_dashboard"]
  },
  "postInstall": {
    "script": "setup_crm_defaults"
  }
}
```

---

## 27. Migration Roadmap

A phased approach to evolving the current EMF platform into the enterprise system described above.

### Phase 1: Foundation (Multi-Tenancy + Enhanced Permissions)

**Objective:** Establish the multi-tenant foundation and robust permission system that all other features depend on.

| Work Item | Builds On |
|-----------|-----------|
| Tenant entity and provisioning | New |
| Schema-per-tenant data isolation | Current single-schema PostgreSQL |
| Tenant-aware request context | Current JWT authentication |
| User entity with profile/permission sets | Current Role/Policy system |
| Object-level CRUD permissions per profile | Current RoutePolicy |
| Field-level security (visible/read-only/hidden) | Current FieldPolicy |
| Organization-wide defaults (sharing model) | New |
| Role hierarchy for record access | New |
| Tenant-scoped OIDC provider config | Current OidcProvider |
| Governor limits framework | New |

### Phase 2: Enhanced Object Model + Validation

**Objective:** Make the collection system feature-equivalent with Salesforce custom objects.

| Work Item | Builds On |
|-----------|-----------|
| New field types (Picklist, Currency, Phone, Email, URL, Auto-Number, Rich Text) | Current Field entity |
| Picklist management with dependencies | New |
| LOOKUP and MASTER_DETAIL relationship types | Current REFERENCE type |
| Many-to-many relationships (junction objects) | New |
| Validation rules with formula expressions | Current field constraints |
| Record types with layout assignment | New |
| Field history tracking | Current audit timestamps |
| Setup audit trail | New |

### Phase 3: UI Framework + Reporting

**Objective:** Replace raw JSONB page configs with a structured, interactive UI system.

| Work Item | Builds On |
|-----------|-----------|
| Structured page layout model (sections, related lists) | Current UiPage |
| Layout assignment per profile/record type | Current UiPage + Phase 1 profiles |
| Drag-and-drop page builder | Current emf-ui |
| List views with inline editing | New |
| Compact and search layouts | New |
| Report builder (tabular, summary, matrix) | New |
| Dashboard builder with report components | Current admin dashboard (health only) |
| Export (CSV, Excel, PDF) | New |

### Phase 4: Workflow + Automation

**Objective:** Enable declarative business process automation.

| Work Item | Builds On |
|-----------|-----------|
| Workflow rules (field update, email alert, outbound message) | New |
| Approval processes with multi-step routing | New |
| Flow engine (visual process builder) | New |
| Scheduled jobs (cron-based script/flow execution) | New |
| Platform events (custom Kafka topics per tenant) | Current Kafka event system |
| Formula engine for calculated fields | New |
| Roll-up summary fields | New |

### Phase 5: Integration + Scripting

**Objective:** Open the platform to external systems and custom logic.

| Work Item | Builds On |
|-----------|-----------|
| Server-side scripting (GraalVM sandbox) | New |
| Before/after triggers on record operations | New |
| Custom API endpoints backed by scripts | New |
| Webhooks (outbound event notifications) | New |
| Connected apps (OAuth2 client registration) | Current OIDC system |
| Bulk API for large-scale data operations | Current REST API |
| Composite/batch API | Current REST API |
| Integration flow builder (visual) | New |

### Phase 6: Enterprise Features

**Objective:** Complete the feature set for enterprise-grade deployments.

| Work Item | Builds On |
|-----------|-----------|
| Full-text search (Elasticsearch or PostgreSQL FTS) | New |
| EMF-QL query language | New |
| Notification system (in-app, email, SMS) | New |
| File/document management (S3 backend) | New |
| Data import/export (CSV, Excel) | Current package system (config only) |
| Sandbox environments (full/partial/developer) | Current environment promotion |
| Change set enhancements (rollback, CI/CD) | Current ConfigPackage |
| Localization / i18n | New |
| Mobile-responsive UI | Current emf-ui |

### Phase 7: Marketplace + Scale

**Objective:** Enable an ecosystem of apps and scale the platform.

| Work Item | Builds On |
|-----------|-----------|
| App marketplace | Current plugin-sdk |
| App packaging and installation | Current ConfigPackage |
| Publisher portal | New |
| App security review pipeline | New |
| Horizontal scaling (read replicas, sharding) | Current architecture |
| CDN for static assets and file storage | New |
| Global deployment (multi-region) | Current Helm charts |
| Advanced analytics (historical trending, AI insights) | Phase 3 reporting |

---

### Summary

EMF today is a well-architected **metadata-driven microservice framework** with dynamic schema management, event-driven configuration propagation, JWT-based security, JSON:API support, and a React admin UI. The foundation -- collections, fields, versioning, policies, OIDC, and environment promotion -- provides the right primitives to build upon.

The path to a Salesforce/NetSuite-class platform requires seven phases of work, each building on the previous. The most critical dependency is **Phase 1 (Multi-Tenancy + Permissions)**, as virtually every subsequent feature requires tenant isolation and a mature permission model. From there, each phase adds a major capability vertical (objects, UI, workflows, integration, enterprise features, marketplace) that compounds the platform's value.
