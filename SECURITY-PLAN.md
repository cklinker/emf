# EMF Security Unification Plan

## Executive Summary

Security in EMF is fragmented across three layers (gateway, control-plane, UI) with no coherent strategy connecting them. The legacy permission tables were dropped (V47) but nothing replaced them. Of ~203 API endpoints, ~130 have **zero authorization checks** — any authenticated user can access them. The UI enforces roles on exactly 1 of ~30 admin pages. The permission system (`/my-permissions`) returns hardcoded permissive stubs. There is no object-level, field-level, or record-level security enforcement anywhere in the running application.

This plan establishes a unified, enterprise-grade security model from the data layer through the API to the UI.

---

## Current State: What's Broken

### 1. No Permission Model (Legacy Dropped, Nothing Replaced)

V47 dropped all permission tables (profile, permission_set, object_permission, field_permission, system_permission, sharing rules). The `MyPermissionsController` returns hardcoded `true` for all permissions. There is no runtime permission evaluation anywhere.

### 2. Inconsistent API Authorization

| Pattern | Endpoint Count | Example |
|---------|---------------|---------|
| No @PreAuthorize at all | ~130 | users, layouts, reports, webhooks, scripts, flows, dashboards, approvals, connected-apps, bulk-jobs, email-templates, scheduled-jobs, workflow-rules, notes, attachments, export, composite, listviews |
| hasRole('ADMIN') | ~50 | collections (write), OIDC providers, packages, migrations, workers, UI config, audit |
| hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION') | ~20 | picklists (write), validation rules (write), record types (write) |
| hasRole('PLATFORM_ADMIN') | ~10 | tenants, governor-limits (write) |
| Intentionally public | ~15 | bootstrap, metrics, slug-map, internal |

Controllers that should require admin access but don't: UserController, PageLayoutController, ReportController, DashboardController, WorkflowRuleController, ScriptController, WebhookController, ConnectedAppController, FlowController, ScheduledJobController, EmailTemplateController, BulkJobController, ListViewController.

### 3. UI Doesn't Enforce Security

- Only `/setup/tenants` has `requiredRoles={['PLATFORM_ADMIN']}` — every other admin page is accessible to any authenticated user by URL
- No per-page permission checks in components
- No field-level visibility enforcement
- No record-level access checks
- Admin mode toggle is cosmetic (checks `ADMIN || PLATFORM_ADMIN` role, but doesn't gate anything)

### 4. Tenant Isolation is Service-Layer Only

- `TenantContextHolder` sets ThreadLocal but nothing enforces it at the JPA/repository level
- A service method bug could expose cross-tenant data
- No Hibernate filter or @TenantId annotation to guarantee isolation

### 5. Permission Stubs Return Permissive Defaults

- `GET /my-permissions/objects/{collection}` → always `{canCreate: true, canRead: true, canEdit: true, canDelete: true, canViewAll: true, canModifyAll: true}`
- `GET /my-permissions/fields/{collection}` → always empty (all fields visible)
- UI relies on these endpoints to decide what to show — so it shows everything to everyone

### 6. Gateway Delegates All Authorization to Backend

The gateway validates JWT tokens and propagates tenant context, but performs no fine-grained authorization. This is by design — but it means the backend MUST enforce authorization, and right now it largely doesn't.

---

## Target Architecture

### Core Principles

1. **Security is data-driven, not annotation-driven.** Permissions are defined in the database and evaluated at runtime, not hardcoded in annotations scattered across controllers.

2. **Defense in depth.** Gateway enforces authentication + tenant isolation. Control-plane enforces authorization via a centralized permission service. UI enforces visibility based on resolved permissions from the API.

3. **Least privilege by default.** New users have no permissions until explicitly granted via a Profile or Permission Set. The system ships with a "System Administrator" profile that has full access.

4. **Three permission layers:**
   - **System Permissions** — Feature-level access (MANAGE_USERS, CUSTOMIZE_APPLICATION, VIEW_SETUP, etc.)
   - **Object Permissions** — Per-collection CRUD (can_create, can_read, can_edit, can_delete, can_view_all, can_modify_all)
   - **Field Permissions** — Per-field visibility (VISIBLE, READ_ONLY, HIDDEN)

5. **Permissions are additive.** A user's effective permissions = Profile permissions + all assigned Permission Set permissions. The most permissive grant wins.

6. **PLATFORM_ADMIN bypasses all checks.** Platform administrators operate outside the tenant permission model — they manage the platform itself.

7. **Every API endpoint has explicit authorization.** No endpoint exists without a documented security policy.

8. **UI reflects API permissions exactly.** If the API would reject an action, the UI doesn't show the button/field.

---

## Phase 1: Foundation — Permission Data Model & Resolution Service

### 1.1 Database Schema (New Migration V48)

Recreate the permission model with a clean, well-designed schema:

```sql
-- Profiles: Named permission bundles assigned to users (one per user)
CREATE TABLE profile (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,  -- System profiles can't be deleted
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

-- System permissions per profile
CREATE TABLE profile_system_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (profile_id, permission_name)
);

-- Object permissions per profile per collection
CREATE TABLE profile_object_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    can_create BOOLEAN NOT NULL DEFAULT FALSE,
    can_read BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_all BOOLEAN NOT NULL DEFAULT FALSE,
    can_modify_all BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (profile_id, collection_id)
);

-- Field permissions per profile per field
CREATE TABLE profile_field_permission (
    id VARCHAR(36) PRIMARY KEY,
    profile_id VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    field_id VARCHAR(36) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',  -- VISIBLE, READ_ONLY, HIDDEN
    UNIQUE (profile_id, field_id)
);

-- Permission Sets: Additional permission bundles (many per user, additive)
CREATE TABLE permission_set (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

-- Permission set system/object/field tables mirror profile tables
CREATE TABLE permset_system_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (permission_set_id, permission_name)
);

CREATE TABLE permset_object_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    can_create BOOLEAN NOT NULL DEFAULT FALSE,
    can_read BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_all BOOLEAN NOT NULL DEFAULT FALSE,
    can_modify_all BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (permission_set_id, collection_id)
);

CREATE TABLE permset_field_permission (
    id VARCHAR(36) PRIMARY KEY,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    collection_id VARCHAR(36) NOT NULL,
    field_id VARCHAR(36) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    UNIQUE (permission_set_id, field_id)
);

-- User ↔ Profile assignment (one profile per user per tenant)
ALTER TABLE platform_user ADD COLUMN profile_id VARCHAR(36) REFERENCES profile(id);

-- User ↔ Permission Set assignment (many-to-many)
CREATE TABLE user_permission_set (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, permission_set_id)
);

-- Group ↔ Permission Set assignment (groups can grant permission sets)
CREATE TABLE group_permission_set (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    permission_set_id VARCHAR(36) NOT NULL REFERENCES permission_set(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, permission_set_id)
);
```

### 1.2 Seed Data — Default Profiles (in same migration)

Every new tenant is provisioned with a set of default profiles that cover common organizational roles. These are marked `is_system = TRUE` so they cannot be deleted, but their permissions CAN be customized per tenant. The first user provisioned via OIDC is automatically assigned the System Administrator profile.

#### Default Profile Definitions

| Profile | Description | Target Users |
|---------|-------------|-------------|
| **System Administrator** | Full, unrestricted access to all features and data | IT admins, platform owners |
| **Standard User** | Read/create/edit records in all collections, create personal reports and list views | Day-to-day business users |
| **Read Only** | View all records and reports, no create/edit/delete capability | Executives, auditors, observers |
| **Marketing User** | Standard User + manage email templates | Marketing team members |
| **Contract Manager** | Standard User + manage approval processes | Legal, procurement |
| **Solution Manager** | Standard User + customize application (collections, fields, layouts, picklists) | Citizen developers, team leads |
| **Minimum Access** | Login only, no data access until object permissions are explicitly granted | Restricted/external users |

#### System Administrator Profile

Full access to everything. All system permissions granted, all object permissions default to full CRUD + view_all + modify_all for every collection.

```
System Permissions: ALL granted
Object Permissions: All collections → can_create, can_read, can_edit, can_delete, can_view_all, can_modify_all
Field Permissions:  All fields → VISIBLE
```

#### Standard User Profile

The workhorse profile for typical business users. Can work with records across all collections but cannot change the application's structure or manage other users.

```
System Permissions:
  ✓ API_ACCESS
  ✓ MANAGE_LISTVIEWS
  ✗ VIEW_SETUP, CUSTOMIZE_APPLICATION, MANAGE_USERS, MANAGE_GROUPS,
    MANAGE_SHARING, MANAGE_WORKFLOWS, MANAGE_REPORTS, MANAGE_EMAIL_TEMPLATES,
    MANAGE_CONNECTED_APPS, MANAGE_DATA, VIEW_ALL_DATA, MODIFY_ALL_DATA,
    MANAGE_APPROVALS

Object Permissions: All collections → can_create, can_read, can_edit, can_delete
                    (can_view_all = FALSE, can_modify_all = FALSE — user sees only own records + shared records)
Field Permissions:  All fields → VISIBLE
```

#### Read Only Profile

For users who need to see data but should never modify it. Useful for executives, external auditors, or compliance reviewers.

```
System Permissions:
  ✓ VIEW_ALL_DATA
  ✗ All others

Object Permissions: All collections → can_read, can_view_all
                    (can_create = FALSE, can_edit = FALSE, can_delete = FALSE, can_modify_all = FALSE)
Field Permissions:  All fields → READ_ONLY
```

#### Marketing User Profile

Extends Standard User with email template management for campaign workflows.

```
System Permissions:
  ✓ API_ACCESS
  ✓ MANAGE_LISTVIEWS
  ✓ MANAGE_EMAIL_TEMPLATES
  ✗ All others

Object Permissions: Same as Standard User
Field Permissions:  All fields → VISIBLE
```

#### Contract Manager Profile

Extends Standard User with the ability to configure and manage approval processes.

```
System Permissions:
  ✓ API_ACCESS
  ✓ MANAGE_LISTVIEWS
  ✓ MANAGE_APPROVALS
  ✗ All others

Object Permissions: Same as Standard User
Field Permissions:  All fields → VISIBLE
```

#### Solution Manager Profile

The "citizen developer" profile. Can customize the application structure — create collections, add fields, configure layouts, manage picklists, and build reports/dashboards — without having full system administrator access.

```
System Permissions:
  ✓ VIEW_SETUP
  ✓ CUSTOMIZE_APPLICATION
  ✓ MANAGE_REPORTS
  ✓ MANAGE_WORKFLOWS
  ✓ MANAGE_LISTVIEWS
  ✓ API_ACCESS
  ✗ MANAGE_USERS, MANAGE_GROUPS, MANAGE_SHARING, MANAGE_EMAIL_TEMPLATES,
    MANAGE_CONNECTED_APPS, MANAGE_DATA, VIEW_ALL_DATA, MODIFY_ALL_DATA,
    MANAGE_APPROVALS

Object Permissions: All collections → can_create, can_read, can_edit, can_delete, can_view_all
                    (can_modify_all = FALSE)
Field Permissions:  All fields → VISIBLE
```

#### Minimum Access Profile

Login-only profile with no data access. Use this as a starting point for users who need very specific, narrowly-scoped access granted via Permission Sets.

```
System Permissions:
  ✗ All denied

Object Permissions: No collections granted (empty — user cannot see any data)
Field Permissions:  N/A (no object access)
```

#### Profile Provisioning Logic

Profiles are seeded during `TenantSchemaManager.provisionTenant()`:

```java
// For each default profile:
// 1. Create profile record
// 2. Create system permission grants
// 3. Object permissions are created dynamically when collections exist:
//    - On tenant provisioning: grant permissions for all existing collections
//    - On new collection creation: auto-grant permissions per profile type
//       (System Admin gets full, Standard User gets CRUD, Read Only gets read, etc.)
```

When a new collection is created, `CollectionService.createCollection()` must also create default object permission grants for all profiles based on their type. This ensures new collections are immediately accessible according to each profile's intent without requiring manual permission configuration.

#### First User Assignment

The first user provisioned via OIDC JIT (`UserService.provisionOrUpdate()`) into a new tenant is automatically assigned the **System Administrator** profile. Subsequent users are assigned the **Standard User** profile by default. The profile assignment can be changed by any user with the MANAGE_USERS system permission.

### 1.3 Defined System Permissions

| Permission | Description | Who Needs It |
|-----------|-------------|--------------|
| VIEW_SETUP | Access setup/admin pages | Admins |
| CUSTOMIZE_APPLICATION | Manage collections, fields, picklists, validation rules, record types, layouts | Admins |
| MANAGE_USERS | Create/edit/deactivate users, assign profiles | Admins |
| MANAGE_GROUPS | Create/edit/delete groups, manage membership | Admins |
| MANAGE_SHARING | Configure sharing rules (future) | Admins |
| MANAGE_WORKFLOWS | Create/edit workflow rules, flows, scheduled jobs | Admins |
| MANAGE_REPORTS | Create/edit reports, dashboards | Report builders |
| MANAGE_EMAIL_TEMPLATES | Create/edit email templates | Admins |
| MANAGE_CONNECTED_APPS | Create/edit connected apps, webhooks, scripts | Admins |
| MANAGE_DATA | Export data, bulk operations | Data managers |
| API_ACCESS | Use API directly (vs UI-only) | Integrators |
| VIEW_ALL_DATA | Read all records regardless of sharing | Support/Compliance |
| MODIFY_ALL_DATA | Edit all records regardless of sharing | Support/Compliance |
| MANAGE_APPROVALS | Configure approval processes | Admins |
| MANAGE_LISTVIEWS | Create/edit list views | Power users |

### 1.4 Permission Resolution Service

New service: `PermissionResolutionService`

```java
@Service
public class PermissionResolutionService {

    /**
     * Resolves the effective permissions for a user by combining:
     * 1. Profile permissions (base)
     * 2. Direct permission set assignments (additive)
     * 3. Group-inherited permission set assignments (additive)
     *
     * Most permissive grant wins for each permission.
     * Results are cached per-user with invalidation on profile/permset changes.
     */
    public ResolvedPermissions resolveForUser(String tenantId, String userId);

    /** Check a single system permission */
    public boolean hasSystemPermission(String tenantId, String userId, String permissionName);

    /** Get object-level permissions for a collection */
    public ObjectPermissions getObjectPermissions(String tenantId, String userId, String collectionId);

    /** Get field visibility for all fields in a collection */
    public Map<String, FieldVisibility> getFieldPermissions(String tenantId, String userId, String collectionId);
}
```

**Caching strategy:** Redis cache keyed by `permissions:{tenantId}:{userId}` with 5-minute TTL. Invalidated on:
- Profile assignment change
- Permission set assignment change
- Profile/permission set definition change
- Group membership change

### 1.5 JPA Entities

- `Profile` extends `BaseEntity` — name, description, isSystem, tenantId
- `PermissionSet` extends `BaseEntity` — name, description, isSystem, tenantId
- `ProfileSystemPermission` — profileId, permissionName, granted
- `ProfileObjectPermission` — profileId, collectionId, CRUD flags
- `ProfileFieldPermission` — profileId, collectionId, fieldId, visibility
- (Mirror tables for permission sets)
- `UserPermissionSet` — userId, permissionSetId
- `GroupPermissionSet` — groupId, permissionSetId

---

## Phase 2: API Authorization Enforcement

### 2.1 Replace Scattered @PreAuthorize with Centralized Security

Create a custom Spring Security method interceptor that checks permissions from the database:

```java
@Component
public class PermissionEvaluator implements org.springframework.security.access.PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        // Resolve user from JWT
        // Check PLATFORM_ADMIN bypass
        // Delegate to PermissionResolutionService
    }
}
```

Define custom annotations that map to permission checks:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@securityService.hasSystemPermission(#root, 'MANAGE_USERS')")
public @interface RequiresPermission {
    String value();  // System permission name
}

// For object-level checks:
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresObjectPermission {
    String action();  // CREATE, READ, EDIT, DELETE
}
```

### 2.2 Endpoint Authorization Matrix

Every endpoint must map to a permission. Here is the complete matrix:

#### Setup/Admin Endpoints — Require System Permissions

| Controller | Endpoints | Required Permission | Current State |
|-----------|-----------|-------------------|---------------|
| **UserController** | GET /users, GET /users/{id} | MANAGE_USERS (or own user) | **NONE** |
| | POST /users | MANAGE_USERS | **NONE** |
| | PUT /users/{id} | MANAGE_USERS (or own user, limited fields) | **NONE** |
| | POST /users/{id}/deactivate, activate | MANAGE_USERS | **NONE** |
| | GET /users/{id}/login-history | MANAGE_USERS | **NONE** |
| **PageLayoutController** | GET /layouts, GET /layouts/{id} | VIEW_SETUP | **NONE** |
| | POST, PUT, DELETE /layouts | CUSTOMIZE_APPLICATION | **NONE** |
| | GET/PUT /layouts/assignments | CUSTOMIZE_APPLICATION | **NONE** |
| **ListViewController** | GET /listviews | authenticated (own + shared) | **NONE** |
| | POST, PUT, DELETE /listviews | MANAGE_LISTVIEWS (or own) | **NONE** |
| **ReportController** | GET /reports | authenticated (own + shared) | **NONE** |
| | POST, PUT, DELETE /reports | MANAGE_REPORTS (or own) | **NONE** |
| | GET/POST/DELETE /reports/folders | MANAGE_REPORTS | **NONE** |
| **DashboardController** | GET /dashboards | authenticated (own + shared) | **NONE** |
| | POST, PUT, DELETE /dashboards | MANAGE_REPORTS (or own) | **NONE** |
| **WorkflowRuleController** | ALL | MANAGE_WORKFLOWS | **NONE** |
| **FlowController** | ALL | MANAGE_WORKFLOWS | **NONE** |
| **ScheduledJobController** | ALL | MANAGE_WORKFLOWS | **NONE** |
| **ApprovalController** | GET processes | MANAGE_APPROVALS or authenticated | **NONE** |
| | POST/PUT/DELETE processes | MANAGE_APPROVALS | **NONE** |
| | GET/POST instances | authenticated (own approvals) | **NONE** |
| **EmailTemplateController** | ALL | MANAGE_EMAIL_TEMPLATES | **NONE** |
| **ScriptController** | ALL | MANAGE_CONNECTED_APPS | **NONE** |
| **WebhookController** | ALL | MANAGE_CONNECTED_APPS | **NONE** |
| **ConnectedAppController** | ALL | MANAGE_CONNECTED_APPS | **NONE** |
| **BulkJobController** | ALL | MANAGE_DATA | **NONE** |
| **ExportController** | ALL | MANAGE_DATA | **NONE** |
| **CompositeApiController** | POST /composite | API_ACCESS | **NONE** |
| **NoteController** | ALL | authenticated + object READ perm | **NONE** |
| **AttachmentController** | ALL | authenticated + object READ/EDIT perm | **NONE** |
| **AdminController** | GET /dashboard | VIEW_SETUP | **NONE** |
| **DiscoveryController** | GET /resources | authenticated | **NONE** |

#### Already-Protected Endpoints — Migrate to Permission-Based

| Controller | Current Check | Target Permission |
|-----------|--------------|-------------------|
| CollectionController (write) | hasRole('ADMIN') | CUSTOMIZE_APPLICATION |
| CollectionController (read) | authenticated | authenticated + object READ |
| OidcProviderController | hasRole('ADMIN') | MANAGE_CONNECTED_APPS (or new MANAGE_SSO) |
| PackageController | hasRole('ADMIN') | CUSTOMIZE_APPLICATION |
| MigrationController | hasRole('ADMIN') | CUSTOMIZE_APPLICATION |
| UiConfigController | hasRole('ADMIN') | CUSTOMIZE_APPLICATION |
| WorkerController (admin) | hasRole('ADMIN') | VIEW_SETUP (read) / PLATFORM_ADMIN (write) |
| SetupAuditController | hasRole('ADMIN') | VIEW_SETUP |
| GovernorLimitsController (read) | hasRole('ADMIN') | VIEW_SETUP |
| GovernorLimitsController (write) | hasRole('PLATFORM_ADMIN') | PLATFORM_ADMIN (keep) |
| TenantController | hasRole('PLATFORM_ADMIN') | PLATFORM_ADMIN (keep) |
| PicklistController (write) | hasRole('ADMIN') or CUSTOMIZE_APPLICATION | CUSTOMIZE_APPLICATION (keep, but from DB) |
| ValidationRuleController (write) | hasRole('ADMIN') or CUSTOMIZE_APPLICATION | CUSTOMIZE_APPLICATION (keep, but from DB) |
| RecordTypeController (write) | hasRole('ADMIN') or CUSTOMIZE_APPLICATION | CUSTOMIZE_APPLICATION (keep, but from DB) |
| FieldHistoryController | hasRole('ADMIN') or VIEW_ALL_DATA | VIEW_ALL_DATA (keep, but from DB) |

### 2.3 SecurityService — Central Authorization Bean

```java
@Service("securityService")
public class SecurityService {

    private final PermissionResolutionService permissionService;
    private final UserService userService;

    /** Called by @PreAuthorize annotations */
    public boolean hasSystemPermission(MethodSecurityExpressionOperations root, String permission) {
        Authentication auth = root.getAuthentication();
        if (isPlatformAdmin(auth)) return true;
        String userId = resolveUserId(auth);
        String tenantId = TenantContextHolder.requireTenantId();
        return permissionService.hasSystemPermission(tenantId, userId, permission);
    }

    /** Check object-level permission for a collection */
    public boolean hasObjectPermission(MethodSecurityExpressionOperations root,
                                        String collectionId, String action) {
        // Similar pattern — resolve user, check DB-backed permissions
    }

    /** PLATFORM_ADMIN check — bypasses all tenant-level permissions */
    public boolean isPlatformAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }
}
```

### 2.4 Implement MyPermissionsController for Real

Replace stubs with actual permission resolution:

```java
@GetMapping("/objects/{collectionName}")
public ResponseEntity<ObjectPermissionsResponse> getObjectPermissions(
        @PathVariable String collectionName) {
    String tenantId = TenantContextHolder.requireTenantId();
    String userId = SecurityContextHolder.getContext().getAuthentication().getName();
    // Resolve collection by name → collectionId
    // Call PermissionResolutionService.getObjectPermissions()
    // Return real permissions
}

@GetMapping("/fields/{collectionName}")
public ResponseEntity<List<FieldPermissionResponse>> getFieldPermissions(
        @PathVariable String collectionName) {
    // Similar — return actual field visibility per user
}
```

Add new endpoint for system permissions:

```java
@GetMapping("/system")
public ResponseEntity<Map<String, Boolean>> getSystemPermissions() {
    // Returns all system permissions and their grant status for the current user
    // UI uses this to show/hide features
}
```

---

## Phase 3: Tenant Data Isolation Enforcement

### 3.1 Hibernate Tenant Filter

Add automatic tenant filtering at the JPA level so no service method can accidentally leak cross-tenant data:

```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
}
```

Apply via a request-scoped Hibernate filter activator:

```java
@Component
public class TenantFilterActivator {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener
    public void onRequest(/* request event */) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter")
               .setParameter("tenantId", TenantContextHolder.requireTenantId());
    }
}
```

### 3.2 Interceptor-Based Activation

The filter activator must run on every request that has a tenant context. Implement as a `HandlerInterceptor` registered in `WebMvcConfigurer`:

```java
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.disableFilter("tenantFilter");
        }
    }
}
```

Register in configuration:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterInterceptor)
                .addPathPatterns("/control/**")
                .excludePathPatterns("/control/bootstrap", "/control/tenants/slug-map",
                                     "/control/ui-bootstrap", "/internal/**");
    }
}
```

### 3.3 Migrate Entities

All tenant-scoped entities extend `TenantScopedEntity` instead of `BaseEntity`. Complete list of entities requiring migration:

| Entity | Current Base | Table | Notes |
|--------|-------------|-------|-------|
| Collection | BaseEntity | collection | Core metadata entity |
| Field | BaseEntity | field | Child of collection |
| User | Plain JPA | platform_user | Already has tenant_id |
| UserGroup | BaseEntity | user_group | Already has tenant_id |
| GroupMembership | Plain JPA | group_membership | Inherits tenant scope from group |
| OidcProvider | BaseEntity | oidc_provider | Already has tenant_id |
| Profile | BaseEntity (new) | profile | New in Phase 1 |
| PermissionSet | BaseEntity (new) | permission_set | New in Phase 1 |
| PageLayout | BaseEntity | page_layout | |
| ListView | BaseEntity | listview | |
| Report | BaseEntity | report | |
| Dashboard | BaseEntity | dashboard | |
| WorkflowRule | BaseEntity | workflow_rule | |
| Flow | BaseEntity | flow | |
| ApprovalProcess | BaseEntity | approval_process | |
| ScheduledJob | BaseEntity | scheduled_job | |
| EmailTemplate | BaseEntity | email_template | |
| Script | BaseEntity | script | |
| Webhook | BaseEntity | webhook | |
| ConnectedApp | BaseEntity | connected_app | |
| ValidationRule | BaseEntity | validation_rule | |
| RecordType | BaseEntity | record_type | |
| GlobalPicklist | BaseEntity | global_picklist | |
| Note | BaseEntity | note | |
| Attachment | BaseEntity | attachment | |
| SetupAuditEntry | BaseEntity | setup_audit | |
| LoginHistory | Plain JPA | login_history | Already has tenant_id |

### 3.4 Cross-Tenant Operations

Some operations legitimately need to query across tenants (platform admin, gateway bootstrap, internal APIs). These bypass the tenant filter:

```java
@Service
public class CrossTenantOperationHelper {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Execute a callback with the tenant filter temporarily disabled.
     * Only callable from methods annotated with @PlatformAdminOnly or from internal services.
     */
    public <T> T withoutTenantFilter(Supplier<T> callback) {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return callback.get();
        } finally {
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                session.enableFilter("tenantFilter")
                       .setParameter("tenantId", tenantId);
            }
        }
    }
}
```

Use cases for cross-tenant queries:
- `TenantController` (PLATFORM_ADMIN managing tenants)
- `GatewayBootstrapController` (bootstrap/slug-map endpoints)
- `InternalOidcController` (internal OIDC provider lookup)
- `TenantSchemaManager` (provisioning)

### 3.5 Write-Side Enforcement

The Hibernate filter only protects reads. For writes, add a `@PrePersist` / `@PreUpdate` listener that validates the entity's tenant_id matches the current context:

```java
@Component
public class TenantWriteGuard {

    @PrePersist
    @PreUpdate
    public void validateTenantOnWrite(Object entity) {
        if (entity instanceof TenantScopedEntity scoped) {
            String currentTenantId = TenantContextHolder.getTenantId();
            if (currentTenantId != null && scoped.getTenantId() != null
                    && !currentTenantId.equals(scoped.getTenantId())) {
                throw new SecurityException(
                    "Attempted to write entity to tenant " + scoped.getTenantId()
                    + " from tenant context " + currentTenantId);
            }
        }
    }
}
```

This prevents a bug from accidentally writing data into another tenant's partition.

---

## Phase 4: UI Security Enforcement

### 4.1 Permission Context

New React context that fetches and caches the user's resolved permissions:

```typescript
// PermissionContext.tsx
interface PermissionContextValue {
  systemPermissions: Record<string, boolean>
  getObjectPermissions: (collectionName: string) => Promise<ObjectPermissions>
  getFieldPermissions: (collectionName: string) => Promise<FieldPermission[]>
  hasPermission: (permission: string) => boolean
  isLoading: boolean
}
```

Fetched on app initialization from:
- `GET /control/my-permissions/system` → system permissions
- `GET /control/my-permissions/objects/{collection}` → per collection (lazy, cached)
- `GET /control/my-permissions/fields/{collection}` → per collection (lazy, cached)

### 4.2 Route-Level Enforcement

Update `App.tsx` to enforce permissions on all admin routes:

```typescript
// Setup routes — require VIEW_SETUP system permission
<Route path="setup" element={
  <ProtectedRoute requiredPermissions={['VIEW_SETUP']}>
    <SetupShell />
  </ProtectedRoute>
}>
  {/* Collections — require CUSTOMIZE_APPLICATION */}
  <Route path="collections" element={
    <RequirePermission permission="CUSTOMIZE_APPLICATION"><CollectionsPage /></RequirePermission>
  } />

  {/* Users — require MANAGE_USERS */}
  <Route path="users" element={
    <RequirePermission permission="MANAGE_USERS"><UsersPage /></RequirePermission>
  } />

  {/* Reports — require MANAGE_REPORTS */}
  <Route path="reports" element={
    <RequirePermission permission="MANAGE_REPORTS"><ReportsPage /></RequirePermission>
  } />

  {/* Tenants — PLATFORM_ADMIN only (role-based, not permission-based) */}
  <Route path="tenants" element={
    <ProtectedRoute requiredRoles={['PLATFORM_ADMIN']}><TenantsPage /></ProtectedRoute>
  } />

  {/* ... all other setup routes with appropriate permissions */}
</Route>
```

Complete route → permission mapping:

| Route | Required Permission |
|-------|-------------------|
| /setup (all) | VIEW_SETUP |
| /setup/collections, /setup/picklists, /setup/layouts, /setup/pages, /setup/menus | CUSTOMIZE_APPLICATION |
| /setup/users | MANAGE_USERS |
| /setup/oidc-providers | MANAGE_CONNECTED_APPS |
| /setup/reports, /setup/dashboards | MANAGE_REPORTS |
| /setup/workflow-rules, /setup/flows, /setup/scheduled-jobs | MANAGE_WORKFLOWS |
| /setup/approvals | MANAGE_APPROVALS |
| /setup/email-templates | MANAGE_EMAIL_TEMPLATES |
| /setup/scripts, /setup/webhooks, /setup/connected-apps | MANAGE_CONNECTED_APPS |
| /setup/bulk-jobs, /setup/export | MANAGE_DATA |
| /setup/packages, /setup/migrations | CUSTOMIZE_APPLICATION |
| /setup/workers, /setup/governor-limits, /setup/tenant-dashboard, /setup/system-health, /setup/audit-trail | VIEW_SETUP |
| /setup/tenants | PLATFORM_ADMIN (role) |
| /setup/plugins | CUSTOMIZE_APPLICATION |
| /setup/listviews | MANAGE_LISTVIEWS |

### 4.3 Navigation Filtering

The setup sidebar/navigation must filter items based on permissions:

```typescript
// SetupShell.tsx or equivalent
const { hasPermission } = usePermissions()

const setupNavItems = allSetupNavItems.filter(item => {
  if (item.permission) return hasPermission(item.permission)
  if (item.role) return userHasRole(item.role)
  return true
})
```

### 4.4 Component-Level Permission Checks

For end-user pages (record list, detail, form), enforce object permissions:

```typescript
// ResourceListPage — check can_read on collection
const { canCreate, canRead, canEdit, canDelete } = useObjectPermissions(collectionName)

if (!canRead) return <UnauthorizedMessage />

// Hide "New" button if !canCreate
// Hide edit/delete actions if !canEdit / !canDelete
```

For field-level visibility:

```typescript
// ObjectFormPage — filter visible fields
const fieldPermissions = useFieldPermissions(collectionName)

const visibleFields = fields.filter(f => {
  const perm = fieldPermissions[f.id]
  return perm !== 'HIDDEN'
})

const readOnlyFields = fields.filter(f => {
  const perm = fieldPermissions[f.id]
  return perm === 'READ_ONLY'
})
```

### 4.5 RequirePermission Component

```typescript
function RequirePermission({ permission, children, fallback }: {
  permission: string
  children: React.ReactNode
  fallback?: React.ReactNode
}) {
  const { hasPermission, isLoading } = usePermissions()

  if (isLoading) return <LoadingSpinner />
  if (!hasPermission(permission)) return fallback ?? <UnauthorizedPage />
  return <>{children}</>
}
```

---

## Phase 5: Setup UI for Security Management

### 5.1 New Setup Navigation Section

Add a "Security" section to the setup sidebar navigation (requires VIEW_SETUP permission):

```
Security
  ├── Profiles                   (MANAGE_USERS)
  ├── Permission Sets            (MANAGE_USERS)
  └── Login History              (MANAGE_USERS)
```

### 5.2 Profile Management Page

New page at `/setup/profiles` (requires MANAGE_USERS system permission).

**Profile List View:**
- Table with columns: Name, Description, System (badge), Users Assigned (count), Created, Updated
- System profiles show a lock icon and cannot be deleted
- "New Profile" button (disabled for system profiles section)
- Click row to open profile detail/editor

**Profile Detail/Editor:**

The editor is organized into tabs:

**Tab 1: Overview**
- Name (read-only for system profiles)
- Description
- Users currently assigned to this profile (list with links)

**Tab 2: System Permissions**
- Checklist of all defined system permissions with descriptions
- Toggle each on/off
- Group by category for readability:

```
Application Setup
  ☑ VIEW_SETUP — Access setup/admin pages
  ☑ CUSTOMIZE_APPLICATION — Manage collections, fields, picklists, layouts

User & Group Management
  ☐ MANAGE_USERS — Create, edit, and deactivate users
  ☐ MANAGE_GROUPS — Create, edit, and delete groups
  ☐ MANAGE_SHARING — Configure sharing rules

Automation & Workflows
  ☐ MANAGE_WORKFLOWS — Manage workflow rules, flows, scheduled jobs
  ☐ MANAGE_APPROVALS — Configure approval processes
  ☐ MANAGE_EMAIL_TEMPLATES — Create and edit email templates

Data & Reporting
  ☐ MANAGE_REPORTS — Create and edit reports, dashboards
  ☐ MANAGE_LISTVIEWS — Create and edit list views
  ☐ MANAGE_DATA — Export data, bulk operations
  ☐ VIEW_ALL_DATA — Read all records regardless of sharing
  ☐ MODIFY_ALL_DATA — Edit all records regardless of sharing

Integration & API
  ☐ MANAGE_CONNECTED_APPS — Manage connected apps, webhooks, scripts
  ☑ API_ACCESS — Access the API directly
```

**Tab 3: Object Permissions**
- Matrix view: rows = collections, columns = CRUD permissions
- Columns: Collection Name | Create | Read | Edit | Delete | View All | Modify All
- Checkbox for each cell
- "Select All" / "Deselect All" per column
- Filter/search for collections by name
- When a new collection is created, it auto-appears in this matrix with permissions based on the profile type

```
┌──────────────────┬────────┬──────┬──────┬────────┬──────────┬────────────┐
│ Collection       │ Create │ Read │ Edit │ Delete │ View All │ Modify All │
├──────────────────┼────────┼──────┼──────┼────────┼──────────┼────────────┤
│ Accounts         │   ☑    │  ☑   │  ☑   │   ☑    │    ☐     │     ☐      │
│ Contacts         │   ☑    │  ☑   │  ☑   │   ☐    │    ☐     │     ☐      │
│ Opportunities    │   ☑    │  ☑   │  ☑   │   ☑    │    ☑     │     ☐      │
│ Cases            │   ☑    │  ☑   │  ☑   │   ☐    │    ☐     │     ☐      │
└──────────────────┴────────┴──────┴──────┴────────┴──────────┴────────────┘
```

**Tab 4: Field Permissions**
- Collection selector dropdown at the top
- After selecting a collection, shows all fields with visibility toggle
- Columns: Field Name | Field Type | Visibility (VISIBLE / READ_ONLY / HIDDEN)
- Bulk actions: "Set All Visible", "Set All Read Only", "Set All Hidden"

```
Collection: [Accounts          ▼]

┌──────────────────┬────────────┬──────────────────────────────┐
│ Field            │ Type       │ Visibility                   │
├──────────────────┼────────────┼──────────────────────────────┤
│ Name             │ String     │ ● Visible ○ Read Only ○ Hidden│
│ Revenue          │ Number     │ ○ Visible ● Read Only ○ Hidden│
│ SSN              │ String     │ ○ Visible ○ Read Only ● Hidden│
│ Phone            │ String     │ ● Visible ○ Read Only ○ Hidden│
└──────────────────┴────────────┴──────────────────────────────┘
```

**Clone Profile:**
- Button to clone any profile (including system profiles)
- Creates a new non-system profile with identical permissions
- User provides a new name

### 5.3 Permission Set Management Page

New page at `/setup/permission-sets` (requires MANAGE_USERS system permission).

Same UI pattern as Profile Management with these differences:
- Permission sets are always custom (no system defaults, except optional seed sets)
- Assignment section shows both users AND groups
- A banner explains that permission sets are additive — they grant additional access on top of the user's profile

**Assignment UI:**
- "Assign Users" button → multi-select user picker dialog
- "Assign Groups" button → multi-select group picker dialog
- Table of current assignments with "Remove" action

### 5.4 User Management — Profile Assignment

Extend the existing user detail page (`/setup/users/{id}`):

**New "Security" section on user detail:**
- **Profile:** Dropdown selector showing all profiles, current profile highlighted
- **Permission Sets:** List of directly assigned permission sets with "Add" / "Remove" buttons
- **Group Permission Sets:** Read-only list of permission sets inherited via group membership (shows which group grants each)
- **Effective Permissions:** Expandable section that shows the resolved/merged permissions:
  - System permissions: green check or red X for each
  - Object permissions: summary table of CRUD per collection
  - Indicates source of each permission (Profile, Permission Set name, or Group name)

```
Security
─────────────────────────────────────
Profile:  [Standard User          ▼]

Permission Sets (Direct):
  ☑ Sales Team Data Access          [Remove]
  ☑ Marketing Reports               [Remove]
  [+ Add Permission Set]

Permission Sets (via Groups):
  ☑ Support Queue Access            (from: Support Team group)

▶ View Effective Permissions
```

### 5.5 Group Management — Permission Set Assignment

Extend the existing group detail page (`/setup/groups/{id}`):

**New "Permission Sets" section:**
- List of permission sets assigned to this group
- "Add Permission Set" / "Remove" actions
- Info text: "All members of this group (including nested groups) receive these permission sets"

### 5.6 New Profile/Permission Set APIs

Backend controllers needed to support the management UI:

```
ProfileController (/control/profiles)
  GET    /                        — List all profiles for tenant
  POST   /                        — Create profile (MANAGE_USERS)
  GET    /{id}                    — Get profile detail with all permissions
  PUT    /{id}                    — Update profile (MANAGE_USERS, not system profiles)
  DELETE /{id}                    — Delete profile (MANAGE_USERS, not system profiles, not if users assigned)
  POST   /{id}/clone              — Clone profile (MANAGE_USERS)
  GET    /{id}/system-permissions  — Get system permissions for profile
  PUT    /{id}/system-permissions  — Set system permissions (batch)
  GET    /{id}/object-permissions  — Get object permissions for profile
  PUT    /{id}/object-permissions  — Set object permissions (batch)
  GET    /{id}/field-permissions/{collectionId} — Get field permissions for profile + collection
  PUT    /{id}/field-permissions/{collectionId} — Set field permissions (batch)

PermissionSetController (/control/permission-sets)
  GET    /                        — List all permission sets for tenant
  POST   /                        — Create permission set (MANAGE_USERS)
  GET    /{id}                    — Get permission set detail
  PUT    /{id}                    — Update permission set (MANAGE_USERS)
  DELETE /{id}                    — Delete permission set (MANAGE_USERS, not if assigned)
  GET    /{id}/assignments        — List users and groups assigned
  POST   /{id}/assignments/users  — Assign to users (MANAGE_USERS)
  DELETE /{id}/assignments/users/{userId} — Unassign from user
  POST   /{id}/assignments/groups — Assign to groups (MANAGE_USERS)
  DELETE /{id}/assignments/groups/{groupId} — Unassign from group

MyPermissionsController (/control/my-permissions) — already exists, replace stubs
  GET    /system                   — Get current user's system permissions
  GET    /objects/{collectionName} — Get current user's object permissions for collection
  GET    /fields/{collectionName}  — Get current user's field permissions for collection
  GET    /effective                — Get full resolved permission summary for current user
```

### 5.7 Login History Page

New page at `/setup/login-history` (requires MANAGE_USERS system permission):

- Table: User, Email, Login Time, IP Address, Login Type, Status
- Filters: Date range, user, status (SUCCESS/FAILED/LOCKED_OUT), login type
- Sortable columns
- Links to user detail page
- Highlight failed/locked-out entries in red
- Data source: `login_history` table (already exists and is populated by `LoginTrackingFilter`)

---

## Phase 6: Gateway Security Hardening

### 6.1 Security Headers

Add a `SecurityHeadersFilter` (GlobalFilter, order 100) that sets security headers on all responses:

```java
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            headers.set("Cache-Control", "no-store");  // Prevent caching of API responses
            headers.set("Pragma", "no-cache");
        }));
    }

    @Override
    public int getOrder() { return 100; }
}
```

### 6.2 Request Size Limits

Prevent oversized request attacks:

```yaml
spring.cloud.gateway.httpclient:
  max-header-size: 16KB
spring.codec:
  max-in-memory-size: 10MB  # Max request body

# Per-route overrides for file upload endpoints
spring.cloud.gateway.routes:
  - id: attachments
    predicates:
      - Path=/control/attachments/**
    filters:
      - name: RequestSize
        args:
          maxSize: 25MB
```

### 6.3 CORS Lockdown

The current default of `CORS_ALLOWED_ORIGIN_PATTERN: *` is insecure for production. Lock down:

```yaml
# Production
CORS_ALLOWED_ORIGIN_PATTERN: https://emf-ui.rzware.com

# Staging (if needed)
CORS_ALLOWED_ORIGIN_PATTERN: https://*.staging.rzware.com
```

Also restrict which headers are allowed instead of `*`:

```java
config.setAllowedHeaders(List.of(
    "Authorization", "Content-Type", "Accept", "X-Correlation-ID",
    "X-Tenant-ID", "X-Tenant-Slug", "X-Requested-With"
));
```

### 6.4 Rate Limiting by IP

Add IP-based rate limiting for unauthenticated endpoints to prevent abuse:

```java
@Component
public class IpRateLimitFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter rateLimiter;

    private static final Set<String> UNAUTHENTICATED_PATHS = Set.of(
        "/control/bootstrap", "/control/ui-bootstrap", "/control/tenants/slug-map"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (UNAUTHENTICATED_PATHS.stream().noneMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(exchange);
        // Allow 100 requests per minute per IP for unauthenticated endpoints
        return rateLimiter.isAllowed("ip:" + clientIp, 100, Duration.ofMinutes(1))
            .flatMap(allowed -> allowed ? chain.filter(exchange) : respond429(exchange));
    }

    @Override
    public int getOrder() { return -150; } // Before JWT filter
}
```

### 6.5 Sensitive Header Stripping

Prevent downstream services from receiving forged internal headers:

```java
// In HeaderTransformationFilter — strip any client-supplied internal headers
// before adding gateway-verified values
ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
builder.headers(h -> {
    h.remove("X-Forwarded-User");
    h.remove("X-User-Id");
    h.remove("X-Forwarded-Roles");
    // Only X-Tenant-ID and X-Tenant-Slug are allowed from gateway's own resolution
});
```

This prevents a malicious client from injecting `X-Forwarded-User: admin` to impersonate another user.

### 6.6 JWT Audience Validation

Enforce audience claim validation when configured on OIDC providers:

```java
// In DynamicReactiveJwtDecoder
if (provider.getAudience() != null && !provider.getAudience().isEmpty()) {
    jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(provider.getIssuer()),
        new JwtClaimValidator<>("aud", aud -> aud != null && aud.contains(provider.getAudience()))
    ));
}
```

### 6.7 Token Expiry Grace Period

Configure a maximum acceptable clock skew for JWT validation to prevent issues with slightly out-of-sync clocks while keeping the window tight:

```yaml
emf.gateway.security:
  jwt-clock-skew-seconds: 30  # Default, configurable
```

---

## Phase 7: Audit Trail for Security Events

### 7.1 Security Audit Table

New migration (V49 or part of V48):

```sql
CREATE TABLE security_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) REFERENCES tenant(id),  -- NULL for platform-level events
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(30) NOT NULL,  -- AUTH, AUTHZ, CONFIG, DATA
    actor_user_id VARCHAR(36),            -- Who performed the action (NULL for anonymous)
    actor_email VARCHAR(320),             -- Denormalized for query without join
    target_type VARCHAR(50),              -- USER, PROFILE, PERMISSION_SET, OIDC_PROVIDER, etc.
    target_id VARCHAR(36),                -- ID of affected entity
    target_name VARCHAR(255),             -- Denormalized name for readability
    details JSONB,                        -- Event-specific details
    ip_address VARCHAR(45),               -- IPv4 or IPv6
    user_agent TEXT,
    correlation_id VARCHAR(36),           -- Request correlation ID
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_audit_tenant_time ON security_audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_security_audit_event_type ON security_audit_log(event_type, created_at DESC);
CREATE INDEX idx_security_audit_actor ON security_audit_log(actor_user_id, created_at DESC);
CREATE INDEX idx_security_audit_target ON security_audit_log(target_type, target_id, created_at DESC);
```

### 7.2 Event Types

| Category | Event Type | Details (JSONB) |
|----------|-----------|-----------------|
| **AUTH** | LOGIN_SUCCESS | `{provider, loginType}` |
| **AUTH** | LOGIN_FAILURE | `{email, reason, provider}` |
| **AUTH** | ACCOUNT_LOCKED | `{email, failedAttempts}` |
| **AUTH** | LOGOUT | `{}` |
| **AUTH** | TOKEN_REFRESH | `{provider}` |
| **AUTHZ** | PERMISSION_DENIED | `{endpoint, method, requiredPermission}` |
| **AUTHZ** | OBJECT_ACCESS_DENIED | `{collection, action, recordId}` |
| **CONFIG** | PROFILE_CREATED | `{profileName}` |
| **CONFIG** | PROFILE_UPDATED | `{profileName, changes: [{field, old, new}]}` |
| **CONFIG** | PROFILE_DELETED | `{profileName}` |
| **CONFIG** | PROFILE_ASSIGNED | `{profileName, targetUserId, targetEmail}` |
| **CONFIG** | PERMSET_CREATED | `{permsetName}` |
| **CONFIG** | PERMSET_UPDATED | `{permsetName, changes}` |
| **CONFIG** | PERMSET_DELETED | `{permsetName}` |
| **CONFIG** | PERMSET_ASSIGNED | `{permsetName, targetType: USER|GROUP, targetId, targetName}` |
| **CONFIG** | PERMSET_UNASSIGNED | `{permsetName, targetType, targetId, targetName}` |
| **CONFIG** | OIDC_PROVIDER_CREATED | `{providerName, issuer}` |
| **CONFIG** | OIDC_PROVIDER_UPDATED | `{providerName, changes}` |
| **CONFIG** | OIDC_PROVIDER_DELETED | `{providerName}` |
| **CONFIG** | ROLE_MAPPING_CHANGED | `{providerName, oldMapping, newMapping}` |
| **DATA** | USER_CREATED | `{email, profileName}` |
| **DATA** | USER_DEACTIVATED | `{email, reason}` |
| **DATA** | USER_ACTIVATED | `{email}` |
| **DATA** | USER_PROVISIONED_JIT | `{email, provider}` |
| **DATA** | BULK_EXPORT | `{collection, recordCount, format}` |
| **DATA** | BULK_DELETE | `{collection, recordCount}` |

### 7.3 Audit Service

```java
@Service
public class SecurityAuditService {

    private final SecurityAuditLogRepository repository;

    /**
     * Log a security event. Automatically captures actor from SecurityContext,
     * tenant from TenantContextHolder, IP and user-agent from request context.
     */
    public void log(SecurityEvent event) {
        SecurityAuditLog entry = SecurityAuditLog.builder()
            .tenantId(TenantContextHolder.getTenantId())
            .eventType(event.type())
            .eventCategory(event.category())
            .actorUserId(resolveCurrentUserId())
            .actorEmail(resolveCurrentUserEmail())
            .targetType(event.targetType())
            .targetId(event.targetId())
            .targetName(event.targetName())
            .details(event.details())
            .ipAddress(RequestContextHolder.getIpAddress())
            .userAgent(RequestContextHolder.getUserAgent())
            .correlationId(RequestContextHolder.getCorrelationId())
            .build();

        repository.save(entry);
    }

    /** Convenience methods for common events */
    public void logPermissionDenied(String endpoint, String method, String requiredPermission) {
        log(SecurityEvent.authz("PERMISSION_DENIED")
            .detail("endpoint", endpoint)
            .detail("method", method)
            .detail("requiredPermission", requiredPermission)
            .build());
    }

    public void logProfileAssigned(String userId, String email, String profileId, String profileName) {
        log(SecurityEvent.config("PROFILE_ASSIGNED")
            .target("USER", userId, email)
            .detail("profileName", profileName)
            .build());
    }

    public void logLoginSuccess(String userId, String provider, String loginType) {
        log(SecurityEvent.auth("LOGIN_SUCCESS")
            .target("USER", userId, null)
            .detail("provider", provider)
            .detail("loginType", loginType)
            .build());
    }

    // ... etc for each event type
}
```

### 7.4 Integration Points

Wire the audit service into existing code paths:

| Location | Event |
|----------|-------|
| `SecurityService` (Phase 2) | PERMISSION_DENIED on every denied @PreAuthorize check |
| `LoginTrackingFilter` | LOGIN_SUCCESS, LOGIN_FAILURE (supplement existing login_history) |
| `UserService.provisionOrUpdate()` | USER_PROVISIONED_JIT |
| `UserService.deactivateUser()` | USER_DEACTIVATED |
| `ProfileController` | PROFILE_CREATED, PROFILE_UPDATED, PROFILE_DELETED |
| `PermissionSetController` | PERMSET_* events |
| `OidcProviderService` | OIDC_PROVIDER_* events |
| `UserController` (profile assignment) | PROFILE_ASSIGNED |
| `ExportController` | BULK_EXPORT |
| `BulkJobController` | BULK_DELETE |

### 7.5 Audit Log API

New controller for querying the audit trail (requires VIEW_SETUP permission):

```
SecurityAuditController (/control/security-audit)
  GET /                — Query audit log with filters (tenant, dateRange, eventType, category, actor, target)
  GET /summary         — Aggregated stats (events per day, failed logins, permission denials)
  GET /export          — Export audit log as CSV (MANAGE_DATA permission)
```

### 7.6 Retention Policy

- Default retention: 90 days
- Configurable per tenant via tenant settings
- Scheduled job to purge old entries: `SecurityAuditPurgeJob` runs nightly
- PLATFORM_ADMIN can set retention per tenant

```yaml
emf.control-plane.security.audit:
  retention-days: ${SECURITY_AUDIT_RETENTION_DAYS:90}
  purge-batch-size: 10000
```

---

## Implementation Order

| Phase | Scope | Dependencies |
|-------|-------|-------------|
| **Phase 1** | DB schema, entities, PermissionResolutionService, seed data | None |
| **Phase 2** | SecurityService, endpoint authorization, MyPermissions implementation | Phase 1 |
| **Phase 3** | Hibernate tenant filter, entity migration | Phase 1 |
| **Phase 4** | PermissionContext, route enforcement, component enforcement | Phase 2 |
| **Phase 5** | Profile/PermSet management UI | Phase 2, Phase 4 |
| **Phase 6** | Gateway hardening | Independent |
| **Phase 7** | Audit trail | Phase 2 |

Phases 1-3 are backend-only and can proceed together. Phase 4 depends on Phase 2's API being live. Phase 5 depends on Phase 4's UI infrastructure. Phase 6 is fully independent. Phase 7 can start after Phase 2.

---

## Migration Strategy

### Existing Users

When V48 runs on an existing deployment:
1. Create all 7 default profiles (System Administrator, Standard User, Read Only, Marketing User, Contract Manager, Solution Manager, Minimum Access) for each tenant
2. Create object permission grants for each profile against all existing collections
3. Assign the **System Administrator** profile to all existing users (preserving current "everyone can do everything" behavior)
4. Tenant admins can then create additional profiles, customize the defaults, and reassign users to more restrictive profiles as needed

### Backward Compatibility

- During rollout, the `PermissionResolutionService` can have a feature flag: `emf.security.permissions.enabled=true`
- When disabled, it returns permissive defaults (current behavior)
- When enabled, it resolves from database
- This allows phased rollout per tenant

### OIDC Role Integration

OIDC roles continue to work as today for PLATFORM_ADMIN detection. The permission model operates WITHIN a tenant — OIDC roles determine WHICH tenant the user can access and whether they're a platform admin. Fine-grained permissions within a tenant come from Profiles and Permission Sets.

---

## Testing Strategy

Security features require rigorous testing. Every phase includes mandatory test coverage.

### Backend Unit Tests

**PermissionResolutionService tests:**
- User with System Administrator profile → all permissions granted
- User with Standard User profile → only expected permissions granted
- User with Minimum Access profile → no permissions granted
- User with profile + one permission set → additive merge, most permissive wins
- User with profile + multiple permission sets → all merge correctly
- User in group with permission set → inherits group's permission set
- User in nested group → inherits parent group's permission set
- PLATFORM_ADMIN role → bypasses all tenant permission checks
- User with no profile → denied everything (fail-closed)
- Cache invalidation on profile change
- Cache invalidation on permission set assignment change
- Cache invalidation on group membership change

**SecurityService tests:**
- `hasSystemPermission()` — granted, denied, PLATFORM_ADMIN bypass
- `hasObjectPermission()` — each CRUD action, view_all, modify_all
- Permission check with no tenant context → exception (not silent pass)
- Permission check with no authenticated user → denied

**Controller authorization tests (per controller):**
- Request with correct permission → 200
- Request without required permission → 403
- Request with no authentication → 401
- PLATFORM_ADMIN → 200 regardless of permission
- Object-level checks for data endpoints (CRUD matching)

**Tenant isolation tests:**
- Query with Hibernate filter active → only current tenant's data returned
- Write with wrong tenant_id → SecurityException
- Cross-tenant query without filter → only via CrossTenantOperationHelper
- Service method without tenant context → exception, not leak

**Profile/PermissionSet CRUD tests:**
- Create, read, update, delete profiles
- Cannot delete system profiles
- Cannot delete profiles with assigned users
- Permission set assignment to users and groups
- Clone profile produces identical permissions with new name

### Backend Integration Tests

- Full request lifecycle: JWT → tenant resolution → permission check → data access
- Permission denied returns proper 403 with error details
- Audit log entry created on permission denial
- Profile change takes effect on next request (cache invalidation)
- New collection auto-creates object permissions for all profiles
- OIDC JIT provisioning assigns correct default profile

### Frontend Tests

**PermissionContext tests:**
- Fetches system permissions on mount
- `hasPermission()` returns correct value
- Lazy-loads object permissions per collection
- Handles API errors gracefully (loading state, error state)
- Re-fetches after permission change events

**RequirePermission component tests:**
- Renders children when permission granted
- Renders fallback/unauthorized when permission denied
- Shows loading state while permissions loading

**Route protection tests:**
- Admin pages inaccessible without VIEW_SETUP
- Each setup page requires its specific permission
- End-user pages enforce object-level read permission
- Form pages enforce edit/create permission
- Navigation items filtered by permission (hidden items not rendered)

**Component-level tests:**
- "New" button hidden when can_create = false
- "Edit" button hidden when can_edit = false
- "Delete" button hidden when can_delete = false
- Hidden fields not rendered in forms or detail views
- Read-only fields rendered as non-editable in forms

### Security-Specific Test Cases

- **Privilege escalation:** User modifies JWT claims client-side → server rejects (JWKS validation)
- **Tenant isolation:** User with access to Tenant A cannot access Tenant B's data by changing URL slug
- **Horizontal privilege escalation:** Standard User cannot call admin-only endpoints even with direct API calls
- **Permission set removal:** Removing a permission set takes effect immediately
- **Profile downgrade:** Changing user from System Admin to Standard User immediately restricts access
- **Concurrent access:** Permission check is thread-safe under concurrent requests
- **Cache poisoning:** Stale cached permissions cannot grant access after revocation (TTL + invalidation)

---

## Files to Create/Modify

### New Files — Backend (Control Plane)

| File | Phase | Purpose |
|------|-------|---------|
| `V48__add_permission_model.sql` | 1 | Permission tables, default profiles, seed data |
| `V49__add_security_audit_log.sql` | 7 | Security audit log table |
| `Profile.java` | 1 | JPA entity |
| `PermissionSet.java` | 1 | JPA entity |
| `ProfileSystemPermission.java` | 1 | JPA entity |
| `ProfileObjectPermission.java` | 1 | JPA entity |
| `ProfileFieldPermission.java` | 1 | JPA entity |
| `PermsetSystemPermission.java` | 1 | JPA entity |
| `PermsetObjectPermission.java` | 1 | JPA entity |
| `PermsetFieldPermission.java` | 1 | JPA entity |
| `UserPermissionSet.java` | 1 | JPA entity |
| `GroupPermissionSet.java` | 1 | JPA entity |
| `SecurityAuditLog.java` | 7 | JPA entity |
| `TenantScopedEntity.java` | 3 | Base entity with Hibernate tenant filter |
| `ProfileRepository.java` | 1 | Repository |
| `PermissionSetRepository.java` | 1 | Repository |
| `ProfileSystemPermissionRepository.java` | 1 | Repository |
| `ProfileObjectPermissionRepository.java` | 1 | Repository |
| `ProfileFieldPermissionRepository.java` | 1 | Repository |
| `UserPermissionSetRepository.java` | 1 | Repository |
| `GroupPermissionSetRepository.java` | 1 | Repository |
| `SecurityAuditLogRepository.java` | 7 | Repository |
| `PermissionResolutionService.java` | 1 | Core permission resolution engine |
| `ProfileService.java` | 5 | Profile CRUD + permission management |
| `PermissionSetService.java` | 5 | Permission set CRUD + assignment |
| `SecurityService.java` | 2 | Spring Security integration bean |
| `SecurityAuditService.java` | 7 | Audit event logging |
| `DefaultProfileSeeder.java` | 1 | Seeds default profiles during tenant provisioning |
| `ProfileController.java` | 5 | Profile CRUD API |
| `PermissionSetController.java` | 5 | Permission Set CRUD API |
| `SecurityAuditController.java` | 7 | Audit log query API |
| `TenantFilterInterceptor.java` | 3 | Hibernate tenant filter activator |
| `TenantWriteGuard.java` | 3 | JPA PrePersist/PreUpdate tenant validation |
| `CrossTenantOperationHelper.java` | 3 | Safe cross-tenant query helper |
| `SystemPermission.java` | 1 | Enum of all system permissions |
| `ObjectPermissions.java` | 1 | DTO for resolved object permissions |
| `FieldVisibility.java` | 1 | Enum (VISIBLE, READ_ONLY, HIDDEN) |
| `ResolvedPermissions.java` | 1 | DTO for full resolved permission set |

### New Files — Gateway

| File | Phase | Purpose |
|------|-------|---------|
| `SecurityHeadersFilter.java` | 6 | Adds security response headers |
| `IpRateLimitFilter.java` | 6 | IP-based rate limiting for unauthenticated endpoints |

### New Files — Frontend (emf-ui)

| File | Phase | Purpose |
|------|-------|---------|
| `PermissionContext.tsx` | 4 | React context for fetching/caching user permissions |
| `PermissionProvider.tsx` | 4 | Provider component wrapping permission state |
| `RequirePermission.tsx` | 4 | Gate component that renders children only if permitted |
| `usePermissions.ts` | 4 | Hook for system permission checks |
| `useObjectPermissions.ts` | 4 | Hook for collection-level CRUD checks |
| `useFieldPermissions.ts` | 4 | Hook for field visibility checks |
| `UnauthorizedPage.tsx` | 4 | Page shown when user lacks permission |
| `ProfilesPage.tsx` | 5 | Profile list + detail/editor |
| `ProfileEditor.tsx` | 5 | Profile permission editor (tabs: system, object, field) |
| `PermissionSetsPage.tsx` | 5 | Permission set list + detail/editor |
| `PermissionSetEditor.tsx` | 5 | Permission set editor + assignment UI |
| `ObjectPermissionMatrix.tsx` | 5 | Reusable CRUD matrix component |
| `FieldPermissionEditor.tsx` | 5 | Reusable field visibility editor component |
| `SystemPermissionChecklist.tsx` | 5 | Reusable system permission toggle list |
| `LoginHistoryPage.tsx` | 5 | Login audit trail UI |
| `EffectivePermissionsPanel.tsx` | 5 | Shows resolved permissions on user detail |

### Modified Files

| File | Phase | Change |
|------|-------|--------|
| **Every controller (~39 files)** | 2 | Replace/add @PreAuthorize using SecurityService |
| `MyPermissionsController.java` | 2 | Replace stubs with real PermissionResolutionService calls; add GET /system and GET /effective |
| `User.java` | 1 | Add profileId field |
| `TenantSchemaManager.java` | 1 | Seed all 7 default profiles during tenant provisioning |
| `CollectionService.java` | 1 | Auto-create object permissions for all profiles when new collection created |
| `UserService.java` | 1 | Assign System Administrator profile to first user, Standard User to subsequent; invalidate permission cache on profile change |
| `LoginTrackingFilter.java` | 7 | Add SecurityAuditService.logLoginSuccess/Failure calls |
| `OidcProviderService.java` | 7 | Add SecurityAuditService calls on provider changes |
| `WebConfig.java` (or create) | 3 | Register TenantFilterInterceptor |
| **All ~26 tenant-scoped entities** | 3 | Change base class to TenantScopedEntity, add @Filter annotations |
| `App.tsx` | 4 | Wrap all setup routes with RequirePermission; wrap end-user routes with object permission checks |
| `SetupShell.tsx` / setup navigation | 4 | Filter nav items by hasPermission() |
| `ResourceListPage.tsx` | 4 | Hide New/Edit/Delete based on object permissions |
| `ObjectFormPage.tsx` | 4 | Filter fields by field permissions; enforce read-only |
| `ResourceDetailPage.tsx` | 4 | Filter fields by visibility; hide edit/delete buttons |
| `TopNavBar.tsx` | 4 | Use hasPermission('VIEW_SETUP') instead of role check for admin toggle |
| `UserDetailPage.tsx` (or equivalent) | 5 | Add profile assignment dropdown, permission set section, effective permissions panel |
| `GroupDetailPage.tsx` (or equivalent) | 5 | Add permission set assignment section |
| Gateway `SecurityConfig.java` | 6 | Tighten CORS allowed headers, remove `*` default |
| Gateway `application.yml` | 6 | Add request size limits |
| Gateway `HeaderTransformationFilter.java` | 6 | Strip forged internal headers before adding verified values |
| Gateway `DynamicReactiveJwtDecoder.java` | 6 | Add audience validation when configured |
| `en.json` / `ar.json` | 4,5 | Add i18n keys for profiles, permission sets, permission names, security UI labels |
