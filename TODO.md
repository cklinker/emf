# EMF Enterprise Platform - Implementation Plan

> Phases 1-6: From current state to enterprise-grade configurable platform.
> Each work item includes purpose, use cases, and technical specifics grounded in the existing codebase.

---

## Phase 1: Multi-Tenancy and Permission Foundation

Everything in phases 2-6 depends on tenant isolation and a real permission model. This phase transforms EMF from a single-tenant framework into a multi-tenant platform.

---

### 1.1 Tenant Entity and Registry

**Purpose:** Establish the top-level organizational boundary. Every resource in the system (users, collections, data, config) belongs to exactly one tenant.

**Use Cases:**
- SaaS operator provisions a new customer organization
- Each customer sees only their own data, schemas, and configurations
- Platform admin views cross-tenant metrics and manages tenant lifecycle

**Technical Specifics:**

Create new Flyway migration `V8__add_tenant_table.sql`:

```sql
CREATE TABLE tenant (
    id VARCHAR(36) PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE,          -- URL-safe, lowercase, used in subdomains
    name VARCHAR(200) NOT NULL,
    edition VARCHAR(20) NOT NULL DEFAULT 'PROFESSIONAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING',
    settings JSONB NOT NULL DEFAULT '{}',       -- timezone, locale, fiscal year, etc.
    limits JSONB NOT NULL DEFAULT '{}',         -- api_calls_per_day, storage_gb, max_users, max_collections
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_edition CHECK (edition IN ('FREE','PROFESSIONAL','ENTERPRISE','UNLIMITED')),
    CONSTRAINT chk_tenant_status CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','DECOMMISSIONED')),
    CONSTRAINT chk_tenant_slug CHECK (slug ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$')
);
CREATE INDEX idx_tenant_status ON tenant(status);
CREATE INDEX idx_tenant_slug ON tenant(slug);
```

New entity class `Tenant` in `com.emf.controlplane.entity` extending `BaseEntity`. Follows existing pattern: UUID id, `@Column` annotations, `@EntityListeners(AuditingEntityListener.class)`.

New `TenantRepository` extending `JpaRepository<Tenant, String>`:
- `findBySlug(String slug)` -> `Optional<Tenant>`
- `findByStatus(String status)` -> `List<Tenant>`
- `existsBySlug(String slug)` -> `boolean`

New `TenantService` following existing service patterns (`@Transactional`, `ResourceNotFoundException`, `DuplicateResourceException`):
- `createTenant(CreateTenantRequest)` -> provisions tenant, creates schema, seeds defaults
- `getTenant(String id)` -> lookup
- `updateTenant(String id, UpdateTenantRequest)` -> update settings/limits
- `suspendTenant(String id)` -> set status SUSPENDED
- `activateTenant(String id)` -> set status ACTIVE

New `TenantController` at `/control/tenants` with PLATFORM_ADMIN security (not per-tenant ADMIN).

**Governor Limits** stored in `tenant.limits` JSONB:
```json
{
  "api_calls_per_day": 100000,
  "storage_gb": 10,
  "max_users": 100,
  "max_collections": 200,
  "max_fields_per_collection": 500,
  "max_workflows": 50,
  "max_reports": 200,
  "script_cpu_ms": 10000,
  "script_heap_mb": 12,
  "bulk_api_records": 50000
}
```

---

### 1.2 Schema-Per-Tenant Data Isolation

**Purpose:** Every tenant's data lives in its own PostgreSQL schema. Prevents data leakage and allows independent schema evolution per tenant.

**Use Cases:**
- Tenant A has a custom "Revenue" field on Accounts; Tenant B does not
- DBA can backup/restore a single tenant's data independently
- Tenant deletion means dropping one schema

**Technical Specifics:**

Create `TenantSchemaManager` service:
- `provisionSchema(String tenantSlug)`: executes `CREATE SCHEMA IF NOT EXISTS tenant_{slug}` then runs tenant-scoped Flyway migrations against that schema
- `dropSchema(String tenantSlug)`: for decommissioning (requires explicit confirmation)

Create `TenantContextHolder` using `ThreadLocal<String>`:
- `setTenantId(String)` / `getTenantId()` / `clear()`
- Gateway and control plane set this from JWT `org_id` claim or request header `X-Tenant-ID`

Create `TenantRoutingDataSource` extending Spring's `AbstractRoutingDataSource`:
- `determineCurrentLookupKey()` returns `TenantContextHolder.getTenantId()`
- For control plane shared tables (tenant, user, oidc_provider): use `public` schema
- For tenant-scoped tables (collection, field, policy, etc.): use `tenant_{slug}` schema

Alternative simpler approach using Hibernate interceptor:
- `TenantConnectionInterceptor` implements `StatementInspector`
- Prepends `SET search_path TO tenant_{slug}, public;` before each statement
- Registered in `application.yml` under `spring.jpa.properties.hibernate.session_factory.statement_inspector`

Add `tenant_id` FK column to these existing tables via `V9__add_tenant_id.sql`:
- `service` (add tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id))
- `collection` (already has service_id which chains to tenant, but add direct FK for query performance)
- `role`, `policy`, `oidc_provider`, `ui_page`, `ui_menu`, `package`, `migration_run`

Update all existing repositories to include tenant filtering:
- `CollectionRepository.findByTenantIdAndActiveTrue(String tenantId, Pageable)`
- `RoleRepository.findByTenantIdOrderByNameAsc(String tenantId)`
- Same pattern for all repositories

Update all existing services to pass `TenantContextHolder.getTenantId()` into repository queries.

---

### 1.3 Tenant Context Resolution

**Purpose:** Every incoming request must be associated with a tenant before any business logic runs.

**Use Cases:**
- User logs in via `acme.emf.app` -> tenant resolved from subdomain
- API call includes JWT with `org_id` claim -> tenant resolved from token
- Service-to-service call includes `X-Tenant-ID` header -> tenant resolved from header

**Technical Specifics:**

Create `TenantResolutionFilter` as a Spring `WebFilter` (gateway) / `OncePerRequestFilter` (control plane), ordered before authentication:

Resolution chain (first match wins):
1. JWT claim `org_id` (from OIDC token)
2. Request header `X-Tenant-ID` (for service-to-service)
3. Subdomain extraction from `Host` header (`acme.emf.app` -> `acme`)
4. Query parameter `_tenant` (for development only, disabled in production)

On resolution:
- Validate tenant exists and status = ACTIVE (cache tenant lookups in Redis with 5-minute TTL)
- Set `TenantContextHolder.setTenantId(tenantId)`
- Add `X-Tenant-ID` header to downstream requests (gateway -> control plane)

On failure:
- Return 400 if no tenant can be resolved
- Return 403 if tenant is SUSPENDED
- Return 404 if tenant does not exist

Add to gateway's `HeaderTransformationFilter` (existing class at `com.emf.gateway.filter.HeaderTransformationFilter`): propagate `X-Tenant-ID` to all downstream service calls.

---

### 1.4 User Entity and Management

**Purpose:** Users are people who log into the platform within a tenant. Currently EMF relies entirely on external OIDC for identity with no local user record. Enterprise features need a local user entity for permission assignment, audit trails, and record ownership.

**Use Cases:**
- Admin creates users and assigns profiles/permission sets
- User logs in via SSO; system matches to local user record by email
- Record shows "Created By: Jane Smith" with link to user profile
- Admin deactivates a departing employee's account

**Technical Specifics:**

New migration `V10__add_user_tables.sql`:

```sql
CREATE TABLE platform_user (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    email VARCHAR(320) NOT NULL,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    locale VARCHAR(10) DEFAULT 'en_US',
    timezone VARCHAR(50) DEFAULT 'UTC',
    profile_id VARCHAR(36),                    -- FK to profile (added in 1.5)
    manager_id VARCHAR(36),                    -- self-referencing FK for role hierarchy
    last_login_at TIMESTAMP WITH TIME ZONE,
    login_count INTEGER DEFAULT 0,
    mfa_enabled BOOLEAN DEFAULT false,
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','PENDING_ACTIVATION')),
    FOREIGN KEY (manager_id) REFERENCES platform_user(id)
);
CREATE INDEX idx_user_tenant ON platform_user(tenant_id);
CREATE INDEX idx_user_email ON platform_user(tenant_id, email);
CREATE INDEX idx_user_manager ON platform_user(manager_id);
CREATE INDEX idx_user_profile ON platform_user(profile_id);
CREATE INDEX idx_user_status ON platform_user(tenant_id, status);
```

New `User` entity, `UserRepository`, `UserService`, `UserController` at `/control/users`.

**OIDC-to-Local User Sync:** On first login via OIDC, if no local user record exists for the email, auto-create one using claims from the JWT (email, name from configured claims on `OidcProvider`). This is a "just-in-time provisioning" pattern. Add `JitUserProvisioningService` called from `JwtAuthenticationFilter`.

**User Service Methods:**
- `listUsers(tenantId, filter, pageable)` -> paginated list with search by name/email
- `createUser(tenantId, CreateUserRequest)` -> manual creation
- `getUser(tenantId, userId)` -> single user
- `updateUser(tenantId, userId, UpdateUserRequest)` -> edit profile assignment, status, etc.
- `deactivateUser(tenantId, userId)` -> set INACTIVE, revoke sessions
- `getManagerChain(tenantId, userId)` -> walk manager_id chain (for approval routing)

---

### 1.5 Profile and Permission Set System

**Purpose:** Profiles define what a user can do (object access, field visibility, system features). Permission sets add incremental permissions on top. This replaces the current simple Role + Policy model with an enterprise-grade permission system.

**Use Cases:**
- "Sales User" profile can create/edit Opportunities but not delete them
- "Finance User" profile can see the "Revenue" field on Account; "Sales User" cannot
- "API Access" permission set grants REST API access to users who need it
- Admin assigns "Report Builder" permission set to a specific user without changing their profile

**Technical Specifics:**

New migration `V11__add_permission_tables.sql`:

```sql
-- Profile: exactly one per user, defines baseline permissions
CREATE TABLE profile (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_system BOOLEAN DEFAULT false,           -- system profiles can't be deleted
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_profile_tenant_name UNIQUE (tenant_id, name)
);

-- Object permission: what operations a profile can do on a collection
CREATE TABLE object_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    can_create BOOLEAN DEFAULT false,
    can_read BOOLEAN DEFAULT false,
    can_edit BOOLEAN DEFAULT false,
    can_delete BOOLEAN DEFAULT false,
    can_view_all BOOLEAN DEFAULT false,        -- bypasses sharing rules for read
    can_modify_all BOOLEAN DEFAULT false,      -- bypasses sharing rules for edit/delete
    CONSTRAINT uq_obj_perm UNIQUE (profile_id, collection_id)
);

-- Field permission: per-field visibility for a profile
CREATE TABLE field_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    field_id VARCHAR(36) NOT NULL REFERENCES field(id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    CONSTRAINT uq_field_perm UNIQUE (profile_id, field_id),
    CONSTRAINT chk_visibility CHECK (visibility IN ('VISIBLE','READ_ONLY','HIDDEN'))
);

-- System permission: platform-level capabilities for a profile
CREATE TABLE system_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    granted BOOLEAN DEFAULT false,
    CONSTRAINT uq_sys_perm UNIQUE (profile_id, permission_key)
);

-- Permission set: additive permissions assigned to individual users
CREATE TABLE permission_set (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_system BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_permset_tenant_name UNIQUE (tenant_id, name)
);

-- Permission set has same object/field/system permissions structure
CREATE TABLE permset_object_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    can_create BOOLEAN DEFAULT false,
    can_read BOOLEAN DEFAULT false,
    can_edit BOOLEAN DEFAULT false,
    can_delete BOOLEAN DEFAULT false,
    can_view_all BOOLEAN DEFAULT false,
    can_modify_all BOOLEAN DEFAULT false,
    CONSTRAINT uq_ps_obj_perm UNIQUE (permission_set_id, collection_id)
);

CREATE TABLE permset_field_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    field_id VARCHAR(36) NOT NULL REFERENCES field(id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    CONSTRAINT uq_ps_field_perm UNIQUE (permission_set_id, field_id),
    CONSTRAINT chk_ps_visibility CHECK (visibility IN ('VISIBLE','READ_ONLY','HIDDEN'))
);

CREATE TABLE permset_system_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    granted BOOLEAN DEFAULT false,
    CONSTRAINT uq_ps_sys_perm UNIQUE (permission_set_id, permission_key)
);

-- Junction: user <-> permission_set (many-to-many)
CREATE TABLE user_permission_set (
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, permission_set_id)
);

-- Add profile FK to platform_user
ALTER TABLE platform_user ADD CONSTRAINT fk_user_profile
    FOREIGN KEY (profile_id) REFERENCES profile(id);
```

**Permission Resolution Algorithm** (implemented in `PermissionResolver` service):

```
effectiveObjectPermission(user, collection) =
    profile.objectPermission(collection)
    OR any(user.permissionSets.objectPermission(collection))

// OR logic: if profile says can_create=false but a permission set says can_create=true, result is true
// Permission sets are purely additive; they never reduce permissions
```

For field permissions, use most-permissive: VISIBLE > READ_ONLY > HIDDEN.

**Seed default profiles per tenant** during provisioning:
- "System Administrator" - all permissions granted, is_system=true
- "Standard User" - basic read/create, is_system=true
- "Read Only" - read access only, is_system=true
- "Minimum Access" - no object access, is_system=true

**System Permission Keys** (initial set):
```
MANAGE_USERS, CUSTOMIZE_APPLICATION, MANAGE_SHARING,
MANAGE_WORKFLOWS, MANAGE_REPORTS, API_ACCESS,
MANAGE_INTEGRATIONS, MANAGE_DATA, VIEW_SETUP,
MANAGE_SANDBOX, VIEW_ALL_DATA, MODIFY_ALL_DATA
```

**Refactor existing authorization:** The current `RoutePolicy` / `FieldPolicy` / `PolicyEvaluator` in the gateway becomes a thin layer that calls `PermissionResolver`. The existing `Role` and `Policy` entities remain for backward compatibility but the gateway's `RouteAuthorizationFilter` and `FieldAuthorizationFilter` are updated to check object/field permissions from the user's resolved profile + permission sets.

---

### 1.6 Record-Level Sharing Model

**Purpose:** Controls which specific records a user can see/edit beyond their object-level permissions. Object permissions say "can this user read Accounts?" Sharing rules say "which Accounts can they read?"

**Use Cases:**
- Sales reps see only their own Opportunities (OWD = Private)
- Managers see their direct reports' records via role hierarchy
- "All West Coast Accounts" shared with "West Coast Sales Team" via sharing rule
- Record owner manually shares a specific Account with a colleague

**Technical Specifics:**

New migration `V12__add_sharing_tables.sql`:

```sql
-- Organization-wide default per collection
CREATE TABLE org_wide_default (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    internal_access VARCHAR(20) NOT NULL DEFAULT 'PUBLIC_READ_WRITE',
    external_access VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    CONSTRAINT uq_owd UNIQUE (tenant_id, collection_id),
    CONSTRAINT chk_internal CHECK (internal_access IN ('PRIVATE','PUBLIC_READ','PUBLIC_READ_WRITE')),
    CONSTRAINT chk_external CHECK (external_access IN ('PRIVATE','PUBLIC_READ','PUBLIC_READ_WRITE'))
);

-- Role hierarchy for record access inheritance
-- (Reuses existing role table, adds hierarchy)
ALTER TABLE role ADD COLUMN parent_role_id VARCHAR(36) REFERENCES role(id);
ALTER TABLE role ADD COLUMN hierarchy_level INTEGER DEFAULT 0;
CREATE INDEX idx_role_parent ON role(parent_role_id);

-- Sharing rule: criteria-based or owner-based
CREATE TABLE sharing_rule (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,            -- OWNER_BASED or CRITERIA_BASED
    shared_from VARCHAR(36),                   -- role or group that owns records
    shared_to VARCHAR(36) NOT NULL,            -- role or group receiving access
    shared_to_type VARCHAR(20) NOT NULL,       -- ROLE, GROUP, QUEUE
    access_level VARCHAR(20) NOT NULL,         -- READ or READ_WRITE
    criteria JSONB,                            -- for CRITERIA_BASED rules (field conditions)
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rule_type CHECK (rule_type IN ('OWNER_BASED','CRITERIA_BASED')),
    CONSTRAINT chk_access_level CHECK (access_level IN ('READ','READ_WRITE'))
);

-- Manual sharing: record owner grants access to specific users/groups
CREATE TABLE record_share (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    record_id VARCHAR(36) NOT NULL,
    shared_with_id VARCHAR(36) NOT NULL,       -- user or group ID
    shared_with_type VARCHAR(20) NOT NULL,     -- USER, GROUP, ROLE
    access_level VARCHAR(20) NOT NULL,         -- READ or READ_WRITE
    reason VARCHAR(20) DEFAULT 'MANUAL',       -- MANUAL, RULE, TEAM, TERRITORY
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_share_access CHECK (access_level IN ('READ','READ_WRITE')),
    CONSTRAINT uq_record_share UNIQUE (collection_id, record_id, shared_with_id, shared_with_type)
);
CREATE INDEX idx_share_record ON record_share(collection_id, record_id);
CREATE INDEX idx_share_user ON record_share(shared_with_id, shared_with_type);

-- Groups (public groups for sharing)
CREATE TABLE user_group (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    group_type VARCHAR(20) DEFAULT 'PUBLIC',   -- PUBLIC, QUEUE
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_group_name UNIQUE (tenant_id, name)
);

CREATE TABLE user_group_member (
    group_id VARCHAR(36) NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);
```

**Record Access Calculation** (implemented in `RecordAccessService`):

```
canUserAccessRecord(user, collection, record, accessType) =
    1. If user has can_view_all/can_modify_all on this collection -> ALLOW
    2. If OWD for collection is PUBLIC_READ_WRITE -> ALLOW
    3. If OWD is PUBLIC_READ and accessType is READ -> ALLOW
    4. If user owns the record (record.owner_id = user.id) -> ALLOW
    5. If user's role is above record owner's role in hierarchy -> ALLOW
    6. If any sharing_rule grants access -> ALLOW
    7. If any record_share grants access -> ALLOW
    8. DENY
```

Add `owner_id` system field to all collection records. The `PhysicalTableStorageAdapter` must add an `owner_id VARCHAR(36)` column to every table it creates. Set automatically to current user on record creation.

**Integration with gateway:** The gateway's `RouteAuthorizationFilter` checks object-level permissions. For record-level, the domain service (or a new `RecordAccessFilter` in the gateway for proxied requests) calls `RecordAccessService` to filter query results. For list operations, this translates to additional WHERE clauses injected into queries.

---

### 1.7 Tenant-Scoped OIDC Configuration

**Purpose:** Each tenant can configure its own identity provider (e.g., Okta for Acme Corp, Azure AD for Contoso). Extend the existing `OidcProvider` entity.

**Use Cases:**
- Acme Corp connects their Okta instance for SSO
- Contoso connects Azure AD
- Platform supports multiple tenants with different identity providers simultaneously

**Technical Specifics:**

The existing `OidcProvider` entity already has the right fields. Changes needed:

1. Add `tenant_id` FK to `oidc_provider` table (migration V9 handles this).
2. Update `OidcProviderService` to filter by tenant.
3. Update gateway's `JwtAuthenticationFilter` to:
   - Extract issuer from incoming JWT
   - Look up `OidcProvider` by issuer (cached in Redis)
   - Validate token against that provider's JWKS
   - Extract claims using that provider's claim mappings
   - Resolve tenant from the provider's `tenant_id`

This replaces the current single-issuer configuration in `application.yml` with dynamic multi-provider resolution.

---

## Phase 2: Enhanced Object Model and Validation

Builds on Phase 1's tenant isolation. Makes the collection/field system powerful enough to model real business data.

---

### 2.1 Extended Field Types

**Purpose:** The current 8 field types (TEXT, INTEGER, BOOLEAN, DECIMAL, DATE, TIMESTAMP, REFERENCE, ARRAY) are insufficient for enterprise data modeling. Real businesses need picklists, currencies, auto-numbers, emails, phones, and more.

**Use Cases:**
- Sales tracks "Opportunity Stage" as a picklist (Prospecting, Qualification, Closed Won, etc.)
- Finance needs Currency fields with ISO currency code
- Support uses Auto-Number for ticket IDs (TICKET-0001, TICKET-0002)
- HR stores employee phone numbers with formatting validation
- Legal needs Rich Text fields for contract clauses

**Technical Specifics:**

Update `FieldType` enum in `runtime-core` (`com.emf.runtime.core.definition.FieldType`):

```java
public enum FieldType {
    // Existing
    TEXT, INTEGER, BOOLEAN, DECIMAL, DATE, TIMESTAMP, REFERENCE, ARRAY, JSON, UUID,
    // New - Phase 2
    PICKLIST, MULTI_PICKLIST, AUTO_NUMBER, CURRENCY, PERCENT,
    PHONE, EMAIL, URL, RICH_TEXT, ENCRYPTED, GEOLOCATION,
    LOOKUP, MASTER_DETAIL, EXTERNAL_ID, FORMULA, ROLLUP_SUMMARY
}
```

Update `PhysicalTableStorageAdapter` SQL type mapping:

| FieldType | PostgreSQL Type | Notes |
|-----------|----------------|-------|
| PICKLIST | VARCHAR(255) | Validated against allowed values |
| MULTI_PICKLIST | TEXT[] | PostgreSQL array of selected values |
| AUTO_NUMBER | VARCHAR(100) | Application generates via sequence |
| CURRENCY | NUMERIC(18,2) + currency_code VARCHAR(3) | Two columns per currency field |
| PERCENT | NUMERIC(8,4) | Stored as decimal (50% = 50.0000) |
| PHONE | VARCHAR(40) | Validated with regex |
| EMAIL | VARCHAR(320) | Validated with regex |
| URL | VARCHAR(2048) | Validated with URL format |
| RICH_TEXT | TEXT | HTML content, sanitized on save |
| ENCRYPTED | BYTEA | Encrypted via pgcrypto or application-level |
| GEOLOCATION | POINT | PostGIS or lat/lng columns |
| LOOKUP | VARCHAR(36) + FK | FK constraint to target table |
| MASTER_DETAIL | VARCHAR(36) + FK NOT NULL | Non-nullable FK + ON DELETE CASCADE |
| EXTERNAL_ID | VARCHAR(255) + UNIQUE INDEX | For integration upsert matching |
| FORMULA | (no column) | Computed at query time via SQL expression or app layer |
| ROLLUP_SUMMARY | (no column) | Computed via aggregate subquery |

Add `fieldTypeConfig` JSONB column to `field` table to store type-specific configuration:

```json
// PICKLIST
{"values": [{"value": "new", "label": "New", "default": true, "color": "#blue"}], "sorted": true}

// AUTO_NUMBER
{"prefix": "TICKET-", "padding": 4, "startValue": 1}

// CURRENCY
{"currencyField": "currency_code", "precision": 2}

// FORMULA
{"expression": "Amount * Quantity", "returnType": "DECIMAL"}

// ROLLUP_SUMMARY
{"childCollection": "line_items", "function": "SUM", "field": "amount", "filter": {"status": "active"}}
```

Update `FieldService.addField()` to validate `fieldTypeConfig` based on field type. Add `FieldTypeValidator` interface with implementations per type.

---

### 2.2 Picklist Management

**Purpose:** Picklists are the most common field type in enterprise apps. They need dedicated management including dependent picklists, restricted picklists, and global picklist value sets.

**Use Cases:**
- Admin defines "Opportunity Stage" with values: Prospecting, Qualification, Proposal, Negotiation, Closed Won, Closed Lost
- "Sub-Stage" picklist depends on "Stage" (when Stage=Prospecting, Sub-Stage shows different values than when Stage=Negotiation)
- Global picklist "Countries" reused across multiple collections
- Admin restricts a picklist so users cannot add new values inline

**Technical Specifics:**

New migration `V13__add_picklist_tables.sql`:

```sql
-- Global picklist value set (reusable across fields)
CREATE TABLE global_picklist (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sorted BOOLEAN DEFAULT false,
    restricted BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_global_picklist UNIQUE (tenant_id, name)
);

CREATE TABLE picklist_value (
    id VARCHAR(36) PRIMARY KEY,
    picklist_source_type VARCHAR(20) NOT NULL,  -- FIELD or GLOBAL
    picklist_source_id VARCHAR(36) NOT NULL,     -- field_id or global_picklist_id
    value VARCHAR(255) NOT NULL,
    label VARCHAR(255) NOT NULL,
    is_default BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    sort_order INTEGER DEFAULT 0,
    color VARCHAR(20),
    description VARCHAR(500),
    CONSTRAINT uq_picklist_value UNIQUE (picklist_source_type, picklist_source_id, value)
);
CREATE INDEX idx_picklist_source ON picklist_value(picklist_source_type, picklist_source_id);

-- Dependent picklist mapping
CREATE TABLE picklist_dependency (
    id VARCHAR(36) PRIMARY KEY,
    controlling_field_id VARCHAR(36) NOT NULL REFERENCES field(id),
    dependent_field_id VARCHAR(36) NOT NULL REFERENCES field(id),
    mapping JSONB NOT NULL,  -- {"controlling_value": ["dependent_value_1", "dependent_value_2"]}
    CONSTRAINT uq_picklist_dep UNIQUE (controlling_field_id, dependent_field_id)
);
```

New `PicklistService`:
- `getPicklistValues(fieldId)` -> returns values with sort order
- `setPicklistValues(fieldId, List<PicklistValueRequest>)` -> replace values (validates no in-use values removed)
- `createGlobalPicklist(tenantId, request)` -> reusable value set
- `setDependency(controllingFieldId, dependentFieldId, mapping)` -> configure dependent picklist

**Validation on record save:** When saving a record with a PICKLIST field, `PhysicalTableStorageAdapter` must validate the value exists in the picklist. For MULTI_PICKLIST, validate each selected value. For dependent picklists, validate the selected value is valid given the controlling field's value.

---

### 2.3 Relationship Types (Lookup and Master-Detail)

**Purpose:** The current REFERENCE type is a simple string pointer with no database enforcement. Enterprise apps need proper foreign keys with cascade behaviors, and need to distinguish between optional lookups and required parent-child relationships.

**Use Cases:**
- Account has many Contacts (Master-Detail: deleting Account deletes its Contacts)
- Opportunity links to Account (Lookup: Account can be changed, Opportunity survives Account deletion)
- Contact can optionally link to a "Referred By" Contact (Self-referencing Lookup)
- Invoice Line Item must belong to exactly one Invoice (Master-Detail, required)

**Technical Specifics:**

Add to `field` table (via migration):
```sql
ALTER TABLE field ADD COLUMN relationship_type VARCHAR(20);  -- LOOKUP, MASTER_DETAIL
ALTER TABLE field ADD COLUMN relationship_name VARCHAR(100); -- child relationship name (e.g., "contacts" on Account)
ALTER TABLE field ADD COLUMN cascade_delete BOOLEAN DEFAULT false;
ALTER TABLE field ADD COLUMN reference_collection_id VARCHAR(36) REFERENCES collection(id);
```

Update `PhysicalTableStorageAdapter.initializeCollection()`:
- For LOOKUP fields: `ALTER TABLE tbl_{child} ADD CONSTRAINT fk_{field} FOREIGN KEY ({field}_id) REFERENCES tbl_{parent}(id) ON DELETE SET NULL`
- For MASTER_DETAIL fields: `ALTER TABLE tbl_{child} ADD CONSTRAINT fk_{field} FOREIGN KEY ({field}_id) REFERENCES tbl_{parent}(id) ON DELETE CASCADE`, column is NOT NULL

Update `CollectionDefinition` to include relationship metadata so the query engine can do JOINs:
```java
record RelationshipConfig(
    String relationshipName,        // "contacts" (used in queries: SELECT ... FROM Account.Contacts)
    String relatedCollection,       // "contact"
    String foreignKeyField,         // "account_id" on contact table
    RelationshipType type,          // LOOKUP or MASTER_DETAIL
    boolean cascadeDelete
) {}
```

Update the gateway's `IncludeResolver` to use relationship metadata for JSON:API `?include=contacts` resolution.

**Many-to-Many:** Implemented as two MASTER_DETAIL relationships with an explicit junction collection. The junction collection has two MASTER_DETAIL fields pointing to each side. Admin creates this manually (or a wizard auto-creates it). Example: `ContactRole` junction between `Contact` and `Opportunity`.

---

### 2.4 Validation Rules Engine

**Purpose:** Field-level constraints (min, max, pattern) are too limited. Enterprise apps need cross-field validation rules with custom error messages.

**Use Cases:**
- "Close Date cannot be in the past when Stage is not Closed" -> cross-field rule
- "Discount cannot exceed 30% unless approved" -> cross-field + permission check
- "Email OR Phone is required" -> either-or rule
- "End Date must be after Start Date" -> field comparison

**Technical Specifics:**

New migration `V14__add_validation_rules.sql`:

```sql
CREATE TABLE validation_rule (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN DEFAULT true,
    error_condition_formula TEXT NOT NULL,  -- expression that evaluates to TRUE when invalid
    error_message VARCHAR(1000) NOT NULL,
    error_field VARCHAR(100),              -- which field to highlight (optional)
    evaluate_on VARCHAR(20) DEFAULT 'CREATE_AND_UPDATE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_validation_rule UNIQUE (tenant_id, collection_id, name),
    CONSTRAINT chk_evaluate_on CHECK (evaluate_on IN ('CREATE','UPDATE','CREATE_AND_UPDATE'))
);
```

Create `FormulaEvaluator` service (shared between validation rules, formula fields, workflow criteria):
- Parses expression strings into an AST
- Evaluates against a record context (field values as variables)
- Returns typed results (Boolean for validation, any type for formula fields)

**Supported expression syntax** (subset of common formula languages):

```
// Comparisons
Amount > 0
CloseDate < TODAY()
Stage != 'Closed Lost'

// Logical
AND(Amount > 0, Stage = 'Closed Won')
OR(ISBLANK(Email), ISBLANK(Phone))
NOT(ISBLANK(Name))
IF(Amount > 10000, 'High', 'Low')

// String functions
LEN(Name) > 0
CONTAINS(Description, 'urgent')
BEGINS(AccountName, 'Acme')
REGEX(ZipCode, '\\d{5}')

// Date functions
CloseDate < TODAY()
DATEDIFF(CreatedDate, TODAY()) > 30
YEAR(CloseDate) = YEAR(TODAY())

// Null handling
ISBLANK(Email)
BLANKVALUE(Phone, 'N/A')
NULLVALUE(Amount, 0)

// Cross-field
EndDate > StartDate
Amount * Quantity = TotalAmount
```

**Integration point:** The `PhysicalTableStorageAdapter.create()` and `.update()` methods call `ValidationRuleService.validate(collectionId, record, isCreate)` before persisting. If any rule's error_condition_formula evaluates to true, throw `ValidationException` with the rule's error_message and error_field.

---

### 2.5 Record Types

**Purpose:** A single collection can serve multiple business purposes. Record types control which page layout, picklist values, and business processes apply.

**Use Cases:**
- "Case" collection has record types: "Support Case", "Billing Inquiry", "Feature Request" -- each with different layouts and picklist options
- "Account" has "Customer Account" and "Partner Account" record types
- Profile assignment controls which record types a user can create

**Technical Specifics:**

New migration `V15__add_record_types.sql`:

```sql
CREATE TABLE record_type (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_record_type UNIQUE (tenant_id, collection_id, name)
);

-- Which picklist values are available per record type
CREATE TABLE record_type_picklist (
    id VARCHAR(36) PRIMARY KEY,
    record_type_id VARCHAR(36) NOT NULL REFERENCES record_type(id) ON DELETE CASCADE,
    field_id VARCHAR(36) NOT NULL REFERENCES field(id),
    available_values JSONB NOT NULL,           -- subset of picklist values
    default_value VARCHAR(255),
    CONSTRAINT uq_rtp UNIQUE (record_type_id, field_id)
);

-- Which profiles can use which record types
CREATE TABLE profile_record_type (
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    record_type_id VARCHAR(36) NOT NULL REFERENCES record_type(id) ON DELETE CASCADE,
    is_default BOOLEAN DEFAULT false,
    PRIMARY KEY (profile_id, record_type_id)
);
```

Add `record_type_id VARCHAR(36)` system field to every collection table (added by `PhysicalTableStorageAdapter`). On record creation, if the user's profile has a default record type for the collection, use it; otherwise require explicit selection.

---

### 2.6 Field History Tracking

**Purpose:** Track every change to specified fields on a record for audit and compliance.

**Use Cases:**
- "Who changed the Opportunity Amount from $50K to $100K and when?"
- Compliance audit: show all changes to financial fields in the last 90 days
- Support debugging: see the history of a case's status changes

**Technical Specifics:**

New migration `V16__add_field_history.sql`:

```sql
CREATE TABLE field_history (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    record_id VARCHAR(36) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    changed_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    change_source VARCHAR(20) NOT NULL DEFAULT 'UI'
);
CREATE INDEX idx_field_history_record ON field_history(collection_id, record_id, changed_at DESC);
CREATE INDEX idx_field_history_field ON field_history(collection_id, field_name, changed_at DESC);
CREATE INDEX idx_field_history_user ON field_history(changed_by, changed_at DESC);

-- Track which fields are history-enabled per collection
ALTER TABLE field ADD COLUMN track_history BOOLEAN DEFAULT false;
```

Add `FieldHistoryService`:
- `recordChanges(collectionId, recordId, oldRecord, newRecord, userId, source)` -> compares old/new values for history-enabled fields, inserts field_history rows
- `getHistory(collectionId, recordId, pageable)` -> paginated history for a record
- `getFieldHistory(collectionId, recordId, fieldName, pageable)` -> history for a specific field

Called from `PhysicalTableStorageAdapter.update()` after a successful update.

---

### 2.7 Setup Audit Trail

**Purpose:** Track all configuration changes (schema changes, permission changes, workflow changes) for compliance.

**Use Cases:**
- "Who added the 'Discount' field to Opportunities last Tuesday?"
- SOX compliance: demonstrate that permission changes are tracked and reviewed
- Debugging: understand why a workflow stopped working (someone modified it)

**Technical Specifics:**

New migration `V17__add_setup_audit.sql`:

```sql
CREATE TABLE setup_audit_trail (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    action VARCHAR(50) NOT NULL,               -- CREATED, UPDATED, DELETED, ACTIVATED, DEACTIVATED
    section VARCHAR(100) NOT NULL,             -- e.g., 'Collections', 'Fields', 'Profiles', 'Workflows'
    entity_type VARCHAR(50) NOT NULL,          -- e.g., 'Field', 'Profile', 'ValidationRule'
    entity_id VARCHAR(36),
    entity_name VARCHAR(200),
    old_value JSONB,
    new_value JSONB,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant_time ON setup_audit_trail(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_user ON setup_audit_trail(user_id, timestamp DESC);
CREATE INDEX idx_audit_entity ON setup_audit_trail(entity_type, entity_id);
```

Create `SetupAuditService` with a single method:
- `log(tenantId, userId, action, section, entityType, entityId, entityName, oldValue, newValue)`

Call from every service that modifies configuration: `CollectionService`, `FieldService`, `AuthorizationService`, `ProfileService`, `WorkflowService`, etc. Use an AOP aspect `@SetupAudited` annotation to capture this automatically.

---

## Phase 3: UI Framework and Reporting

Builds on Phase 2's enhanced object model. Gives users the ability to customize their interface and analyze their data.

---

### 3.1 Structured Page Layout Model

**Purpose:** Replace the current raw JSONB `ui_page.config` with a structured, queryable layout model that supports sections, field ordering, related lists, and profile-based assignment.

**Use Cases:**
- Admin arranges Account detail page: "Account Info" section (2 columns) at top, "Financial Details" section below, "Contacts" related list at bottom
- Different profiles see different layouts for the same object
- Each record type can have its own layout

**Technical Specifics:**

New migration `V18__add_page_layouts.sql`:

```sql
CREATE TABLE page_layout (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    layout_type VARCHAR(20) DEFAULT 'DETAIL',  -- DETAIL, COMPACT, SEARCH, MINI
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_layout UNIQUE (tenant_id, collection_id, name)
);

CREATE TABLE layout_section (
    id VARCHAR(36) PRIMARY KEY,
    layout_id VARCHAR(36) NOT NULL REFERENCES page_layout(id) ON DELETE CASCADE,
    heading VARCHAR(200),
    columns INTEGER DEFAULT 2,                 -- 1 or 2 column layout
    sort_order INTEGER NOT NULL,
    collapsed BOOLEAN DEFAULT false,
    style VARCHAR(20) DEFAULT 'DEFAULT'        -- DEFAULT, FULL_WIDTH, CARD
);
CREATE INDEX idx_section_layout ON layout_section(layout_id, sort_order);

CREATE TABLE layout_field (
    id VARCHAR(36) PRIMARY KEY,
    section_id VARCHAR(36) NOT NULL REFERENCES layout_section(id) ON DELETE CASCADE,
    field_id VARCHAR(36) NOT NULL REFERENCES field(id),
    column_number INTEGER DEFAULT 1,           -- 1 or 2 (which column in section)
    sort_order INTEGER NOT NULL,
    is_required_on_layout BOOLEAN DEFAULT false,
    is_read_only_on_layout BOOLEAN DEFAULT false
);
CREATE INDEX idx_layout_field ON layout_field(section_id, sort_order);

CREATE TABLE layout_related_list (
    id VARCHAR(36) PRIMARY KEY,
    layout_id VARCHAR(36) NOT NULL REFERENCES page_layout(id) ON DELETE CASCADE,
    related_collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    relationship_field_id VARCHAR(36) NOT NULL REFERENCES field(id),  -- the FK field on child
    display_columns JSONB NOT NULL,            -- field IDs to show as columns
    sort_field VARCHAR(100),
    sort_direction VARCHAR(4) DEFAULT 'DESC',
    row_limit INTEGER DEFAULT 10,
    sort_order INTEGER NOT NULL
);

-- Assignment: which layout for which profile + record type combination
CREATE TABLE layout_assignment (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id),
    record_type_id VARCHAR(36) REFERENCES record_type(id),  -- null = default record type
    layout_id VARCHAR(36) NOT NULL REFERENCES page_layout(id),
    CONSTRAINT uq_layout_assign UNIQUE (tenant_id, collection_id, profile_id, record_type_id)
);
```

New `PageLayoutService`:
- `getLayoutForUser(tenantId, collectionId, recordTypeId, userId)` -> resolves user's profile -> finds layout assignment -> returns full layout with sections, fields, related lists
- `createLayout(tenantId, collectionId, request)` -> creates layout with sections
- `updateLayout(layoutId, request)` -> update sections/fields/related lists
- `assignLayout(tenantId, collectionId, profileId, recordTypeId, layoutId)` -> assign

New `PageLayoutController` at `/control/layouts`.

The gateway's `FieldAuthorizationFilter` uses layout data (in addition to field permissions) to determine which fields to include in responses. A field not on the layout is omitted from the response unless explicitly requested via `?fields[type]=field1,field2`.

---

### 3.2 List Views

**Purpose:** Configurable saved views of collection records with filtering, column selection, and sorting.

**Use Cases:**
- "My Open Opportunities" - filtered to owner=currentUser AND stage != Closed
- "All Accounts in California" - filtered by state field
- "Recently Modified Contacts" - sorted by updatedAt DESC
- Users create personal list views; admins create org-wide views

**Technical Specifics:**

New migration `V19__add_list_views.sql`:

```sql
CREATE TABLE list_view (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(100) NOT NULL,
    created_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    visibility VARCHAR(20) DEFAULT 'PRIVATE',  -- PRIVATE, GROUP, PUBLIC
    is_default BOOLEAN DEFAULT false,
    columns JSONB NOT NULL,                    -- ordered list of field IDs
    filter_logic VARCHAR(500),                 -- "1 AND (2 OR 3)"
    filters JSONB NOT NULL DEFAULT '[]',       -- [{fieldId, operator, value}]
    sort_field VARCHAR(100),
    sort_direction VARCHAR(4) DEFAULT 'ASC',
    row_limit INTEGER DEFAULT 50,
    chart_config JSONB,                        -- optional chart above list
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_list_view UNIQUE (tenant_id, collection_id, name, created_by)
);
```

**Filter operators:** equals, not_equals, greater_than, less_than, greater_or_equal, less_or_equal, contains, not_contains, starts_with, ends_with, is_blank, is_not_blank, in, not_in, between, today, this_week, this_month, this_quarter, this_year, last_n_days.

**Filter logic:** Supports AND/OR grouping with numbered references: `"1 AND (2 OR 3)"` means filter #1 AND (filter #2 OR filter #3).

New `ListViewService` and `ListViewController` at `/control/listviews`.

The `PhysicalTableStorageAdapter.query()` method already supports dynamic WHERE/ORDER BY. Extend its `QueryRequest` to accept list view filter definitions and translate them to SQL.

---

### 3.3 Report Builder

**Purpose:** Let users create reports that aggregate and visualize data across collections.

**Use Cases:**
- Sales manager: "Show me total pipeline by Stage, grouped by sales rep, for this quarter"
- Finance: "Monthly revenue trend for the last 12 months"
- Executive: "Win rate by product line (matrix report)"
- Support: "Average case resolution time by category"

**Technical Specifics:**

New migration `V20__add_report_tables.sql`:

```sql
CREATE TABLE report (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    report_type VARCHAR(20) NOT NULL,          -- TABULAR, SUMMARY, MATRIX, JOINED
    primary_collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    related_joins JSONB DEFAULT '[]',          -- [{collectionId, joinField, joinType}]
    columns JSONB NOT NULL,                    -- [{fieldId, aggregateFunction, label, formula}]
    filters JSONB DEFAULT '[]',               -- same format as list view filters
    filter_logic VARCHAR(500),
    row_groupings JSONB DEFAULT '[]',          -- [{fieldId, sortOrder, dateGranularity}]
    column_groupings JSONB DEFAULT '[]',       -- for MATRIX type
    sort_order JSONB DEFAULT '[]',
    chart_type VARCHAR(20),                    -- BAR, LINE, PIE, DONUT, FUNNEL, SCATTER, NONE
    chart_config JSONB,
    scope VARCHAR(20) DEFAULT 'MY_RECORDS',    -- MY_RECORDS, MY_TEAM, ALL_RECORDS
    folder_id VARCHAR(36),
    access_level VARCHAR(20) DEFAULT 'PRIVATE',
    created_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE report_folder (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    access_level VARCHAR(20) DEFAULT 'PRIVATE',
    created_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    CONSTRAINT uq_report_folder UNIQUE (tenant_id, name, created_by)
);
```

Create `ReportExecutionEngine`:
- Takes a report definition and the current user's context
- Builds a SQL query with JOINs, WHERE, GROUP BY, ORDER BY, HAVING
- Applies record-level security (adds sharing filter WHERE clauses)
- Applies field-level security (removes unauthorized columns)
- Executes and returns `ReportResult` with headers, rows, subtotals, grand totals

**Report Column Types:**
- Direct field: `{fieldId: "amount", label: "Amount"}`
- Aggregate: `{fieldId: "amount", aggregateFunction: "SUM", label: "Total Amount"}`
- Row count: `{aggregateFunction: "COUNT", label: "Record Count"}`
- Custom formula: `{formula: "SUM(amount) / COUNT(id)", label: "Average Deal Size"}`
- Bucket: `{fieldId: "amount", bucket: {ranges: [{label: "Small", max: 10000}, {label: "Medium", max: 50000}, {label: "Large"}]}}`

**Date grouping granularity** for row/column groupings: DAY, WEEK, MONTH, QUARTER, YEAR, FISCAL_QUARTER, FISCAL_YEAR.

New `ReportService` and `ReportController` at `/control/reports`.

---

### 3.4 Dashboard Builder

**Purpose:** Compose visual dashboards from report data. Each dashboard component displays data from a source report.

**Use Cases:**
- Sales dashboard: pipeline chart + recent wins list + forecast gauge + activity metrics
- Executive dashboard: revenue trend + win rate + top accounts + support case volume
- Support dashboard: open cases by priority + average resolution time + agent workload

**Technical Specifics:**

New migration `V21__add_dashboard_tables.sql`:

```sql
CREATE TABLE dashboard (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    folder_id VARCHAR(36),
    access_level VARCHAR(20) DEFAULT 'PRIVATE',
    is_dynamic BOOLEAN DEFAULT false,          -- runs as viewing user (respects their data access)
    running_user_id VARCHAR(36) REFERENCES platform_user(id),  -- if not dynamic, runs as this user
    column_count INTEGER DEFAULT 3,            -- grid column count
    created_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE dashboard_component (
    id VARCHAR(36) PRIMARY KEY,
    dashboard_id VARCHAR(36) NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    report_id VARCHAR(36) NOT NULL REFERENCES report(id),
    component_type VARCHAR(20) NOT NULL,       -- CHART, GAUGE, METRIC, TABLE
    title VARCHAR(200),
    column_position INTEGER NOT NULL,          -- 0-based grid column
    row_position INTEGER NOT NULL,             -- 0-based grid row
    column_span INTEGER DEFAULT 1,             -- how many columns wide
    row_span INTEGER DEFAULT 1,                -- how many rows tall
    config JSONB DEFAULT '{}',                 -- chart colors, gauge ranges, etc.
    sort_order INTEGER NOT NULL
);
```

New `DashboardService` and `DashboardController` at `/control/dashboards`.

Update the existing `AdminController` (`/api/_admin/dashboard`) to return tenant-specific dashboards instead of just system health.

---

### 3.5 Data Export

**Purpose:** Users need to get their data out of the system in standard formats.

**Use Cases:**
- Export a list view to CSV for offline analysis
- Export a report to Excel with formatting
- Schedule a weekly data export to SFTP for warehouse ingestion

**Technical Specifics:**

New `ExportService`:
- `exportListViewCsv(listViewId, userId)` -> generates CSV from list view query
- `exportReportCsv(reportId, userId)` -> generates CSV from report execution
- `exportReportExcel(reportId, userId)` -> generates XLSX with Apache POI
- `exportReportPdf(reportId, userId)` -> generates PDF with chart images

Add dependency to `control-plane-app/pom.xml`:
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

New controller endpoint: `GET /control/reports/{id}/export?format=csv|xlsx|pdf`

All exports respect record-level and field-level security. A user exporting a report only gets data they're authorized to see.

---

## Phase 4: Workflow and Automation

Builds on Phase 2's validation engine and formula evaluator. Adds declarative business process automation.

---

### 4.1 Workflow Rules

**Purpose:** Declarative if-then automation triggered by record events. The most common automation in enterprise platforms.

**Use Cases:**
- When Opportunity Stage changes to "Closed Won", set Close Date to today
- When Case Priority is "Critical", send email to support manager
- When Invoice Status changes to "Paid", update Account Balance
- When Lead is created with Source="Web", assign to web lead queue

**Technical Specifics:**

New migration `V22__add_workflow_tables.sql`:

```sql
CREATE TABLE workflow_rule (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN DEFAULT true,
    trigger_type VARCHAR(30) NOT NULL,
    filter_formula TEXT,                        -- evaluated against record; must be true to fire
    re_evaluate_on_update BOOLEAN DEFAULT false,
    execution_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_rule UNIQUE (tenant_id, collection_id, name),
    CONSTRAINT chk_trigger CHECK (trigger_type IN (
        'ON_CREATE','ON_UPDATE','ON_CREATE_OR_UPDATE','ON_DELETE'
    ))
);

CREATE TABLE workflow_action (
    id VARCHAR(36) PRIMARY KEY,
    workflow_rule_id VARCHAR(36) NOT NULL REFERENCES workflow_rule(id) ON DELETE CASCADE,
    action_type VARCHAR(30) NOT NULL,
    execution_order INTEGER DEFAULT 0,
    config JSONB NOT NULL,                     -- type-specific configuration
    active BOOLEAN DEFAULT true,
    CONSTRAINT chk_action_type CHECK (action_type IN (
        'FIELD_UPDATE','EMAIL_ALERT','CREATE_RECORD','INVOKE_SCRIPT',
        'OUTBOUND_MESSAGE','CREATE_TASK','PUBLISH_EVENT'
    ))
);
CREATE INDEX idx_wf_action_rule ON workflow_action(workflow_rule_id, execution_order);
```

**Action config schemas:**

```json
// FIELD_UPDATE
{"targetField": "close_date", "valueType": "FORMULA", "value": "TODAY()"}
{"targetField": "status", "valueType": "LITERAL", "value": "Active"}
{"targetField": "amount", "valueType": "FIELD", "value": "quoted_amount"}

// EMAIL_ALERT
{"templateId": "uuid", "recipients": {"type": "FIELD", "value": "owner_id"}}
{"templateId": "uuid", "recipients": {"type": "ROLE", "value": "support_manager"}}

// CREATE_RECORD
{"collectionId": "uuid", "fieldMappings": {"account_id": "{!record.account_id}", "amount": "{!record.amount}"}}

// OUTBOUND_MESSAGE
{"url": "https://api.example.com/webhook", "method": "POST", "headers": {}, "bodyTemplate": "..."}

// PUBLISH_EVENT
{"eventName": "OpportunityWon", "payload": {"opportunityId": "{!record.id}", "amount": "{!record.amount}"}}
```

Create `WorkflowEngine`:
- `evaluateAndExecute(collectionId, triggerType, oldRecord, newRecord, userId)`:
  1. Find active workflow rules for collection + trigger type, ordered by execution_order
  2. For each rule, evaluate filter_formula against record using `FormulaEvaluator` (from Phase 2.4)
  3. If filter matches, execute actions in order
  4. Log execution results in `workflow_execution_log`

```sql
CREATE TABLE workflow_execution_log (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    workflow_rule_id VARCHAR(36) NOT NULL,
    record_id VARCHAR(36) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,               -- SUCCESS, PARTIAL_FAILURE, FAILURE
    actions_executed INTEGER DEFAULT 0,
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    duration_ms INTEGER
);
```

**Integration point:** The `PhysicalTableStorageAdapter`'s `create()`, `update()`, and `delete()` methods call `WorkflowEngine.evaluateAndExecute()` after the database operation succeeds. Workflow field updates trigger recursive evaluation (up to 5 levels deep to prevent infinite loops).

---

### 4.2 Approval Processes

**Purpose:** Formalize record approval with multi-step routing, escalation, and delegation.

**Use Cases:**
- Discount > 20% on a quote requires manager approval
- Expense reports over $5,000 require VP approval after manager approval
- Purchase orders route to department head then finance
- Hiring requisitions need HR review then budget approval

**Technical Specifics:**

New migration `V23__add_approval_tables.sql`:

```sql
CREATE TABLE approval_process (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN DEFAULT true,
    entry_criteria TEXT,                        -- formula: which records can enter this process
    record_editability VARCHAR(20) DEFAULT 'LOCKED',  -- LOCKED or ADMIN_ONLY while in approval
    initial_submitter_field VARCHAR(100),       -- field for initial submitter (default: owner)
    on_submit_field_updates JSONB DEFAULT '[]',
    on_approval_field_updates JSONB DEFAULT '[]',
    on_rejection_field_updates JSONB DEFAULT '[]',
    on_recall_field_updates JSONB DEFAULT '[]',
    allow_recall BOOLEAN DEFAULT true,
    execution_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_approval_process UNIQUE (tenant_id, collection_id, name)
);

CREATE TABLE approval_step (
    id VARCHAR(36) PRIMARY KEY,
    approval_process_id VARCHAR(36) NOT NULL REFERENCES approval_process(id) ON DELETE CASCADE,
    step_number INTEGER NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    entry_criteria TEXT,                        -- formula: when does this step activate
    approver_type VARCHAR(30) NOT NULL,        -- USER, ROLE, QUEUE, MANAGER_HIERARCHY, RELATED_USER
    approver_id VARCHAR(36),                   -- user/role/queue ID (null for MANAGER_HIERARCHY)
    approver_field VARCHAR(100),               -- for RELATED_USER type
    unanimity_required BOOLEAN DEFAULT false,  -- all approvers or first response
    escalation_timeout_hours INTEGER,          -- auto-escalate after N hours
    escalation_action VARCHAR(20),             -- APPROVE, REJECT, REASSIGN
    on_approve_action VARCHAR(20) DEFAULT 'NEXT_STEP',  -- NEXT_STEP, APPROVE_FINAL
    on_reject_action VARCHAR(20) DEFAULT 'REJECT_FINAL', -- REJECT_FINAL, PREVIOUS_STEP
    CONSTRAINT uq_approval_step UNIQUE (approval_process_id, step_number)
);

-- Runtime: tracks each record's approval status
CREATE TABLE approval_instance (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    approval_process_id VARCHAR(36) NOT NULL REFERENCES approval_process(id),
    collection_id VARCHAR(36) NOT NULL,
    record_id VARCHAR(36) NOT NULL,
    submitted_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    current_step_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED','RECALLED'))
);
CREATE INDEX idx_approval_record ON approval_instance(collection_id, record_id);

CREATE TABLE approval_step_instance (
    id VARCHAR(36) PRIMARY KEY,
    approval_instance_id VARCHAR(36) NOT NULL REFERENCES approval_instance(id) ON DELETE CASCADE,
    step_id VARCHAR(36) NOT NULL REFERENCES approval_step(id),
    assigned_to VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comments TEXT,
    acted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_step_status CHECK (status IN ('PENDING','APPROVED','REJECTED','REASSIGNED'))
);
CREATE INDEX idx_step_assigned ON approval_step_instance(assigned_to, status);
```

Create `ApprovalService`:
- `submitForApproval(collectionId, recordId, userId)` -> creates approval_instance, routes to first step
- `approve(stepInstanceId, userId, comments)` -> marks step approved, advances to next or completes
- `reject(stepInstanceId, userId, comments)` -> marks rejected, executes rejection actions
- `recall(instanceId, userId)` -> submitter cancels the approval request
- `reassign(stepInstanceId, newAssigneeId, userId)` -> delegate to another user
- `getMyPendingApprovals(userId)` -> all approval steps assigned to me

**Scheduled escalation:** A scheduled job (`ApprovalEscalationJob`) runs every 15 minutes, finds overdue approval steps, and applies the configured escalation action.

---

### 4.3 Flow Engine

**Purpose:** A general-purpose process automation engine for complex multi-step processes that go beyond simple workflow rules.

**Use Cases:**
- Lead conversion: create Account, Contact, and Opportunity from a Lead record
- Order fulfillment: create invoice, deduct inventory, send confirmation email, schedule delivery
- Employee onboarding: create user, assign equipment, schedule orientation, notify HR

**Technical Specifics:**

New migration `V24__add_flow_tables.sql`:

```sql
CREATE TABLE flow (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    flow_type VARCHAR(30) NOT NULL,            -- RECORD_TRIGGERED, SCHEDULED, AUTOLAUNCHED, SCREEN
    active BOOLEAN DEFAULT false,
    version INTEGER DEFAULT 1,
    trigger_config JSONB,                      -- collection, trigger type, criteria
    definition JSONB NOT NULL,                 -- the flow's nodes and edges
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_flow UNIQUE (tenant_id, name)
);

CREATE TABLE flow_execution (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    flow_id VARCHAR(36) NOT NULL REFERENCES flow(id),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_by VARCHAR(36),
    trigger_record_id VARCHAR(36),
    variables JSONB DEFAULT '{}',
    current_node_id VARCHAR(100),
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_flow_status CHECK (status IN ('RUNNING','COMPLETED','FAILED','WAITING','CANCELLED'))
);
CREATE INDEX idx_flow_exec_status ON flow_execution(tenant_id, status);
```

**Flow definition JSONB structure:**

```json
{
  "nodes": [
    {"id": "start", "type": "START"},
    {"id": "check_amount", "type": "DECISION", "config": {
      "conditions": [
        {"label": "High Value", "formula": "{!record.Amount} > 50000", "nextNode": "create_task"},
        {"label": "Default", "nextNode": "send_email"}
      ]
    }},
    {"id": "create_task", "type": "CREATE_RECORD", "config": {
      "collection": "task",
      "fields": {"subject": "Review high-value deal: {!record.Name}", "assigned_to": "{!record.OwnerId}"}
    }, "nextNode": "send_email"},
    {"id": "send_email", "type": "EMAIL", "config": {
      "templateId": "uuid", "to": "{!record.OwnerId}"
    }, "nextNode": "end"},
    {"id": "end", "type": "END"}
  ]
}
```

**Node types:**
- START, END
- ASSIGNMENT (set variable or field values)
- DECISION (branching based on conditions)
- CREATE_RECORD, UPDATE_RECORD, DELETE_RECORD
- GET_RECORDS (query into a variable)
- LOOP (iterate over a collection variable)
- EMAIL (send email)
- ACTION (invoke a script)
- WAIT (pause until condition or time)
- SUBFLOW (call another flow)
- FAULT_HANDLER (error handling)

Create `FlowExecutionEngine` that interprets the flow definition, walking nodes and executing actions. Maintains execution state in `flow_execution.variables` for resumability (WAIT nodes).

---

### 4.4 Scheduled Jobs

**Purpose:** Run automation on a schedule (nightly batch processing, weekly reports, data cleanup).

**Use Cases:**
- Nightly: recalculate all roll-up summary fields
- Weekly: send pipeline report to sales leadership
- Monthly: archive cases older than 1 year
- Hourly: sync data from external system

**Technical Specifics:**

New migration `V25__add_scheduled_jobs.sql`:

```sql
CREATE TABLE scheduled_job (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    job_type VARCHAR(20) NOT NULL,             -- FLOW, SCRIPT, REPORT_EXPORT
    job_reference_id VARCHAR(36),              -- flow_id, script_id, or report_id
    cron_expression VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) DEFAULT 'UTC',
    active BOOLEAN DEFAULT true,
    last_run_at TIMESTAMP WITH TIME ZONE,
    last_status VARCHAR(20),
    next_run_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_scheduled_job UNIQUE (tenant_id, name)
);

CREATE TABLE job_execution_log (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL REFERENCES scheduled_job(id),
    status VARCHAR(20) NOT NULL,
    records_processed INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms INTEGER
);
```

Implement using Spring's `@Scheduled` with a dispatcher that:
1. Every minute, queries `scheduled_job` where `active=true AND next_run_at <= NOW()`
2. For each due job, submits execution to a thread pool
3. Updates `last_run_at`, `last_status`, and calculates `next_run_at` from cron expression
4. Logs execution to `job_execution_log`

Use a distributed lock (Redis `SET NX EX`) keyed by job ID to prevent duplicate execution across multiple control plane instances.

---

### 4.5 Formula Fields and Roll-Up Summaries

**Purpose:** Calculated fields that derive their value from other fields (formulas) or from child records (roll-ups).

**Use Cases:**
- `FullName = FirstName & " " & LastName` (formula)
- `AnnualRevenue = MonthlyRevenue * 12` (formula)
- `DaysSinceCreated = TODAY() - CreatedDate` (formula)
- `TotalLineItems = COUNT(LineItem)` (roll-up on parent)
- `TotalAmount = SUM(LineItem.Amount)` (roll-up on parent)
- `OldestCase = MIN(Case.CreatedDate)` (roll-up on parent)

**Technical Specifics:**

Formula fields (type=FORMULA) have no database column. They are computed at query time:
- **Option A (SQL):** `PhysicalTableStorageAdapter` translates the formula into a SQL expression in the SELECT clause. This is performant but limits formula complexity.
- **Option B (Application):** The formula is evaluated in Java after the query returns. This supports complex functions but adds overhead.

Recommendation: Use Option A for simple arithmetic and string concatenation. Fall back to Option B for complex functions (IF, CASE, date math).

Add formula translation to `PhysicalTableStorageAdapter.query()`:
```java
// For a formula field "annual_revenue" with expression "monthly_revenue * 12":
// SELECT *, (monthly_revenue * 12) AS annual_revenue FROM tbl_accounts ...
```

Roll-up summary fields are stored in the parent table and recalculated when child records change:
- On child insert/update/delete, `RollupRecalculationService` recalculates the parent's roll-up field
- Use SQL: `UPDATE tbl_account SET total_revenue = (SELECT SUM(amount) FROM tbl_opportunity WHERE account_id = ? AND stage = 'Closed Won') WHERE id = ?`
- Triggered from `PhysicalTableStorageAdapter` after child record mutations

Add `RollupRecalculationService` with async batch mode for bulk operations.

---

### 4.6 Email Templates and Alerts

**Purpose:** Template-based email sending used by workflow actions, approval notifications, and scheduled alerts.

**Use Cases:**
- Workflow sends "Deal Closed" email to account team when opportunity is won
- Approval sends "Pending Your Approval" email with approve/reject links
- Scheduled alert sends "Overdue Tasks" summary to managers

**Technical Specifics:**

New migration `V26__add_email_tables.sql`:

```sql
CREATE TABLE email_template (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    subject VARCHAR(500) NOT NULL,             -- supports merge fields: "Deal {!Opportunity.Name} Closed"
    body_html TEXT NOT NULL,                   -- HTML with merge fields
    body_text TEXT,                            -- plain text fallback
    related_collection_id VARCHAR(36),         -- which collection's fields are available
    folder VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_email_template UNIQUE (tenant_id, name)
);

CREATE TABLE email_log (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    template_id VARCHAR(36),
    recipient_email VARCHAR(320) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    source VARCHAR(30),                        -- WORKFLOW, APPROVAL, SCHEDULED, MANUAL
    source_id VARCHAR(36),
    error_message TEXT,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

Create `EmailService`:
- `sendFromTemplate(templateId, recordId, recipientUserId)` -> resolves merge fields, sends via SMTP
- `sendDirect(to, subject, bodyHtml)` -> sends without template
- `resolveTemplate(templateId, recordId)` -> returns resolved subject + body

Merge field resolution: `{!Opportunity.Name}` -> queries the record and substitutes field values. Supports relationship traversal: `{!Opportunity.Account.Name}`.

SMTP configuration via `application.yml`:
```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
```

---

## Phase 5: Integration and Scripting

Opens the platform to external systems and custom logic.

---

### 5.1 Server-Side Scripting Engine

**Purpose:** Allow tenant developers to write custom business logic that runs on the server in a sandboxed environment.

**Use Cases:**
- Before-save trigger: validate that a discount doesn't exceed the configured maximum for the product line
- After-save trigger: sync the record to an external CRM via REST API
- Scheduled script: nightly batch job to recalculate territory assignments
- Custom API endpoint: complex pricing calculation that can't be expressed as a formula

**Technical Specifics:**

Use GraalVM JavaScript engine (`org.graalvm.polyglot`) for sandboxed execution.

Add dependency to `control-plane-app/pom.xml`:
```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>24.0.0</version>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>24.0.0</version>
    <type>pom</type>
</dependency>
```

New migration `V27__add_script_tables.sql`:

```sql
CREATE TABLE script (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    script_type VARCHAR(30) NOT NULL,
    language VARCHAR(20) DEFAULT 'javascript',
    source_code TEXT NOT NULL,
    active BOOLEAN DEFAULT true,
    version INTEGER DEFAULT 1,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_script UNIQUE (tenant_id, name),
    CONSTRAINT chk_script_type CHECK (script_type IN (
        'BEFORE_TRIGGER','AFTER_TRIGGER','SCHEDULED','API_ENDPOINT',
        'VALIDATION','EVENT_HANDLER','EMAIL_HANDLER'
    ))
);

-- Bind scripts to collections as triggers
CREATE TABLE script_trigger (
    id VARCHAR(36) PRIMARY KEY,
    script_id VARCHAR(36) NOT NULL REFERENCES script(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    trigger_event VARCHAR(20) NOT NULL,        -- INSERT, UPDATE, DELETE
    execution_order INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT true,
    CONSTRAINT uq_script_trigger UNIQUE (script_id, collection_id, trigger_event)
);

CREATE TABLE script_execution_log (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    script_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(30),
    record_id VARCHAR(36),
    duration_ms INTEGER,
    cpu_ms INTEGER,
    queries_executed INTEGER DEFAULT 0,
    dml_rows INTEGER DEFAULT 0,
    callouts INTEGER DEFAULT 0,
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

Create `ScriptExecutionEngine`:
- Manages GraalVM `Context` pool per tenant
- Injects the `ctx` API object into script scope
- Enforces governor limits (CPU time via `Context.close()` with timeout, query/DML counters in the API)
- Returns script output or throws `ScriptExecutionException`

**Governor limits enforcement:**
```java
Context context = Context.newBuilder("js")
    .allowHostAccess(HostAccess.NONE)          // no Java interop
    .allowIO(false)                             // no file system
    .allowCreateThread(false)                   // no threading
    .option("engine.MaximumCompilationTime", "5000")
    .build();
```

The `ctx` API object (injected as `ProxyObject`):

```javascript
ctx.record         // current record (Map<String, Object>)
ctx.oldRecord      // previous values (in update triggers)
ctx.user           // {id, email, name, profileId, roles}
ctx.tenant         // {id, slug, name}

ctx.query(emfql)   // returns List<Map>; increments query counter
ctx.insert(collection, records)   // returns created records; increments DML counter
ctx.update(collection, records)   // increments DML counter
ctx.delete(collection, ids)       // increments DML counter

ctx.http.get(url, headers)        // HTTP callout; increments callout counter
ctx.http.post(url, body, headers)
ctx.http.put(url, body, headers)

ctx.email.send(to, templateId, recordId)
ctx.event.publish(eventName, payload)
ctx.log.info(message)
ctx.log.error(message)

ctx.cache.get(key)
ctx.cache.set(key, value, ttlSeconds)
```

---

### 5.2 Webhooks

**Purpose:** Push real-time event notifications to external systems when things happen in EMF.

**Use Cases:**
- Notify Slack when a high-value deal is closed
- Sync new contacts to Mailchimp when created
- Trigger external fulfillment system when order status changes to "Shipped"
- Send data to analytics platform on every record change

**Technical Specifics:**

New migration `V28__add_webhook_tables.sql`:

```sql
CREATE TABLE webhook (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    events JSONB NOT NULL,                     -- ["record.created", "record.updated"]
    collection_id VARCHAR(36) REFERENCES collection(id),  -- null = all collections
    filter_formula TEXT,                        -- only fire when true
    headers JSONB DEFAULT '{}',                -- custom HTTP headers
    secret VARCHAR(200),                       -- HMAC-SHA256 signing secret
    active BOOLEAN DEFAULT true,
    retry_policy JSONB DEFAULT '{"maxRetries": 3, "backoffSeconds": [10, 60, 300]}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook UNIQUE (tenant_id, name)
);

CREATE TABLE webhook_delivery (
    id VARCHAR(36) PRIMARY KEY,
    webhook_id VARCHAR(36) NOT NULL REFERENCES webhook(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    attempt_count INTEGER DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED','RETRYING'))
);
CREATE INDEX idx_delivery_retry ON webhook_delivery(status, next_retry_at);
```

Create `WebhookService`:
- `fireWebhooks(tenantId, eventType, collectionId, record)`:
  1. Find matching webhooks (by event type and collection)
  2. Evaluate filter_formula if present
  3. Build payload JSON
  4. Sign payload with HMAC-SHA256 using webhook's secret
  5. Queue delivery (async via Kafka topic `emf.webhooks.outbound`)

Create `WebhookDeliveryWorker` (Kafka consumer):
- Consumes from `emf.webhooks.outbound`
- POSTs to webhook URL with headers including `X-EMF-Signature`
- On failure, schedules retry per retry_policy
- Logs delivery attempt in `webhook_delivery`

---

### 5.3 Connected Apps

**Purpose:** Allow external applications to authenticate and access the EMF API with scoped permissions.

**Use Cases:**
- Mobile app authenticates with OAuth2 to access EMF data
- Data warehouse uses client credentials to bulk-extract data
- Partner portal uses limited-scope tokens to see only shared records

**Technical Specifics:**

New migration `V29__add_connected_apps.sql`:

```sql
CREATE TABLE connected_app (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(200) NOT NULL,
    redirect_uris JSONB DEFAULT '[]',
    scopes JSONB DEFAULT '["api"]',
    ip_restrictions JSONB DEFAULT '[]',
    rate_limit_per_hour INTEGER DEFAULT 10000,
    active BOOLEAN DEFAULT true,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_connected_app UNIQUE (tenant_id, name)
);
```

Extend the gateway's `JwtAuthenticationFilter` to also accept tokens issued by EMF itself (not just external OIDC providers). The gateway acts as both a resource server and a token issuer for connected apps.

---

### 5.4 Bulk and Composite APIs

**Purpose:** Handle large-scale data operations efficiently.

**Use Cases:**
- Initial data load: import 100,000 accounts from a CSV
- Nightly sync: upsert 50,000 records from external system
- Dashboard: fetch 5 different resources in a single API call

**Technical Specifics:**

**Bulk API** - new controller at `/api/v1/bulk`:
- `POST /api/v1/bulk/{collection}/insert` - accepts CSV or JSON array, returns job ID
- `POST /api/v1/bulk/{collection}/update` - same
- `POST /api/v1/bulk/{collection}/upsert` - match on external ID field
- `POST /api/v1/bulk/{collection}/delete` - accepts ID list
- `GET /api/v1/bulk/jobs/{jobId}` - check job status
- `GET /api/v1/bulk/jobs/{jobId}/results` - download results (success/error per row)

Bulk operations are async. They:
1. Accept the payload and create a job record
2. Process in batches of 200 records
3. Run validation rules and triggers in bulk mode (governor limits apply per batch, not per record)
4. Write results to a result file
5. Send notification when complete

**Composite API** - new controller at `/api/v1/composite`:
- `POST /api/v1/composite` - accepts array of sub-requests, executes in order, allows referencing previous results

```json
{
  "compositeRequest": [
    {"method": "POST", "url": "/api/v1/accounts", "body": {"name": "Acme"}, "referenceId": "newAccount"},
    {"method": "POST", "url": "/api/v1/contacts", "body": {"accountId": "@{newAccount.id}", "name": "John"}, "referenceId": "newContact"}
  ]
}
```

---

## Phase 6: Enterprise Features

The final phase adds capabilities that large organizations require.

---

### 6.1 Full-Text Search

**Purpose:** Search across all records in all collections from a single search bar.

**Use Cases:**
- User types "Acme" and sees matching Accounts, Contacts, and Opportunities
- Support agent searches case number "CS-1234" across all objects
- Sales rep searches a phone number to find the associated contact

**Technical Specifics:**

Use PostgreSQL full-text search (tsvector/tsquery) for initial implementation:

Add to `PhysicalTableStorageAdapter.initializeCollection()`:
```sql
-- For each collection table, add a search vector column
ALTER TABLE tbl_{collection} ADD COLUMN search_vector tsvector;
CREATE INDEX idx_{collection}_search ON tbl_{collection} USING gin(search_vector);

-- Trigger to auto-update search vector on insert/update
CREATE OR REPLACE FUNCTION update_{collection}_search_vector() RETURNS trigger AS $$
BEGIN
  NEW.search_vector := to_tsvector('english', coalesce(NEW.name,'') || ' ' || coalesce(NEW.description,''));
  RETURN NEW;
END $$ LANGUAGE plpgsql;
```

The fields included in the search vector are configurable per collection (those with `enableSearch=true`).

New `SearchService`:
- `globalSearch(tenantId, query, collectionsToSearch, limit)` -> queries all searchable collections, returns ranked results

New `SearchController` at `/api/v1/search`:
- `GET /api/v1/search?q=acme&scope=account,contact&limit=25`

Results include: collection name, record ID, record name (display field), matching snippet, relevance score.

For larger deployments, add Elasticsearch as an optional backend. The `SearchService` checks configuration and delegates to either `PostgresSearchAdapter` or `ElasticsearchSearchAdapter`.

---

### 6.2 EMF-QL Query Language

**Purpose:** A structured query language for programmatic data access, similar to Salesforce SOQL.

**Use Cases:**
- Script: `ctx.query("SELECT Name, Amount FROM Opportunity WHERE Stage = 'Closed Won' AND Amount > 10000")`
- Report builder internally generates EMF-QL
- External integration queries via API: `GET /api/v1/query?q=SELECT...`
- List view filters translate to EMF-QL

**Technical Specifics:**

Create `EmfQlParser` using ANTLR4 or a hand-written recursive descent parser:

```
Grammar:
  query       = SELECT fields FROM collection [WHERE condition] [ORDER BY orderSpec] [LIMIT n] [OFFSET n]
  fields      = field (',' field)*
  field       = fieldName | relationship '.' fieldName | aggregateFunc '(' fieldName ')'
  condition   = comparison ((AND | OR) comparison)*
  comparison  = field operator value
  operator    = '=' | '!=' | '>' | '<' | '>=' | '<=' | 'LIKE' | 'IN' | 'NOT IN' | 'INCLUDES' | 'EXCLUDES'
  value       = string | number | boolean | NULL | datelit | list
  datelit     = TODAY | YESTERDAY | THIS_WEEK | THIS_MONTH | THIS_QUARTER | THIS_YEAR | LAST_N_DAYS ':' n
  orderSpec   = field (ASC | DESC) (',' field (ASC | DESC))*
```

Create `EmfQlExecutor`:
1. Parse query into AST
2. Validate collection and field names against tenant's metadata
3. Check object-level and field-level permissions
4. Translate to SQL (using `PhysicalTableStorageAdapter`'s table/column mapping)
5. Inject sharing model WHERE clauses
6. Execute and return results

New controller endpoint: `GET /api/v1/query?q={emfql}` with JSON response.

---

### 6.3 Notification System

**Purpose:** Multi-channel notifications to keep users informed about events that matter to them.

**Use Cases:**
- In-app bell notification when an approval is assigned to you
- Email when a record you follow is modified
- Slack notification when a critical case is created
- SMS for urgent alerts (e.g., system threshold exceeded)

**Technical Specifics:**

New migration `V30__add_notification_tables.sql`:

```sql
CREATE TABLE notification (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    title VARCHAR(200) NOT NULL,
    body VARCHAR(2000),
    type VARCHAR(30) NOT NULL,                 -- APPROVAL, RECORD_UPDATE, MENTION, SYSTEM, WORKFLOW
    entity_type VARCHAR(50),                   -- collection name
    entity_id VARCHAR(36),                     -- record id
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notification_user ON notification(user_id, is_read, created_at DESC);

CREATE TABLE notification_preference (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    notification_type VARCHAR(30) NOT NULL,
    channel_email BOOLEAN DEFAULT true,
    channel_in_app BOOLEAN DEFAULT true,
    channel_slack BOOLEAN DEFAULT false,
    channel_sms BOOLEAN DEFAULT false,
    CONSTRAINT uq_notif_pref UNIQUE (user_id, notification_type)
);
```

Create `NotificationService`:
- `notify(tenantId, userId, title, body, type, entityType, entityId)` -> routes to enabled channels
- `getUnread(userId)` -> returns unread notifications
- `markRead(notificationId)` -> marks as read

Add WebSocket endpoint `/ws/notifications` for real-time in-app delivery. Use Spring WebSocket with STOMP.

---

### 6.4 File and Document Management

**Purpose:** Attach files to records and manage documents.

**Use Cases:**
- Attach a signed contract PDF to an Opportunity record
- Upload a company logo to an Account
- Attach receipts to expense report records
- Generate an invoice PDF from record data

**Technical Specifics:**

New migration `V31__add_file_tables.sql`:

```sql
CREATE TABLE file_attachment (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    record_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(200) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,         -- S3 key
    description VARCHAR(1000),
    version INTEGER DEFAULT 1,
    uploaded_by VARCHAR(36) NOT NULL REFERENCES platform_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_attachment_record ON file_attachment(collection_id, record_id);
```

Use S3-compatible storage (AWS S3 or MinIO for self-hosted). Add to `application.yml`:
```yaml
emf:
  files:
    storage: s3
    bucket: ${FILE_BUCKET:emf-files}
    endpoint: ${S3_ENDPOINT:}     # empty = AWS, set for MinIO
    region: ${AWS_REGION:us-east-1}
    max-file-size-mb: 25
    allowed-types: ["application/pdf","image/*","text/*","application/msword","application/vnd.openxmlformats*"]
```

New `FileService` and `FileController`:
- `POST /api/v1/{collection}/{recordId}/files` - multipart upload
- `GET /api/v1/{collection}/{recordId}/files` - list attachments
- `GET /api/v1/files/{fileId}/download` - presigned S3 URL redirect
- `DELETE /api/v1/files/{fileId}` - delete attachment

---

### 6.5 Data Import/Export

**Purpose:** Bulk data import from CSV/Excel and export for backup or analysis.

**Use Cases:**
- Initial data migration from legacy system via CSV upload
- HR uploads employee list from Excel spreadsheet
- Nightly export of all Accounts to data warehouse
- Admin imports updated pricing from a vendor spreadsheet

**Technical Specifics:**

New migration `V32__add_import_tables.sql`:

```sql
CREATE TABLE data_import_job (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36) NOT NULL REFERENCES collection(id),
    file_name VARCHAR(500) NOT NULL,
    operation VARCHAR(20) NOT NULL,            -- INSERT, UPDATE, UPSERT
    external_id_field VARCHAR(100),            -- for UPSERT matching
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_rows INTEGER,
    processed_rows INTEGER DEFAULT 0,
    success_rows INTEGER DEFAULT 0,
    error_rows INTEGER DEFAULT 0,
    column_mapping JSONB,                      -- csv_column -> field_name mapping
    error_file_key VARCHAR(500),               -- S3 key for error file
    uploaded_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_import_op CHECK (operation IN ('INSERT','UPDATE','UPSERT')),
    CONSTRAINT chk_import_status CHECK (status IN ('PENDING','MAPPING','PROCESSING','COMPLETED','FAILED'))
);
```

New `DataImportService`:
1. Upload CSV/Excel file (stored in S3)
2. Parse headers, present column mapping UI (CSV column -> collection field)
3. Validate mapping (required fields, type compatibility)
4. Process in batches of 200: validate, run triggers, insert/update
5. Collect errors per row with reason
6. Generate error file (CSV with original row + error column)
7. Send notification when complete

New `DataImportController` at `/control/imports`:
- `POST /control/imports/upload` - upload file, returns job with detected columns
- `PUT /control/imports/{jobId}/mapping` - set column mapping
- `POST /control/imports/{jobId}/execute` - start processing
- `GET /control/imports/{jobId}` - check status
- `GET /control/imports/{jobId}/errors` - download error file

---

### 6.6 Sandbox and Environment Management

**Purpose:** Extend the existing ConfigPackage system into a full environment management solution with sandbox creation, comparison, and deployment.

**Use Cases:**
- Developer creates a sandbox to build and test new features
- Admin compares sandbox config vs production before deploying
- CI/CD pipeline deploys a change set from Git to production
- QA refreshes their sandbox with latest production schema

**Technical Specifics:**

New migration `V33__add_environment_tables.sql`:

```sql
CREATE TABLE environment (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(100) NOT NULL,
    environment_type VARCHAR(20) NOT NULL,     -- PRODUCTION, FULL_SANDBOX, PARTIAL_SANDBOX, DEVELOPER_SANDBOX
    source_environment_id VARCHAR(36) REFERENCES environment(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING',
    database_schema VARCHAR(100),              -- the PostgreSQL schema for this environment
    last_refreshed_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_environment UNIQUE (tenant_id, name),
    CONSTRAINT chk_env_type CHECK (environment_type IN ('PRODUCTION','FULL_SANDBOX','PARTIAL_SANDBOX','DEVELOPER_SANDBOX')),
    CONSTRAINT chk_env_status CHECK (status IN ('PROVISIONING','ACTIVE','REFRESHING','LOCKED','DECOMMISSIONED'))
);

-- Deployment tracking (extends existing package system)
CREATE TABLE deployment (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    package_id VARCHAR(36) NOT NULL REFERENCES package(id),
    source_environment_id VARCHAR(36) NOT NULL REFERENCES environment(id),
    target_environment_id VARCHAR(36) NOT NULL REFERENCES environment(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    validation_result JSONB,
    deployed_by VARCHAR(36) NOT NULL,
    deployed_at TIMESTAMP WITH TIME ZONE,
    rolled_back_at TIMESTAMP WITH TIME ZONE,
    rollback_package_id VARCHAR(36) REFERENCES package(id),  -- snapshot for rollback
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_deploy_status CHECK (status IN ('PENDING','VALIDATING','VALIDATED','DEPLOYING','DEPLOYED','FAILED','ROLLED_BACK'))
);
```

New `EnvironmentService`:
- `createSandbox(tenantId, name, type, sourceEnvironmentId)` -> provisions new schema, copies config (and optionally data)
- `refreshSandbox(environmentId)` -> re-copies from source
- `compareEnvironments(sourceId, targetId)` -> returns diff of all configuration entities
- `deploy(packageId, sourceEnvId, targetEnvId)` -> validate then apply package
- `rollback(deploymentId)` -> revert using pre-deployment snapshot

Enhance existing `PackageService`:
- Before deploy, automatically create a rollback snapshot of the target's current state
- Add `rollback(deploymentId)` that applies the rollback snapshot
- Add `validate(packageId, targetEnvId)` that does a dry-run and returns conflicts/warnings

---

### 6.7 Localization / i18n

**Purpose:** Support multiple languages for field labels, picklist values, error messages, and UI text.

**Use Cases:**
- French-speaking users see "Compte" instead of "Account"
- Picklist "Industry" shows values in the user's language
- Validation error messages displayed in user's locale

**Technical Specifics:**

New migration `V34__add_i18n_tables.sql`:

```sql
CREATE TABLE translation (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    locale VARCHAR(10) NOT NULL,               -- e.g., 'fr', 'de', 'ja'
    entity_type VARCHAR(50) NOT NULL,          -- COLLECTION, FIELD, PICKLIST_VALUE, VALIDATION_RULE, UI_LABEL
    entity_id VARCHAR(36) NOT NULL,
    field_name VARCHAR(50) NOT NULL,           -- which text field: 'displayName', 'label', 'errorMessage'
    translated_value TEXT NOT NULL,
    CONSTRAINT uq_translation UNIQUE (tenant_id, locale, entity_type, entity_id, field_name)
);
CREATE INDEX idx_translation_lookup ON translation(tenant_id, locale, entity_type, entity_id);
```

Create `TranslationService`:
- `getTranslation(tenantId, locale, entityType, entityId, fieldName)` -> returns translated value or null
- `setTranslation(tenantId, locale, entityType, entityId, fieldName, value)` -> create/update
- `getTranslationsForLocale(tenantId, locale)` -> bulk fetch for UI bootstrap

The gateway includes an `Accept-Language` header parser. The control plane's bootstrap config endpoint (`/ui/config/bootstrap`) returns translations for the requested locale alongside the default labels.

---

### 6.8 Mobile-Responsive API

**Purpose:** Provide optimized API responses for mobile clients with compact layouts and reduced payloads.

**Use Cases:**
- Mobile app shows compact record view (3-4 key fields instead of full layout)
- Offline sync: mobile app syncs only changed records since last sync
- Push notifications to mobile app for approvals

**Technical Specifics:**

New controller at `/api/v1/mobile`:
- `GET /api/v1/mobile/{collection}/{id}` -> returns compact layout fields only
- `GET /api/v1/mobile/{collection}/sync?since={timestamp}` -> returns records modified since timestamp
- `GET /api/v1/mobile/notifications` -> returns pending notifications

Add `If-Modified-Since` and `ETag` header support to all GET endpoints for efficient mobile caching.

Compact layout configuration stored in `page_layout` with `layout_type = 'COMPACT'`. Mobile clients request compact layouts by default.

---

## Cross-Cutting Concerns

These apply across all phases and should be implemented incrementally.

---

### CC.1 Governor Limits Enforcement

**Purpose:** Prevent any single tenant from consuming disproportionate platform resources.

**Implementation:**
- Create `GovernorLimitsService` that checks tenant limits before operations
- Integrate with `TenantContextHolder` to load limits from cached tenant config
- Check API call count (Redis counter per tenant per day)
- Check storage usage (track via metadata table)
- Check concurrent API requests (Redis counter)
- Return HTTP 429 with limit details when exceeded

---

### CC.2 Comprehensive Error Handling

**Purpose:** Consistent, informative error responses across all endpoints.

**Implementation:**
- Extend existing `GlobalExceptionHandler` in control plane
- Extend existing `GlobalErrorHandler` in gateway
- Standard error envelope: `{error: {status, code, message, details[], traceId}}`
- Map all custom exceptions to appropriate HTTP status codes
- Include trace ID from OpenTelemetry for debugging

---

### CC.3 Caching Strategy

**Purpose:** Maintain performance as the system scales.

**Implementation:**
- **L1 (in-process):** Caffeine cache for tenant config, collection metadata, permissions (5-min TTL)
- **L2 (distributed):** Redis for JWKS, session data, rate limiting, sharing calculations (configurable TTL)
- **Invalidation:** Kafka events trigger cache eviction in all instances
- Extend existing `@Cacheable` usage in `CollectionService` to all metadata services

---

### CC.4 Observability Enhancements

**Purpose:** Enterprise customers need detailed operational visibility.

**Implementation:**
- Add tenant_id as a dimension to all Micrometer metrics
- Add tenant_id to all log entries via MDC (existing structured logging)
- Create per-tenant usage dashboards (API calls, storage, active users)
- Add slow query logging (>1s) with full context
- Extend existing health indicators with per-tenant health status

---

### CC.5 Database Migration Strategy

**Purpose:** Manage the Flyway migrations across phases without breaking existing deployments.

**Implementation:**
- Migrations V8-V34 are numbered sequentially across phases
- Each migration is idempotent (uses IF NOT EXISTS, checks before ALTER)
- Tenant-scoped tables live in the tenant schema; platform tables live in public schema
- Migration runner applies platform migrations first, then iterates over all active tenants to apply tenant migrations
- Add `V_TENANT_1__baseline.sql` for tenant schema initialization

---

## Dependency Graph

```
Phase 1 (Foundation)
  1.1 Tenant Entity
  1.2 Schema Isolation        <- 1.1
  1.3 Tenant Resolution       <- 1.1, 1.2
  1.4 User Entity             <- 1.1
  1.5 Profiles/PermSets       <- 1.4
  1.6 Sharing Model           <- 1.5
  1.7 Tenant OIDC             <- 1.1, 1.3

Phase 2 (Object Model)        <- Phase 1 complete
  2.1 Field Types             <- 1.5 (field permissions)
  2.2 Picklist Management     <- 2.1
  2.3 Relationships           <- 2.1
  2.4 Validation Rules        <- 2.1
  2.5 Record Types            <- 2.2, 2.3
  2.6 Field History           <- 1.4 (user tracking)
  2.7 Setup Audit             <- 1.4

Phase 3 (UI + Reporting)      <- Phase 2 complete
  3.1 Page Layouts            <- 2.5 (record types)
  3.2 List Views              <- 2.1 (field types)
  3.3 Report Builder          <- 2.3 (relationships), 3.2 (filter syntax)
  3.4 Dashboard Builder       <- 3.3
  3.5 Data Export              <- 3.3

Phase 4 (Automation)          <- Phase 2.4 (formula evaluator)
  4.1 Workflow Rules          <- 2.4 (FormulaEvaluator)
  4.2 Approval Processes      <- 4.1, 1.4 (users), 1.5 (profiles)
  4.3 Flow Engine             <- 4.1
  4.4 Scheduled Jobs          <- 4.1, 4.3
  4.5 Formula Fields          <- 2.4 (FormulaEvaluator), 2.3 (relationships for roll-ups)
  4.6 Email Templates         <- 4.1

Phase 5 (Integration)         <- Phase 4 complete
  5.1 Scripting Engine        <- 4.1 (trigger points)
  5.2 Webhooks                <- 4.1 (event system)
  5.3 Connected Apps          <- 1.7 (OIDC)
  5.4 Bulk/Composite API      <- 2.1 (field types), 5.1 (triggers)

Phase 6 (Enterprise)          <- Phases 3-5 complete
  6.1 Full-Text Search        <- 2.1 (field types)
  6.2 EMF-QL                  <- 2.3 (relationships), 1.6 (sharing model)
  6.3 Notifications           <- 4.2 (approvals), 4.1 (workflows)
  6.4 File Management         <- 2.1 (attachment field type)
  6.5 Data Import/Export      <- 2.4 (validation), 5.1 (triggers)
  6.6 Sandbox Management      <- existing ConfigPackage system
  6.7 Localization            <- 2.2 (picklists), 3.1 (layouts)
  6.8 Mobile API              <- 3.1 (compact layouts)
```
