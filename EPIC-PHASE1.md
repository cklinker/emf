# EPIC: Phase 1 - Multi-Tenancy and Permission Foundation

> **Goal:** Transform EMF from a single-tenant framework into a multi-tenant enterprise platform with a complete permission model.
>
> **Outcome:** Multiple organizations share the same infrastructure with fully isolated data, independently configurable schemas, and granular object/field/record-level security.

---

## Task Execution Order

Tasks are grouped into **work streams** that can execute in parallel where possible. Within each stream, tasks are sequential. Dependencies across streams are noted explicitly.

```
Stream A: Tenant Core          Stream B: Users & Auth       Stream C: Permissions
---------------------          --------------------         --------------------
A1  Tenant migration           B1  User migration           C1  Permission migration
A2  Tenant entity                  (blocked by A3)              (blocked by B3)
A3  Tenant service             B2  User entity              C2  Profile entity
A4  Tenant controller          B3  User service             C3  ObjPerm entity
A5  TenantContextHolder        B4  User controller          C4  FieldPerm entity
A6  Tenant isolation mig       B5  JIT provisioning         C5  SysPerm entity
A7  Tenant-aware entities          (blocked by A10)         C6  PermSet entity
A8  TenantSchemaManager        B6  Login history            C7  PermissionResolver
A9  Tenant-aware repos         B7  User mgmt UI             C8  Profile service
A10 Tenant-aware services      B8  User detail UI           C9  PermSet service
A11 Tenant resolution filter                                C10 Profile/PermSet controller
A12 Header propagation         Stream D: Sharing            C11 Gateway authz refactor
A13 Tenant admin UI            --------------------         C12 Default profile seeding
A14 Tenant dashboard UI        D1  Sharing migration        C13 Permission admin UI
                                   (blocked by C7)          C14 Field perm editor UI
Stream E: OIDC Enhancement     D2  OWD entity
---------------------          D3  Role hierarchy
E1  OIDC tenant scoping        D4  Group entity
    (blocked by A10)           D5  SharingRule entity
E2  Multi-provider JWT         D6  RecordShare entity
    (blocked by E1)            D7  RecordAccessService
E3  OIDC admin UI updates      D8  Storage adapter update
                               D9  Sharing admin UI
Stream F: Cross-Cutting        D10 Role hierarchy UI
---------------------
F1  Setup audit trail
    (blocked by B3)
F2  SetupAuditService
F3  Governor limits
F4  Test suite updates
F5  Setup audit UI
F6  Governor limits UI
```

**Critical path:** A1 -> A2 -> A3 -> A5 -> A6 -> A7 -> A9 -> A10 -> B1 -> B3 -> C1 -> C7 -> D1 -> D7

---

## Stream A: Tenant Core Infrastructure

### A1: Tenant Database Migration

**Purpose:** Create the tenant table that serves as the root entity for all multi-tenant operations.

**Flyway file:** `V8__add_tenant_table.sql`

```sql
CREATE TABLE tenant (
    id            VARCHAR(36)  PRIMARY KEY,
    slug          VARCHAR(63)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    edition       VARCHAR(20)  NOT NULL DEFAULT 'PROFESSIONAL',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING',
    settings      JSONB        NOT NULL DEFAULT '{}',
    limits        JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_edition CHECK (edition IN ('FREE','PROFESSIONAL','ENTERPRISE','UNLIMITED')),
    CONSTRAINT chk_tenant_status  CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','DECOMMISSIONED')),
    CONSTRAINT chk_tenant_slug    CHECK (slug ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$')
);
CREATE INDEX idx_tenant_status ON tenant(status);
```

**Acceptance criteria:**
- Migration runs cleanly on fresh and existing databases
- Constraint checks pass for valid/invalid slugs, editions, statuses
- No impact on existing tables

**Integration points:** None -- this is the foundation.

---

### A2: Tenant Entity Class

**Purpose:** JPA entity following the existing `BaseEntity` pattern.

**File:** `com.emf.controlplane.entity.Tenant`

```java
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {
    // Fields matching V8 schema
    @Column(name = "slug", nullable = false, unique = true, length = 63)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "edition", nullable = false, length = 20)
    private String edition = "PROFESSIONAL";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PROVISIONING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits", columnDefinition = "jsonb")
    private String limits = "{}";

    // Constructors: Tenant(), Tenant(String slug, String name)
    // Getters/setters for all fields
}
```

**Conventions followed:**
- Extends `BaseEntity` (gets id, createdAt, updatedAt, equals/hashCode)
- `@EntityListeners(AuditingEntityListener.class)` inherited from BaseEntity
- UUID generated in `BaseEntity` protected constructor
- JSONB fields use `@JdbcTypeCode(SqlTypes.JSON)` (same as `Field.constraints`)

**Integration points:**
- Referenced as FK by most other entities (User, Collection, Role, etc.)
- `TenantRepository`, `TenantService`, and `TenantContextHolder` depend on this class

---

### A3: Tenant Service and Repository

**Purpose:** CRUD operations for tenants with provisioning orchestration.

**Files:**
- `com.emf.controlplane.repository.TenantRepository`
- `com.emf.controlplane.service.TenantService`

**Repository interface:**

```java
public interface TenantRepository extends JpaRepository<Tenant, String> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByIdAndStatus(String id, String status);
    boolean existsBySlug(String slug);
    List<Tenant> findByStatus(String status);
    Page<Tenant> findAll(Pageable pageable);
}
```

**Service methods:**

```java
@Service
public class TenantService {
    // Dependencies: TenantRepository, TenantSchemaManager (A8), ConfigEventPublisher
    @Transactional
    public Tenant createTenant(CreateTenantRequest request)
    // Validates slug uniqueness via existsBySlug()
    // Sets status=PROVISIONING, creates entity
    // Calls TenantSchemaManager.provisionSchema() (A8)
    // Sets status=ACTIVE
    // Publishes emf.config.tenant.changed event

    @Transactional(readOnly = true)
    public Tenant getTenant(String id)

    @Transactional(readOnly = true)
    public Tenant getTenantBySlug(String slug)

    @Transactional(readOnly = true)
    public Page<Tenant> listTenants(Pageable pageable)

    @Transactional
    public Tenant updateTenant(String id, UpdateTenantRequest request)

    @Transactional
    public void suspendTenant(String id)  // sets status=SUSPENDED

    @Transactional
    public void activateTenant(String id) // sets status=ACTIVE

    @Transactional
    public GovernorLimits getGovernorLimits(String tenantId)
    // Parses tenant.limits JSONB into GovernorLimits record
}
```

**Error handling:** Follows existing patterns -- `DuplicateResourceException` for slug conflicts, `ResourceNotFoundException` for missing tenant, `ValidationException` for invalid slug format.

**Integration points:**
- **A8 (TenantSchemaManager):** Called during `createTenant()` to provision database schema
- **C12 (Default profile seeding):** Called after schema provisioning to seed System Administrator profile
- **F3 (Governor limits):** `getGovernorLimits()` provides the limits data that `GovernorLimitsService` enforces

**DTO definitions:**

```java
public record CreateTenantRequest(
    @NotBlank String slug,
    @NotBlank String name,
    String edition,          // defaults to PROFESSIONAL
    Map<String, Object> settings,
    Map<String, Object> limits
) {}

public record UpdateTenantRequest(
    String name,
    String edition,
    Map<String, Object> settings,
    Map<String, Object> limits
) {}

public record GovernorLimits(
    int apiCallsPerDay,         // default 100000
    int storageGb,              // default 10
    int maxUsers,               // default 100
    int maxCollections,         // default 200
    int maxFieldsPerCollection, // default 500
    int maxWorkflows,           // default 50
    int maxReports              // default 200
) {}
```

---

### A4: Tenant Controller

**Purpose:** REST endpoints for platform-level tenant administration.

**File:** `com.emf.controlplane.controller.TenantController`

**Base path:** `/platform/tenants` (separate from `/control` which is tenant-scoped)

**Security:** Requires `PLATFORM_ADMIN` role (a new super-admin role for cross-tenant operations, distinct from per-tenant `ADMIN`).

```java
@RestController
@RequestMapping("/platform/tenants")
@SecurityRequirement(name = "bearer-jwt")
public class TenantController {
    @GetMapping                                         // List tenants (paginated)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Page<TenantDto> listTenants(@PageableDefault(size = 20) Pageable pageable)

    @PostMapping                                        // Create tenant
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest request)

    @GetMapping("/{id}")                                // Get tenant
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public TenantDto getTenant(@PathVariable String id)

    @PutMapping("/{id}")                                // Update tenant
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public TenantDto updateTenant(@PathVariable String id, @Valid @RequestBody UpdateTenantRequest request)

    @PostMapping("/{id}/suspend")                       // Suspend tenant
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> suspendTenant(@PathVariable String id)

    @PostMapping("/{id}/activate")                      // Activate tenant
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> activateTenant(@PathVariable String id)

    @GetMapping("/{id}/limits")                         // Get governor limits
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public GovernorLimitsDto getLimits(@PathVariable String id)
}
```

**Integration points:**
- **A13 (Tenant admin UI):** The frontend calls these endpoints
- **F1 (Setup audit):** All mutations logged via `SetupAuditService`

---

### A5: TenantContextHolder

**Purpose:** Thread-local storage for current tenant ID. Set on every request, read by all services and repositories.

**File:** `com.emf.controlplane.tenant.TenantContextHolder`

```java
public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    public record TenantContext(String tenantId, String tenantSlug) {}

    public static void set(String tenantId, String tenantSlug)
    public static TenantContext get()
    public static String getTenantId()   // returns get().tenantId() or null
    public static String getTenantSlug() // returns get().tenantSlug() or null
    public static void clear()
    public static boolean isSet()
}
```

**Usage pattern:** Set in request filters (A11), read by repositories (A9) and services (A10), cleared in filter's `finally` block.

**Integration points:**
- **A11 (Tenant resolution filter):** Sets the context
- **A9 (Tenant-aware repos):** Reads `getTenantId()` for query filtering
- **A10 (Tenant-aware services):** Reads context for all business logic
- **B5 (JIT provisioning):** Reads tenant context to associate user with tenant

---

### A6: Add tenant_id to Existing Tables

**Purpose:** Every tenant-scoped resource needs a `tenant_id` foreign key for data isolation.

**Flyway file:** `V9__add_tenant_id_to_existing_tables.sql`

Tables to modify: `service`, `collection`, `role`, `policy`, `oidc_provider`, `ui_page`, `ui_menu`, `package`, `migration_run`

Strategy for existing data:
1. Create a default "platform" tenant
2. Add `tenant_id` as nullable
3. Backfill existing rows with the default tenant's ID
4. Set `tenant_id` to NOT NULL
5. Add FK constraint and index

```sql
-- Create default tenant for existing data
INSERT INTO tenant (id, slug, name, edition, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Organization', 'ENTERPRISE', 'ACTIVE', NOW(), NOW());

-- For each table (example: service)
ALTER TABLE service ADD COLUMN tenant_id VARCHAR(36);
UPDATE service SET tenant_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE service ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE service ADD CONSTRAINT fk_service_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_service_tenant ON service(tenant_id);

-- Repeat for: collection, role, policy, oidc_provider, ui_page, ui_menu, package, migration_run
-- Drop old unique constraints and recreate with tenant_id:
ALTER TABLE service DROP CONSTRAINT IF EXISTS service_name_key;
ALTER TABLE service ADD CONSTRAINT uq_service_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE role DROP CONSTRAINT IF EXISTS role_name_key;
ALTER TABLE role ADD CONSTRAINT uq_role_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE policy DROP CONSTRAINT IF EXISTS policy_name_key;
ALTER TABLE policy ADD CONSTRAINT uq_policy_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE oidc_provider DROP CONSTRAINT IF EXISTS oidc_provider_name_key;
ALTER TABLE oidc_provider ADD CONSTRAINT uq_oidc_tenant_name UNIQUE (tenant_id, name);

ALTER TABLE ui_page DROP CONSTRAINT IF EXISTS ui_page_path_key;
ALTER TABLE ui_page ADD CONSTRAINT uq_page_tenant_path UNIQUE (tenant_id, path);

ALTER TABLE ui_menu DROP CONSTRAINT IF EXISTS ui_menu_name_key;
ALTER TABLE ui_menu ADD CONSTRAINT uq_menu_tenant_name UNIQUE (tenant_id, name);
```

**Acceptance criteria:**
- Existing data preserved with default tenant assignment
- All unique constraints now include tenant_id
- FK relationships valid

**Integration points:**
- **A7 (Entity updates):** Entities must add `tenantId` field
- **A9 (Repository updates):** Queries must filter by tenantId

---

### A7: Update Existing Entities with tenantId

**Purpose:** Add `tenantId` field to all tenant-scoped JPA entities.

**Files to modify:**
- `Service.java` -- add `@Column(name = "tenant_id") private String tenantId;`
- `Collection.java` -- add `tenantId` (also inherited transitively via Service, but direct FK aids queries)
- `Role.java` -- add `tenantId`
- `Policy.java` -- add `tenantId`
- `OidcProvider.java` -- add `tenantId`
- `UiPage.java` -- add `tenantId`
- `UiMenu.java` -- add `tenantId`
- `ConfigPackage.java` -- add `tenantId`
- `MigrationRun.java` -- add `tenantId`

**Pattern for each:**
```java
@Column(name = "tenant_id", nullable = false, length = 36)
private String tenantId;

// Add to constructor(s)
// Add getter/setter
```

**Integration points:**
- **A9:** Repositories add tenant filtering
- **A10:** Services set tenantId from TenantContextHolder before save

---

### A8: TenantSchemaManager

**Purpose:** Provisions and manages per-tenant database schemas. Called during tenant creation.

**File:** `com.emf.controlplane.tenant.TenantSchemaManager`

```java
@Component
public class TenantSchemaManager {
    // Dependencies: DataSource, Flyway (for tenant-scoped migrations)

    public void provisionSchema(String tenantSlug)
    // 1. CREATE SCHEMA IF NOT EXISTS tenant_{slug}
    // 2. Run tenant-scoped Flyway migrations against that schema
    // 3. Seed default data (menus, default OIDC config)

    public void dropSchema(String tenantSlug)
    // DROP SCHEMA tenant_{slug} CASCADE (decommissioning only)

    public boolean schemaExists(String tenantSlug)

    public void runMigrations(String tenantSlug)
    // For applying new migrations to existing tenant schemas
}
```

For Phase 1, the simpler approach is to keep all data in the `public` schema and rely on `tenant_id` FK filtering (task A6/A9). `TenantSchemaManager` provisions the tenant record and seeds defaults. Schema-per-tenant can be introduced later as an optimization if needed.

**Integration points:**
- **A3 (TenantService):** Calls `provisionSchema()` during `createTenant()`
- **C12 (Default profile seeding):** Seeding runs as part of provisioning

---

### A9: Update All Repositories with Tenant Filtering

**Purpose:** Every query must be scoped to the current tenant.

**Files to modify:** All repositories in `com.emf.controlplane.repository.*`

**Example (CollectionRepository):**

```java
// BEFORE
Page<Collection> findByActiveTrue(Pageable pageable);
Optional<Collection> findByIdAndActiveTrue(String id);
boolean existsByNameAndActiveTrue(String name);

// AFTER
Page<Collection> findByTenantIdAndActiveTrue(String tenantId, Pageable pageable);
Optional<Collection> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);
boolean existsByTenantIdAndNameAndActiveTrue(String tenantId, String name);
```

**Apply same pattern to:**
- `ServiceRepository` -- add tenantId to all finders
- `RoleRepository` -- `findByTenantIdOrderByNameAsc(String tenantId)`
- `PolicyRepository` -- `findByTenantIdOrderByNameAsc(String tenantId)`
- `OidcProviderRepository` -- `findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId)`
- `UiPageRepository` -- `findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId)`
- `UiMenuRepository` -- add tenantId variants
- `FieldRepository` -- fields are scoped via collection.tenantId, may need join query
- `RoutePolicyRepository` -- scoped via collection
- `FieldPolicyRepository` -- scoped via field -> collection

**Integration points:**
- **A10:** Services pass `TenantContextHolder.getTenantId()` into these methods

---

### A10: Update All Services with Tenant Context

**Purpose:** Every service method must scope its operations to the current tenant.

**Files to modify:** `CollectionService`, `FieldService`, `ServiceService`, `AuthorizationService`, `OidcProviderService`, `UiConfigService`, `PackageService`, `MigrationService`, `DiscoveryService`, `GatewayBootstrapService`

**Pattern for each service:**

```java
// BEFORE (CollectionService.createCollection)
public Collection createCollection(CreateCollectionRequest request) {
    if (collectionRepository.existsByNameAndActiveTrue(request.name())) {
        throw new DuplicateResourceException(...);
    }
    ...
}

// AFTER
public Collection createCollection(CreateCollectionRequest request) {
    String tenantId = TenantContextHolder.getTenantId();
    if (collectionRepository.existsByTenantIdAndNameAndActiveTrue(tenantId, request.name())) {
        throw new DuplicateResourceException(...);
    }
    Collection collection = new Collection(...);
    collection.setTenantId(tenantId);
    ...
}
```

Every query method passes tenantId. Every create method sets tenantId on the new entity.

**Integration points:**
- **A5 (TenantContextHolder):** Source of tenant ID
- **A9 (Repositories):** Tenant-filtered queries

---

### A11: Tenant Resolution Filter

**Purpose:** Resolve the tenant for every incoming request before any business logic runs.

**Files:**
- `com.emf.controlplane.tenant.TenantResolutionFilter` (control plane, `OncePerRequestFilter`)
- `com.emf.gateway.tenant.TenantResolutionFilter` (gateway, `GlobalFilter`)

**Control plane filter:**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantResolutionFilter extends OncePerRequestFilter {
    // Dependencies: TenantRepository (or Redis cache)

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) {
        try {
            String tenantId = resolve(request);
            if (tenantId == null && !isExemptPath(request)) {
                response.sendError(400, "Tenant could not be resolved");
                return;
            }
            if (tenantId != null) {
                TenantContextHolder.set(tenantId, tenantSlug);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String resolve(HttpServletRequest request) {
        // 1. Header: X-Tenant-ID (from gateway or service-to-service)
        // 2. JWT claim: org_id (from OIDC token)
        // 3. Subdomain: acme.emf.app -> lookup by slug "acme"
        // 4. Return null if none found
    }

    private boolean isExemptPath(HttpServletRequest request) {
        // /actuator/**, /platform/tenants (platform admin), /ui/config/bootstrap
    }
}
```

**Gateway filter (reactive):**

```java
@Component
public class TenantResolutionFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Same resolution logic but reactive
        // Sets tenant in exchange attributes and X-Tenant-ID header for downstream
    }

    @Override
    public int getOrder() { return -200; } // Before JwtAuthenticationFilter (-100)
}
```

**Tenant cache:** Cache tenant lookups in Redis (key: `tenant:slug:{slug}`, TTL: 5 min) to avoid database hit on every request.

**Integration points:**
- **A5 (TenantContextHolder):** Sets the context
- **A12 (Header propagation):** Gateway adds `X-Tenant-ID` header
- **E2 (Multi-provider JWT):** Tenant can also be resolved from OIDC provider's tenant_id

**Exempt paths** (no tenant required):
- `/actuator/**` -- health checks
- `/platform/**` -- platform admin endpoints
- `/ui/config/bootstrap` -- pre-auth UI bootstrap

---

### A12: Update Gateway Header Propagation

**Purpose:** Ensure `X-Tenant-ID` is propagated from gateway to all downstream services.

**File to modify:** `com.emf.gateway.filter.HeaderTransformationFilter`

**Current implementation** adds `X-Forwarded-User` and `X-Forwarded-Roles`. Add:

```java
// In filter() method, after extracting principal:
String tenantId = exchange.getAttribute("tenantId"); // set by TenantResolutionFilter
if (tenantId != null) {
    mutatedRequest.header("X-Tenant-ID", tenantId);
    mutatedRequest.header("X-Tenant-Slug", exchange.getAttribute("tenantSlug"));
}
```

**Integration points:**
- **A11 (Tenant resolution):** Reads tenant from exchange attributes
- **A10 (Control plane):** Control plane's TenantResolutionFilter reads `X-Tenant-ID` header

---

### A13: Tenant Administration UI

**Purpose:** Frontend page for platform admins to manage tenants.

**Location:** New page in `emf-ui/app/src/pages/TenantManagement.tsx`

**SDK additions** (`emf-web/packages/sdk/src/admin/AdminClient.ts`):

```typescript
readonly tenants = {
    list: async (page?: number, size?: number): Promise<Page<Tenant>> => { ... },
    get: async (id: string): Promise<Tenant> => { ... },
    create: async (request: CreateTenantRequest): Promise<Tenant> => { ... },
    update: async (id: string, request: UpdateTenantRequest): Promise<Tenant> => { ... },
    suspend: async (id: string): Promise<void> => { ... },
    activate: async (id: string): Promise<void> => { ... },
    getLimits: async (id: string): Promise<GovernorLimits> => { ... },
};
```

**SDK types** (`emf-web/packages/sdk/src/admin/types.ts`):

```typescript
export interface Tenant {
    id: string;
    slug: string;
    name: string;
    edition: 'FREE' | 'PROFESSIONAL' | 'ENTERPRISE' | 'UNLIMITED';
    status: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'DECOMMISSIONED';
    settings: Record<string, unknown>;
    limits: GovernorLimits;
    createdAt: string;
    updatedAt: string;
}

export interface CreateTenantRequest {
    slug: string;
    name: string;
    edition?: string;
    settings?: Record<string, unknown>;
    limits?: Partial<GovernorLimits>;
}

export interface GovernorLimits {
    apiCallsPerDay: number;
    storageGb: number;
    maxUsers: number;
    maxCollections: number;
    maxFieldsPerCollection: number;
}
```

**UI features:**
- Table listing all tenants with status badge, edition, created date
- Create tenant dialog with slug validation (live uniqueness check)
- Edit tenant dialog (name, edition, settings, limits)
- Suspend/activate actions with confirmation
- Status filter (Active, Suspended, etc.)
- Search by name or slug

**Navigation:** Add "Platform Admin" section to `UiMenu` with items: Tenants, Platform Health.

---

### A14: Tenant Dashboard UI

**Purpose:** Per-tenant usage and health metrics visible to tenant admins.

**Location:** `emf-ui/app/src/pages/TenantDashboard.tsx`

**Displays:**
- API calls today / limit
- Storage used / limit
- Active users / limit
- Collections count / limit
- Recent login activity (from B6)
- System health indicators

**Backend endpoint:** `GET /control/dashboard` (tenant-scoped, uses `TenantContextHolder`)

---

## Stream B: User Management

### B1: User Database Migration

**Blocked by:** A6 (tenant_id must exist on tenant table)

**Flyway file:** `V10__add_user_tables.sql`

```sql
CREATE TABLE platform_user (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    email           VARCHAR(320) NOT NULL,
    username        VARCHAR(100),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    locale          VARCHAR(10)  DEFAULT 'en_US',
    timezone        VARCHAR(50)  DEFAULT 'UTC',
    profile_id      VARCHAR(36),
    manager_id      VARCHAR(36)  REFERENCES platform_user(id),
    last_login_at   TIMESTAMP WITH TIME ZONE,
    login_count     INTEGER      DEFAULT 0,
    mfa_enabled     BOOLEAN      DEFAULT false,
    settings        JSONB        DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED','PENDING_ACTIVATION'))
);
CREATE INDEX idx_user_tenant     ON platform_user(tenant_id);
CREATE INDEX idx_user_email      ON platform_user(tenant_id, email);
CREATE INDEX idx_user_manager    ON platform_user(manager_id);
CREATE INDEX idx_user_status     ON platform_user(tenant_id, status);

CREATE TABLE login_history (
    id              VARCHAR(36)  PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    login_time      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    source_ip       VARCHAR(45),
    login_type      VARCHAR(20)  NOT NULL DEFAULT 'UI',
    status          VARCHAR(20)  NOT NULL,
    user_agent      VARCHAR(500),
    CONSTRAINT chk_login_type   CHECK (login_type IN ('UI','API','OAUTH','SERVICE_ACCOUNT')),
    CONSTRAINT chk_login_status CHECK (status IN ('SUCCESS','FAILED','LOCKED_OUT'))
);
CREATE INDEX idx_login_user ON login_history(user_id, login_time DESC);
CREATE INDEX idx_login_tenant ON login_history(tenant_id, login_time DESC);
```

**Note:** `profile_id` FK is added in C1 after the profile table exists.

---

### B2: User Entity Class

**File:** `com.emf.controlplane.entity.User`

```java
@Entity
@Table(name = "platform_user")
public class User extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "locale", length = 10)
    private String locale = "en_US";

    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Column(name = "profile_id", length = 36)
    private String profileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "login_count")
    private Integer loginCount = 0;

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings = "{}";

    // Constructors, getters, setters
    public String getFullName() { return firstName + " " + lastName; }
}
```

**Integration points:**
- **C7 (PermissionResolver):** Reads `profileId` and permission set assignments
- **D7 (RecordAccessService):** Uses `manager` chain for role hierarchy
- **B5 (JIT provisioning):** Creates User records from JWT claims

---

### B3: User Service and Repository

**File:** `com.emf.controlplane.service.UserService`

**Repository:**

```java
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByTenantIdAndEmail(String tenantId, String email);
    Optional<User> findByIdAndTenantId(String id, String tenantId);
    Page<User> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);
    Page<User> findByTenantId(String tenantId, Pageable pageable);
    boolean existsByTenantIdAndEmail(String tenantId, String email);
    long countByTenantIdAndStatus(String tenantId, String status);
    List<User> findByManagerId(String managerId);
}
```

**Service methods:**

```java
@Service
public class UserService {
    @Transactional(readOnly = true)
    public Page<User> listUsers(String filter, Pageable pageable)

    @Transactional
    public User createUser(CreateUserRequest request)
    // Validates email uniqueness per tenant
    // Checks maxUsers governor limit
    // Sets default profile if not specified

    @Transactional(readOnly = true)
    public User getUser(String id)

    @Transactional
    public User updateUser(String id, UpdateUserRequest request)

    @Transactional
    public void deactivateUser(String id)
    // Sets status=INACTIVE, revokes active sessions

    @Transactional
    public void recordLogin(String userId, String sourceIp, String loginType, String status, String userAgent)
    // Creates login_history record, updates lastLoginAt and loginCount

    @Transactional(readOnly = true)
    public List<User> getManagerChain(String userId)
    // Walks manager_id chain upward, returns list [user, manager, manager's manager, ...]
    // Used by approval routing (Phase 4) and role hierarchy (D3)
}
```

**Integration points:**
- **B5 (JIT provisioning):** Calls `createUser()` when a new user logs in via OIDC
- **C7 (PermissionResolver):** Calls `getUser()` to load profile and permission sets
- **D7 (RecordAccessService):** Calls `getManagerChain()` for role hierarchy access
- **F1 (Setup audit):** User ID included in all audit records

---

### B4: User Controller

**File:** `com.emf.controlplane.controller.UserController`

**Base path:** `/control/users` (tenant-scoped)

```java
@RestController
@RequestMapping("/control/users")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {
    @GetMapping
    public Page<UserDto> listUsers(@RequestParam(required = false) String filter,
                                    @PageableDefault(size = 20) Pageable pageable)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_USERS')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request)

    @GetMapping("/{id}")
    public UserDto getUser(@PathVariable String id)

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_USERS')")
    public UserDto updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request)

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_USERS')")
    public ResponseEntity<Void> deactivateUser(@PathVariable String id)

    @GetMapping("/{id}/login-history")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_USERS')")
    public Page<LoginHistoryDto> getLoginHistory(@PathVariable String id, @PageableDefault Pageable pageable)

    @GetMapping("/me")   // Current user's own profile
    public UserDto getCurrentUser()
}
```

---

### B5: JIT User Provisioning

**Purpose:** When a user authenticates via OIDC for the first time, automatically create a local user record.

**File:** `com.emf.controlplane.service.JitUserProvisioningService`

```java
@Service
public class JitUserProvisioningService {
    // Dependencies: UserService, TenantService, ProfileService (C8)

    @Transactional
    public User provisionOrUpdate(String tenantId, Map<String, Object> jwtClaims) {
        String email = extractClaim(claims, "email");
        Optional<User> existing = userRepository.findByTenantIdAndEmail(tenantId, email);
        if (existing.isPresent()) {
            return updateLastLogin(existing.get());
        }
        // Create new user with claims
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setFirstName(extractClaim(claims, "given_name"));
        user.setLastName(extractClaim(claims, "family_name"));
        user.setUsername(extractClaim(claims, "preferred_username"));
        user.setProfileId(getDefaultProfileId(tenantId)); // "Standard User" profile
        return userService.createUser(user);
    }
}
```

**Called from:** The control plane's `SecurityConfig` JWT authentication converter -- after JWT validation, calls `provisionOrUpdate()`. Also callable from gateway via internal endpoint `POST /internal/users/provision`.

**Integration points:**
- **A11 (Tenant resolution):** Tenant must be resolved before provisioning
- **C8 (ProfileService):** Assigns default profile to new users
- **E2 (Multi-provider JWT):** OIDC claim names come from the tenant's `OidcProvider` config

---

### B6: Login History

**Purpose:** Track every authentication attempt for security audit.

Already included in B1 migration (`login_history` table).

**Entity:** `com.emf.controlplane.entity.LoginHistory`

**Service integration:** `UserService.recordLogin()` called from `JitUserProvisioningService` and from the gateway's `JwtAuthenticationFilter` via internal API.

---

### B7: User Management UI

**Location:** `emf-ui/app/src/pages/UserManagement.tsx`

**SDK additions:**

```typescript
readonly users = {
    list: async (filter?: string, page?: number): Promise<Page<User>> => { ... },
    get: async (id: string): Promise<User> => { ... },
    create: async (request: CreateUserRequest): Promise<User> => { ... },
    update: async (id: string, request: UpdateUserRequest): Promise<User> => { ... },
    deactivate: async (id: string): Promise<void> => { ... },
    getLoginHistory: async (id: string, page?: number): Promise<Page<LoginHistory>> => { ... },
    me: async (): Promise<User> => { ... },
};
```

**UI features:**
- User table with name, email, status, profile, last login
- Search by name or email
- Status filter (Active, Inactive, Locked)
- Create user form (email, name, profile assignment)
- Bulk status change (select multiple, deactivate)

---

### B8: User Detail UI

**Location:** `emf-ui/app/src/pages/UserDetail.tsx`

**Displays:**
- User info (name, email, status, locale, timezone)
- Profile assignment (dropdown)
- Permission set assignments (multi-select add/remove)
- Manager assignment (user lookup)
- Login history table
- Active sessions (future)
- Related records ownership summary (future)

---

## Stream C: Profile and Permission System

### C1: Permission Database Migration

**Blocked by:** B1 (user table must exist)

**Flyway file:** `V11__add_permission_tables.sql`

Creates tables: `profile`, `object_permission`, `field_permission`, `system_permission`, `permission_set`, `permset_object_permission`, `permset_field_permission`, `permset_system_permission`, `user_permission_set`

Also adds FK from `platform_user.profile_id` to `profile.id`.

(Full SQL as specified in TODO.md Phase 1.5)

---

### C2: Profile Entity

**File:** `com.emf.controlplane.entity.Profile`

```java
@Entity
@Table(name = "profile")
public class Profile extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system")
    private boolean system = false;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ObjectPermission> objectPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldPermission> fieldPermissions = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SystemPermission> systemPermissions = new ArrayList<>();
}
```

---

### C3: ObjectPermission Entity

```java
@Entity
@Table(name = "object_permission")
public class ObjectPermission extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "collection_id", nullable = false)
    private String collectionId;

    private boolean canCreate, canRead, canEdit, canDelete, canViewAll, canModifyAll;
}
```

Same pattern for `PermsetObjectPermission` with `permissionSetId` FK.

---

### C4: FieldPermission Entity

```java
@Entity
@Table(name = "field_permission")
public class FieldPermission extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "field_id", nullable = false)
    private String fieldId;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "VISIBLE"; // VISIBLE, READ_ONLY, HIDDEN
}
```

---

### C5: SystemPermission Entity

```java
@Entity
@Table(name = "system_permission")
public class SystemPermission extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(name = "granted")
    private boolean granted = false;
}
```

**System permission keys:**
`MANAGE_USERS`, `CUSTOMIZE_APPLICATION`, `MANAGE_SHARING`, `MANAGE_WORKFLOWS`, `MANAGE_REPORTS`, `API_ACCESS`, `MANAGE_INTEGRATIONS`, `MANAGE_DATA`, `VIEW_SETUP`, `MANAGE_SANDBOX`, `VIEW_ALL_DATA`, `MODIFY_ALL_DATA`

---

### C6: PermissionSet Entity

```java
@Entity
@Table(name = "permission_set")
public class PermissionSet extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system")
    private boolean system = false;

    @OneToMany(mappedBy = "permissionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermsetObjectPermission> objectPermissions = new ArrayList<>();
    // ... same for field and system permissions
}
```

Junction table entity for user <-> permission_set:

```java
@Entity
@Table(name = "user_permission_set")
@IdClass(UserPermissionSetId.class)
public class UserPermissionSet {
    @Id private String userId;
    @Id private String permissionSetId;
}
```

---

### C7: PermissionResolver Service

**Purpose:** The single source of truth for "what can this user do?" Merges profile + permission sets.

**File:** `com.emf.controlplane.service.PermissionResolver`

```java
@Service
public class PermissionResolver {
    // Dependencies: UserRepository, ProfileRepository, PermissionSetRepository,
    //               ObjectPermissionRepository, FieldPermissionRepository,
    //               SystemPermissionRepository

    public EffectiveObjectPermission resolveObjectPermission(String userId, String collectionId)
    // 1. Load user's profile -> get ObjectPermission for collectionId
    // 2. Load user's permission sets -> get PermsetObjectPermission for collectionId
    // 3. OR-merge: result.canCreate = profile.canCreate || any(permSets.canCreate)
    // 4. Return EffectiveObjectPermission record

    public EffectiveFieldPermission resolveFieldPermission(String userId, String fieldId)
    // Same OR logic: VISIBLE > READ_ONLY > HIDDEN (most permissive wins)

    public boolean hasSystemPermission(String userId, String permissionKey)
    // profile.granted || any(permSets.granted)

    public Set<String> getEffectiveSystemPermissions(String userId)
    // All granted system permissions from profile + permission sets

    public record EffectiveObjectPermission(
        boolean canCreate, boolean canRead, boolean canEdit, boolean canDelete,
        boolean canViewAll, boolean canModifyAll
    ) {}

    public record EffectiveFieldPermission(
        String visibility // VISIBLE, READ_ONLY, or HIDDEN
    ) {}
}
```

**Caching:** Cache resolved permissions in Redis (key: `perm:{userId}:{collectionId}`, TTL: 5 min). Evict on profile/permission set changes via Kafka event.

**Integration points:**
- **C11 (Gateway authz refactor):** Gateway calls PermissionResolver via REST or reads cached permissions
- **D7 (RecordAccessService):** Uses `canViewAll` / `canModifyAll` to skip sharing checks
- **B3 (UserService):** Provides user data

---

### C8: Profile Service

**File:** `com.emf.controlplane.service.ProfileService`

```java
@Service
public class ProfileService {
    @Transactional(readOnly = true)
    public List<Profile> listProfiles()

    @Transactional
    public Profile createProfile(CreateProfileRequest request)
    // Validates name uniqueness per tenant

    @Transactional(readOnly = true)
    public Profile getProfile(String id)

    @Transactional
    public Profile updateProfile(String id, UpdateProfileRequest request)

    @Transactional
    public void setObjectPermissions(String profileId, String collectionId, ObjectPermissionRequest request)
    // Upserts ObjectPermission for this profile+collection

    @Transactional
    public void setFieldPermissions(String profileId, List<FieldPermissionRequest> requests)
    // Upserts FieldPermission entries

    @Transactional
    public void setSystemPermissions(String profileId, List<SystemPermissionRequest> requests)
    // Upserts SystemPermission entries

    @Transactional
    public void deleteProfile(String id)
    // Cannot delete system profiles. Validates no users assigned.
}
```

---

### C9: PermissionSet Service

Same pattern as ProfileService but for PermissionSet entities. Also manages user <-> permission_set assignments.

```java
@Service
public class PermissionSetService {
    // CRUD for permission sets
    // assignToUser(userId, permissionSetId)
    // removeFromUser(userId, permissionSetId)
    // getUserPermissionSets(userId) -> List<PermissionSet>
}
```

---

### C10: Profile and PermissionSet Controllers

**Files:**
- `com.emf.controlplane.controller.ProfileController` at `/control/profiles`
- `com.emf.controlplane.controller.PermissionSetController` at `/control/permission-sets`

```java
// ProfileController
GET    /control/profiles                    // List profiles
POST   /control/profiles                    // Create profile
GET    /control/profiles/{id}               // Get profile with all permissions
PUT    /control/profiles/{id}               // Update profile metadata
DELETE /control/profiles/{id}               // Delete profile (if not system, no users)
PUT    /control/profiles/{id}/object-permissions/{collectionId}  // Set object perms
PUT    /control/profiles/{id}/field-permissions                  // Set field perms
PUT    /control/profiles/{id}/system-permissions                 // Set system perms

// PermissionSetController
GET    /control/permission-sets             // List permission sets
POST   /control/permission-sets             // Create
GET    /control/permission-sets/{id}        // Get with all permissions
PUT    /control/permission-sets/{id}        // Update
DELETE /control/permission-sets/{id}        // Delete
POST   /control/permission-sets/{id}/assign/{userId}    // Assign to user
DELETE /control/permission-sets/{id}/assign/{userId}     // Remove from user
```

---

### C11: Refactor Gateway Authorization for Permissions

**Purpose:** Replace the current `PolicyEvaluator` (simple role-list OR check) with the profile-based permission system.

**Files to modify:**
- `RouteAuthorizationFilter` -- check `EffectiveObjectPermission` instead of `RoutePolicy`
- `FieldAuthorizationFilter` -- check `EffectiveFieldPermission` instead of `FieldPolicy`
- `PolicyEvaluator` -- rewrite to call `PermissionResolver` (via cached REST call or Redis)

**New flow for RouteAuthorizationFilter:**

```
1. Extract GatewayPrincipal (has userId from JWT 'sub' claim)
2. Look up EffectiveObjectPermission from cache (Redis key: perm:obj:{userId}:{collectionId})
3. Map HTTP method to permission:
     GET    -> canRead
     POST   -> canCreate
     PUT    -> canEdit
     PATCH  -> canEdit
     DELETE -> canDelete
4. If not permitted -> 403
```

**New flow for FieldAuthorizationFilter:**

```
1. For each field in response:
2. Look up EffectiveFieldPermission from cache
3. If HIDDEN -> remove field entirely
4. If READ_ONLY and this is a write request -> reject field modification
5. If VISIBLE -> pass through
```

**Cache population:** Control plane exposes `GET /internal/permissions/{userId}` returning all effective permissions. Gateway caches this in Redis on first access and invalidates via Kafka event `emf.config.permissions.changed`.

**Backward compatibility:** The existing `Role` and `Policy` entities remain in the database. The `RoutePolicy` and `FieldPolicy` tables become deprecated but are not dropped. The gateway ignores them once the permission system is active. A feature flag `emf.gateway.use-profiles=true` controls which path is used.

---

### C12: Default Profile Seeding

**Purpose:** When a tenant is provisioned, create four default profiles.

**Called from:** `TenantSchemaManager.provisionSchema()` (A8)

**Default profiles:**

| Profile Name | Object Perms | System Perms | is_system |
|-------------|-------------|-------------|-----------|
| System Administrator | All CRUD + ViewAll + ModifyAll on all collections | All granted | true |
| Standard User | Create + Read + Edit on all; no Delete, no ViewAll | API_ACCESS | true |
| Read Only | Read only on all | none | true |
| Minimum Access | No object permissions | none | true |

System profiles cannot be deleted but their permissions can be customized.

The first user created for a tenant (the tenant admin) is assigned the "System Administrator" profile.

---

### C13: Permission Administration UI

**Location:** `emf-ui/app/src/pages/ProfileManagement.tsx`

**Features:**
- Profile list with user count badge
- Create/edit profile dialog
- Profile detail page with three tabs:
  - Object Permissions: matrix grid (collections as rows, CRUD+ViewAll+ModifyAll as columns, checkboxes)
  - Field Permissions: grouped by collection, visibility dropdown per field
  - System Permissions: checklist of permission keys with descriptions

**SDK additions:**

```typescript
readonly profiles = {
    list: async (): Promise<Profile[]> => { ... },
    get: async (id: string): Promise<ProfileDetail> => { ... },
    create: async (request: CreateProfileRequest): Promise<Profile> => { ... },
    update: async (id: string, request: UpdateProfileRequest): Promise<Profile> => { ... },
    delete: async (id: string): Promise<void> => { ... },
    setObjectPermissions: async (id: string, collectionId: string, perms: ObjectPermissionRequest): Promise<void> => { ... },
    setFieldPermissions: async (id: string, perms: FieldPermissionRequest[]): Promise<void> => { ... },
    setSystemPermissions: async (id: string, perms: SystemPermissionRequest[]): Promise<void> => { ... },
};

readonly permissionSets = {
    list: async (): Promise<PermissionSet[]> => { ... },
    get: async (id: string): Promise<PermissionSetDetail> => { ... },
    create: async (request: CreatePermissionSetRequest): Promise<PermissionSet> => { ... },
    update: async (id: string, request: UpdatePermissionSetRequest): Promise<PermissionSet> => { ... },
    delete: async (id: string): Promise<void> => { ... },
    assign: async (id: string, userId: string): Promise<void> => { ... },
    unassign: async (id: string, userId: string): Promise<void> => { ... },
};
```

---

### C14: Field Permission Editor UI

**Location:** Component within `ProfileManagement.tsx`

**Features:**
- Collection selector dropdown
- For selected collection: table of fields with visibility toggle (Visible / Read Only / Hidden)
- Bulk actions: "Set all to Visible", "Set all to Hidden"
- Preview: "View as this profile" mode showing which fields would be visible

---

## Stream D: Record-Level Sharing

### D1: Sharing Database Migration

**Blocked by:** C7 (PermissionResolver must exist for canViewAll/canModifyAll checks)

**Flyway file:** `V12__add_sharing_tables.sql`

Creates: `org_wide_default`, `sharing_rule`, `record_share`, `user_group`, `user_group_member`

Adds to `role` table: `tenant_id` (already done in A6), `parent_role_id VARCHAR(36) REFERENCES role(id)`, `hierarchy_level INTEGER DEFAULT 0`

(Full SQL as specified in TODO.md Phase 1.6)

---

### D2: OrgWideDefault Entity

**Purpose:** Defines the baseline record access level per collection.

```java
@Entity
@Table(name = "org_wide_default")
public class OrgWideDefault extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "collection_id", nullable = false)
    private String collectionId;

    @Column(name = "internal_access", nullable = false, length = 20)
    private String internalAccess = "PUBLIC_READ_WRITE";  // PRIVATE, PUBLIC_READ, PUBLIC_READ_WRITE

    @Column(name = "external_access", length = 20)
    private String externalAccess = "PRIVATE";
}
```

**Default behavior:** If no OWD record exists for a collection, default is `PUBLIC_READ_WRITE` (matching current behavior where all authenticated users can access everything).

---

### D3: Role Hierarchy Enhancement

**Purpose:** Add parent-child relationships to roles so managers can see their subordinates' records.

**Modify:** `Role.java` entity to add:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_role_id")
private Role parentRole;

@Column(name = "hierarchy_level")
private Integer hierarchyLevel = 0;

@OneToMany(mappedBy = "parentRole")
private List<Role> childRoles = new ArrayList<>();
```

**Service addition to AuthorizationService:**

```java
public List<Role> getRoleHierarchy(String tenantId)
// Returns all roles in hierarchy order (tree structure)

public Set<String> getSubordinateRoleIds(String roleId)
// Returns all role IDs below this role in the hierarchy (recursive)
```

Users are assigned to roles. If User A has role "VP Sales" and User B has role "Sales Rep" (which is a child of "VP Sales"), then A can see B's records when OWD is PRIVATE.

---

### D4: Group Entity

```java
@Entity
@Table(name = "user_group")
public class UserGroup extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "group_type", length = 20)
    private String groupType = "PUBLIC"; // PUBLIC or QUEUE

    @ManyToMany
    @JoinTable(name = "user_group_member",
               joinColumns = @JoinColumn(name = "group_id"),
               inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new HashSet<>();
}
```

---

### D5-D6: SharingRule and RecordShare Entities

(As specified in TODO.md section 1.6, entities for `sharing_rule` and `record_share` tables)

---

### D7: RecordAccessService

**Purpose:** The core algorithm that determines whether a specific user can access a specific record.

**File:** `com.emf.controlplane.service.RecordAccessService`

```java
@Service
public class RecordAccessService {
    // Dependencies: PermissionResolver (C7), OrgWideDefaultRepository,
    //               SharingRuleRepository, RecordShareRepository,
    //               AuthorizationService (for role hierarchy), UserService

    /**
     * Determines if a user can access a record with the given access type.
     *
     * @return true if access is granted
     */
    public boolean canAccess(String userId, String collectionId, String recordId,
                              String recordOwnerId, AccessType accessType) {
        // 1. Object permission check (via PermissionResolver)
        EffectiveObjectPermission objPerm = permissionResolver.resolveObjectPermission(userId, collectionId);
        if (accessType == READ && !objPerm.canRead()) return false;
        if (accessType == EDIT && !objPerm.canEdit()) return false;
        if (accessType == DELETE && !objPerm.canDelete()) return false;

        // 2. canViewAll / canModifyAll bypasses sharing
        if (accessType == READ && objPerm.canViewAll()) return true;
        if ((accessType == EDIT || accessType == DELETE) && objPerm.canModifyAll()) return true;

        // 3. OWD check
        OrgWideDefault owd = owdRepository.findByTenantIdAndCollectionId(tenantId, collectionId)
            .orElse(DEFAULT_PUBLIC_READ_WRITE);
        if (owd.internalAccess == PUBLIC_READ_WRITE) return true;
        if (owd.internalAccess == PUBLIC_READ && accessType == READ) return true;

        // 4. Ownership check
        if (recordOwnerId.equals(userId)) return true;

        // 5. Role hierarchy check
        if (isInRoleHierarchyAbove(userId, recordOwnerId)) return true;

        // 6. Sharing rules check
        if (sharingRuleGrantsAccess(userId, collectionId, recordId, accessType)) return true;

        // 7. Manual share check
        if (recordShareExists(userId, collectionId, recordId, accessType)) return true;

        return false;
    }

    /**
     * Returns additional WHERE clauses for list queries to filter records
     * the user can see. Used by StorageAdapter.
     */
    public String buildSharingWhereClause(String userId, String collectionId) {
        // If canViewAll -> no additional clause
        // If OWD PUBLIC_READ_WRITE -> no additional clause
        // Otherwise: owner_id = ? OR id IN (sharing rule matches) OR id IN (record shares)
    }

    public enum AccessType { READ, EDIT, DELETE }
}
```

**Integration points:**
- **C7 (PermissionResolver):** Object-level permission check
- **D3 (Role hierarchy):** Role hierarchy traversal
- **D8 (StorageAdapter):** `buildSharingWhereClause()` used in queries

---

### D8: Update StorageAdapter for Sharing

**Purpose:** Inject sharing-model WHERE clauses into queries so users only see records they're allowed to see.

**Modification to `PhysicalTableStorageAdapter.query()`:**

Before executing the query, call `RecordAccessService.buildSharingWhereClause()` and append it to the WHERE clause. This is injected at the domain service level (sample-service) or via the gateway's data proxy.

**Also:** Add `owner_id VARCHAR(36)` as a system column to all tables created by `initializeCollection()`. Set automatically to the current user's ID on record creation.

---

### D9: Sharing Administration UI

**Location:** `emf-ui/app/src/pages/SharingSettings.tsx`

**Features:**
- Per-collection OWD settings (dropdown: Private, Public Read, Public Read/Write)
- Sharing rules list per collection (create, edit, delete)
- Sharing rule editor: rule type (owner-based / criteria-based), shared from, shared to, access level
- Manual share viewer: see all shares on a specific record

**SDK additions:**

```typescript
readonly sharing = {
    getOwd: async (collectionId: string): Promise<OrgWideDefault> => { ... },
    setOwd: async (collectionId: string, request: SetOwdRequest): Promise<OrgWideDefault> => { ... },
    listRules: async (collectionId: string): Promise<SharingRule[]> => { ... },
    createRule: async (collectionId: string, request: CreateSharingRuleRequest): Promise<SharingRule> => { ... },
    updateRule: async (ruleId: string, request: UpdateSharingRuleRequest): Promise<SharingRule> => { ... },
    deleteRule: async (ruleId: string): Promise<void> => { ... },
    listRecordShares: async (collectionId: string, recordId: string): Promise<RecordShare[]> => { ... },
};
```

---

### D10: Role Hierarchy Visualization UI

**Location:** `emf-ui/app/src/pages/RoleHierarchy.tsx`

**Features:**
- Tree visualization of role hierarchy (parent-child relationships)
- Drag-and-drop to rearrange hierarchy
- Click role to see assigned users
- Create/edit role dialog with parent selector

---

## Stream E: OIDC Enhancement

### E1: Tenant-Scoped OIDC Configuration

**Blocked by:** A10 (tenant-aware services)

**Modification:** `OidcProviderService` already scoped to tenant (via A10). The main change is ensuring the gateway can look up the right OIDC provider based on the JWT's issuer, then derive the tenant from the provider's `tenant_id`.

**New endpoint:** `GET /internal/oidc/by-issuer?issuer={issuer}` (internal API, no auth) returns the OidcProvider including its `tenant_id`. Cached in Redis by the gateway.

---

### E2: Multi-Provider JWT Validation in Gateway

**Blocked by:** E1

**Modify:** Gateway `SecurityConfig.java` to replace the single `issuerUri` configuration with dynamic multi-provider resolution.

**New `DynamicReactiveJwtDecoder`:**

```java
public class DynamicReactiveJwtDecoder implements ReactiveJwtDecoder {
    // Dependencies: WebClient (for control plane), RedisReactiveCommands (for cache)

    @Override
    public Mono<Jwt> decode(String token) {
        // 1. Parse JWT header to get kid (key ID) - without validation
        // 2. Parse JWT body to get iss (issuer) - without validation
        // 3. Look up OidcProvider by issuer (cached in Redis)
        // 4. Fetch JWKS from provider's jwksUri (cached)
        // 5. Validate JWT signature against JWKS
        // 6. Validate claims (exp, iss, aud)
        // 7. Set tenantId in exchange attributes (from provider's tenant_id)
        // 8. Return decoded Jwt
    }
}
```

This replaces the current static `ReactiveJwtDecoder` bean.

**Integration points:**
- **A11 (Tenant resolution):** Tenant can be resolved from the OIDC provider's `tenant_id` as a fallback
- **B5 (JIT provisioning):** Claim names come from the provider's configured claim mappings

---

### E3: OIDC Admin UI Updates

**Modify:** Existing OIDC provider management UI to scope to current tenant. No major changes needed since the backend is already scoped via A10.

Add: "Test Connection" button that validates the issuer URL and JWKS endpoint are reachable.

---

## Stream F: Cross-Cutting Concerns

### F1: Setup Audit Trail Migration and Entity

**Blocked by:** B3 (user table must exist for FK)

**Flyway file:** `V13__add_setup_audit_trail.sql`

```sql
CREATE TABLE setup_audit_trail (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    user_id         VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    action          VARCHAR(50)  NOT NULL,
    section         VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       VARCHAR(36),
    entity_name     VARCHAR(200),
    old_value       JSONB,
    new_value       JSONB,
    timestamp       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant_time ON setup_audit_trail(tenant_id, timestamp DESC);
CREATE INDEX idx_audit_entity      ON setup_audit_trail(entity_type, entity_id);
```

---

### F2: SetupAuditService

**File:** `com.emf.controlplane.service.SetupAuditService`

```java
@Service
public class SetupAuditService {
    public void log(String action, String section, String entityType,
                    String entityId, String entityName,
                    Object oldValue, Object newValue) {
        // Gets tenantId and userId from TenantContextHolder and SecurityContext
        // Serializes old/new values to JSON
        // Persists SetupAuditTrail record
    }

    public Page<SetupAuditTrailDto> getAuditTrail(String filter, Pageable pageable)
    // Filter by section, entity_type, user, date range
}
```

**AOP integration:** Create `@SetupAudited` annotation and aspect that automatically captures before/after state for annotated service methods. Applied to: `CollectionService`, `FieldService`, `ProfileService`, `PermissionSetService`, `OidcProviderService`, `TenantService`.

---

### F3: Governor Limits Enforcement

**File:** `com.emf.controlplane.service.GovernorLimitsService`

```java
@Service
public class GovernorLimitsService {
    // Dependencies: TenantService, RedisTemplate

    public void checkApiCallLimit(String tenantId)
    // Increment Redis counter (key: limits:api:{tenantId}:{date})
    // If exceeds tenant.limits.apiCallsPerDay -> throw GovernorLimitExceededException

    public void checkUserLimit(String tenantId)
    // Count active users for tenant
    // If >= tenant.limits.maxUsers -> throw GovernorLimitExceededException

    public void checkCollectionLimit(String tenantId)
    // Count active collections for tenant

    public void checkFieldLimit(String tenantId, String collectionId)
    // Count active fields for collection

    public GovernorLimitsStatus getStatus(String tenantId)
    // Returns current usage vs limits for all categories
}
```

**Integration:** Called from `TenantResolutionFilter` (A11) for API call counting. Called from `CollectionService.createCollection()` for collection limit. Called from `UserService.createUser()` for user limit.

---

### F4: Test Suite Updates

**Purpose:** Update all existing tests to work with tenant context.

**Changes:**
- Add `TestTenantContextHelper` that sets up a default tenant context for all tests
- Update `@BeforeEach` in integration tests to create a test tenant and set context
- Update all service tests to verify tenant isolation (create data in tenant A, verify invisible from tenant B)
- Add cross-tenant isolation tests

**New test classes:**
- `TenantIsolationIntegrationTest` -- verifies data isolation across tenants
- `PermissionResolverTest` -- unit tests for permission merging logic
- `RecordAccessServiceTest` -- tests sharing model algorithm
- `TenantResolutionFilterTest` -- tests all resolution paths

---

### F5: Setup Audit Trail UI

**Location:** `emf-ui/app/src/pages/SetupAuditTrail.tsx`

**Features:**
- Chronological log of all configuration changes
- Filters: date range, section, entity type, user
- Expandable rows showing old/new values side-by-side (JSON diff view)
- Export to CSV

---

### F6: Governor Limits Dashboard UI

**Location:** Component within `TenantDashboard.tsx` (A14)

**Features:**
- Progress bars for each limit category (API calls, storage, users, collections)
- Usage trends over time (line chart)
- Alert indicators when approaching limits (>80%)

---

## Completeness Review

### Verified Coverage

| Requirement | Tasks |
|-------------|-------|
| Tenant CRUD | A1-A4, A13 |
| Tenant data isolation | A5-A10 |
| Tenant resolution (JWT, header, subdomain) | A11-A12 |
| User CRUD + JIT provisioning | B1-B5, B7-B8 |
| Login history | B1, B6, B7 |
| Profiles with object/field/system perms | C1-C5, C8, C10, C13 |
| Permission sets (additive) | C6, C9, C10, C13 |
| Permission resolution (OR merge) | C7 |
| Gateway authorization refactor | C11 |
| Default profiles per tenant | C12 |
| OWD settings | D1-D2, D9 |
| Role hierarchy | D3, D10 |
| Sharing rules (owner + criteria based) | D5, D7, D9 |
| Manual record sharing | D6, D7, D9 |
| Groups/queues | D4 |
| Record access algorithm | D7, D8 |
| Storage adapter sharing integration | D8 |
| Tenant-scoped OIDC | E1-E3 |
| Multi-provider JWT validation | E2 |
| Setup audit trail | F1-F2, F5 |
| Governor limits | F3, F6 |
| All existing tests updated | F4 |
| Frontend for every backend feature | A13-A14, B7-B8, C13-C14, D9-D10, E3, F5-F6 |

### Potential Gaps Identified and Addressed

1. **Session management** -- Not explicitly called out. Add to B5: after JIT provisioning, create a session record. The existing JWT-based auth is stateless, which is sufficient for Phase 1. Session tracking can be derived from login_history.

2. **Password policies** -- Phase 1 relies on OIDC for authentication (no local passwords). Local auth with password policies is a Phase 5 item (connected apps). No gap for Phase 1.

3. **Kafka event for permission changes** -- C11 references cache invalidation. Add explicit event: `emf.config.permissions.changed` published by `ProfileService` and `PermissionSetService` when permissions are modified. Gateway's `ConfigEventListener` must handle this event to invalidate permission cache.

4. **owner_id on existing records** -- D8 adds `owner_id` to new tables. Need a migration to add `owner_id` to existing collection data tables. This is handled by `PhysicalTableStorageAdapter` when it initializes tables.

5. **Bootstrap config update** -- The gateway's `GatewayBootstrapService.getBootstrapConfig()` currently returns empty authorization config. It must be updated to return permission-based config (effective permissions per collection).

6. **UI navigation for new sections** -- Add menu items to the existing `UiMenu` seed data (V3 migration update): "Users", "Profiles", "Permission Sets", "Sharing Settings", "Role Hierarchy", "Audit Trail", "Governor Limits" under a "Security" section.

7. **Tenant switching for platform admins** -- Platform admins need to "act as" a specific tenant. Add `X-Act-As-Tenant` header support in A11 filter, only honored for PLATFORM_ADMIN role.

8. **Concurrent request safety** -- `TenantContextHolder` uses `ThreadLocal` which is safe for traditional servlet threads. For the reactive gateway, use `Mono.deferContextual()` with Reactor Context instead of ThreadLocal.

9. **API versioning** -- New endpoints (`/platform/tenants`, `/control/users`, `/control/profiles`, etc.) should be versioned. Use `/api/v1/` prefix for all new endpoints, keeping existing `/control/` paths for backward compatibility during migration.

10. **Data migration for existing deployments** -- V9 migration handles adding `tenant_id` to existing data with a default tenant. Existing API consumers continue to work against the default tenant until explicitly migrated. Document this in a MIGRATION-GUIDE.md.
