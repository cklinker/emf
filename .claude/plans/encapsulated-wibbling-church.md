# Consolidated Security Model: Groups, Grants, and Record-Level Security

## Context

The platform has two parallel authorization systems that don't interoperate cleanly:

- **System A (Legacy Policy/Role):** `policy` table with `{"roles": ["ADMIN", "*"]}` rules → `route_policy` → `PolicyEvaluator` checks JWT roles. Kafka-pushed to gateway `AuthzConfigCache`.
- **System B (Profile/PermSet):** `profile` + `permission_set` → `object_permission` / `field_permission` / `system_permission` → `PermissionResolver` OR-merges. Fetched on-demand via `/internal/permissions/{userId}`.

The gateway uses a feature flag (`use-profiles`) to pick one, falling back to the other. Other issues:
- `UserGroup` exists but is flat, manual-only, and disconnected from OIDC groups
- `RecordAccessService` implements a 7-step record access algorithm but is **never called** in the request flow
- `rolesMapping` in `oidc_provider` is stored but never applied during JWT processing
- No group nesting support

**Goal:** Consolidate into a single group-and-grant-based authorization model where:
1. Groups are the primary access unit (users → groups, groups → groups)
2. OIDC groups auto-sync to platform groups
3. Access grants give groups/users permissions on resources (collections, fields, system)
4. One authorization path in the gateway (no dual-mode, no fallbacks)
5. Record-level security is actually enforced

---

## Phase S1: Nestable Groups with OIDC Source Tracking

**Goal:** Extend `user_group` to support nesting and OIDC source, without changing authorization flow yet.

### Migration V45

```sql
-- Extend user_group with source tracking
ALTER TABLE user_group ADD COLUMN source VARCHAR(20) DEFAULT 'MANUAL' NOT NULL;
ALTER TABLE user_group ADD COLUMN oidc_group_name VARCHAR(200);
CREATE INDEX idx_user_group_oidc ON user_group(tenant_id, oidc_group_name);

-- New group_membership table (replaces user_group_member, supports nesting)
CREATE TABLE group_membership (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    member_type VARCHAR(10) NOT NULL CHECK (member_type IN ('USER','GROUP')),
    member_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_group_membership UNIQUE (group_id, member_type, member_id)
);
CREATE INDEX idx_gm_group ON group_membership(group_id);
CREATE INDEX idx_gm_member ON group_membership(member_type, member_id);

-- Migrate existing data
INSERT INTO group_membership (id, group_id, member_type, member_id, created_at)
SELECT gen_random_uuid()::varchar, group_id, 'USER', user_id, NOW()
FROM user_group_member ON CONFLICT DO NOTHING;

-- Create well-known "All Authenticated Users" group (per tenant)
INSERT INTO user_group (id, tenant_id, name, description, group_type, source, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000300', t.id, 'All Authenticated Users',
       'System group: all authenticated users', 'SYSTEM', 'SYSTEM', NOW(), NOW()
FROM tenant t WHERE NOT EXISTS (
    SELECT 1 FROM user_group WHERE id = '00000000-0000-0000-0000-000000000300'
);
```

### Control-Plane Files

| File | Action |
|------|--------|
| `entity/GroupMembership.java` | **CREATE** — JPA entity for `group_membership` |
| `entity/UserGroup.java` | **MODIFY** — add `source`, `oidcGroupName` fields; add `@OneToMany` to `GroupMembership` |
| `repository/GroupMembershipRepository.java` | **CREATE** — queries for member lookup, effective groups |
| `repository/UserGroupRepository.java` | **MODIFY** — add `findByTenantIdAndOidcGroupName()`, `findByTenantIdAndSource()` |
| `service/GroupMembershipResolver.java` | **CREATE** — flattens nested groups recursively (cycle detection, max depth=10) |
| `service/UserGroupService.java` | **MODIFY** — CRUD for nested membership |

### Tests
- `GroupMembershipResolverTest` — cycle detection, depth limiting, basic flattening, diamond inheritance

---

## Phase S2: OIDC Group Sync

**Goal:** Auto-sync JWT groups to platform groups on user authentication.

### Control-Plane Files

| File | Action |
|------|--------|
| `service/OidcGroupSyncService.java` | **CREATE** — `syncGroups(tenantId, userId, List<String> oidcGroups)`: find-or-create OIDC groups, add user to `group_membership`, remove from stale OIDC groups |
| `service/UserService.java` | **MODIFY** — `provisionOrUpdate()` accepts groups param, calls `OidcGroupSyncService` |
| `controller/InternalPermissionController.java` | **MODIFY** — add `POST /internal/sync-groups` endpoint |

### Gateway Files

| File | Action |
|------|--------|
| `auth/GatewayPrincipal.java` | **MODIFY** — add `groups` field (separate from `roles`) |
| `auth/PrincipalExtractor.java` | **MODIFY** — extract `groups` claim into dedicated field |
| `service/GroupSyncClient.java` | **CREATE** — reactive WebClient, fire-and-forget call to `/internal/sync-groups` on first auth |

### Flow
1. `JwtAuthenticationFilter` validates JWT, `PrincipalExtractor` extracts `groups` claim
2. `GroupSyncClient` fires async POST to `/internal/sync-groups` with `{tenantId, userId, groups}`
3. Control plane creates OIDC-sourced groups if missing, syncs membership
4. User's first request uses cached permissions; next cache refresh picks up new groups

---

## Phase S3: Access Grant Model

**Goal:** Unified `access_grant` table replaces profiles, permission sets, and policies.

### Migration V46 — Create Table

```sql
CREATE TABLE access_grant (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES tenant(id),
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    grantee_type VARCHAR(10) NOT NULL CHECK (grantee_type IN ('USER','GROUP')),
    grantee_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(20) NOT NULL CHECK (resource_type IN ('COLLECTION','SYSTEM','FIELD')),
    resource_id VARCHAR(200) NOT NULL,
    permissions JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ag_grantee ON access_grant(grantee_type, grantee_id);
CREATE INDEX idx_ag_resource ON access_grant(resource_type, resource_id);
CREATE INDEX idx_ag_tenant ON access_grant(tenant_id);
```

### Permission JSON shapes by resource_type

- **COLLECTION:** `{"canCreate":true, "canRead":true, "canEdit":true, "canDelete":true, "canViewAll":false, "canModifyAll":false}`
- **SYSTEM:** `{"granted":true}` (resource_id = permission key like `MANAGE_USERS`)
- **FIELD:** `{"visibility":"VISIBLE"}` (resource_id = field_id)

### Migration V47 — Migrate Existing Data

Converts profiles + permission sets into access grants:
1. For each profile → create a "Profile: {name}" group → add all users with that profile → create group-level grants for each `object_permission`, `field_permission`, `system_permission`
2. For each permission_set → create a "PermSet: {name}" group → add assigned users → create group-level grants
3. For each `route_policy` with `policy.rules = {"roles": ["*"]}` → create grant for "All Authenticated Users" group
4. For specific role-based policies → create grants for corresponding OIDC-synced groups

### Control-Plane Files

| File | Action |
|------|--------|
| `entity/AccessGrant.java` | **CREATE** — JPA entity |
| `repository/AccessGrantRepository.java` | **CREATE** — `findActiveByGranteeUserOrGroups(userId, groupIds)` |
| `service/GrantResolver.java` | **CREATE** — replaces `PermissionResolver` |
| `service/AccessGrantService.java` | **CREATE** — CRUD for access grants |
| `controller/AccessGrantController.java` | **CREATE** — REST API |
| `controller/InternalPermissionController.java` | **MODIFY** — switch to `GrantResolver` behind feature flag |
| `dto/AccessGrantDto.java`, `dto/CreateAccessGrantRequest.java` | **CREATE** |

### GrantResolver Algorithm

```java
public EffectivePermissions resolve(String userId) {
    Set<String> groupIds = groupMembershipResolver.getEffectiveGroupIds(userId);
    List<AccessGrant> grants = accessGrantRepository.findActiveByGrantee(userId, groupIds);
    // OR-merge by resource — same semantics as current PermissionResolver
    return merge(grants);
}
```

---

## Phase S4: Gateway Unification

**Goal:** Single authorization path. Remove dual-mode system.

### Gateway Files

| File | Action |
|------|--------|
| `authz/RouteAuthorizationFilter.java` | **MODIFY** — remove `useProfiles` flag, remove `PolicyEvaluator` path. Single path: `ProfilePolicyEvaluator` (now backed by `GrantResolver`) |
| `authz/PolicyEvaluator.java` | **DEPRECATE** — no longer used for route auth |
| `authz/AuthzConfigCache.java` | **DEPRECATE** — no longer needed |
| `authz/FieldAuthorizationFilter.java` | **MODIFY** — use grant-based field permissions from `EffectivePermissions` |
| `listener/ConfigEventListener.java` | **MODIFY** — replace `authz-changed` handler with `permission-invalidation` handler (evict specific users from Redis) |
| `service/RouteConfigService.java` | **MODIFY** — remove authz config loading from bootstrap |

### Control-Plane Files

| File | Action |
|------|--------|
| `event/ConfigEventPublisher.java` | **MODIFY** — add `publishPermissionChanged(List<String> affectedUserIds)` |
| `service/AccessGrantService.java` | **MODIFY** — on grant create/update/delete, compute affected users and publish invalidation event |

### Key Design Decision
Authorization is no longer Kafka-pushed (bootstrap + events → AuthzConfigCache). It's pulled on-demand via `/internal/permissions/{userId}` → Redis cache with TTL + explicit invalidation on grant changes.

---

## Phase S5: Record-Level Security Enforcement

**Goal:** Wire `RecordAccessService` into the actual request flow.

### Architecture
Record-level filtering happens in the **worker** (not gateway). The gateway only handles collection-level and field-level auth. Record data (ownership, sharing) lives in the worker's database.

### Gateway Changes

| File | Action |
|------|--------|
| `filter/HeaderTransformationFilter.java` | **MODIFY** — add `X-EMF-User-Id` and `X-EMF-Effective-Groups` headers when proxying to workers |

### Control-Plane Changes

| File | Action |
|------|--------|
| `service/RecordAccessService.java` | **MODIFY** — use `GroupMembershipResolver` for group checks; replace simplified role hierarchy with group hierarchy |

### Worker Integration
- Worker request interceptor extracts `X-EMF-User-Id` and `X-EMF-Effective-Groups` headers
- For list queries: calls `RecordAccessService.buildSharingWhereClause()` to append filtering
- For single-record ops: calls `RecordAccessService.canAccess()`

---

## Phase S6: Cleanup

**Goal:** Remove deprecated entities after verification period.

### Migration V48 — Deprecate Old Tables

```sql
ALTER TABLE profile RENAME TO _deprecated_profile;
ALTER TABLE object_permission RENAME TO _deprecated_object_permission;
ALTER TABLE field_permission RENAME TO _deprecated_field_permission;
ALTER TABLE system_permission RENAME TO _deprecated_system_permission;
ALTER TABLE permission_set RENAME TO _deprecated_permission_set;
ALTER TABLE permset_object_permission RENAME TO _deprecated_permset_object_permission;
ALTER TABLE permset_field_permission RENAME TO _deprecated_permset_field_permission;
ALTER TABLE permset_system_permission RENAME TO _deprecated_permset_system_permission;
ALTER TABLE user_permission_set RENAME TO _deprecated_user_permission_set;
ALTER TABLE user_group_member RENAME TO _deprecated_user_group_member;
-- Remove profile_id FK from platform_user
ALTER TABLE platform_user DROP COLUMN profile_id;
```

### Files to Remove
- `entity/Profile.java`, `entity/ObjectPermission.java`, `entity/FieldPermission.java`, `entity/SystemPermission.java`
- `entity/PermissionSet.java`, `entity/PermsetObjectPermission.java`, `entity/PermsetFieldPermission.java`, `entity/PermsetSystemPermission.java`, `entity/UserPermissionSet.java`
- `service/PermissionResolver.java`, `service/ProfileService.java`, `service/PermissionSetService.java`
- All associated repositories
- Gateway: `PolicyEvaluator.java`, `AuthzConfigCache.java`, legacy `RoutePolicy.java`, `FieldPolicy.java`

---

## Feature Flags

| Flag | Default | Phase |
|------|---------|-------|
| `emf.security.oidc-group-sync` | `false` | S2 |
| `emf.security.use-access-grants` | `false` | S3 |
| `emf.security.unified-gateway-authz` | `false` | S4 |
| `emf.security.record-level-enforcement` | `false` | S5 |

**Rollout:** S1 (always on) → S2 (enable sync) → S3 (enable grants, verify) → S4 (single path) → S5 (record security) → S6 (cleanup after bake period)

---

## Implementation Order

This is a large effort spanning multiple PRs. Recommended implementation:

1. **PR: Phase S1** — Group nesting + GroupMembershipResolver (no auth changes)
2. **PR: Phase S2** — OIDC group sync (groups start populating)
3. **PR: Phase S3** — AccessGrant entity + GrantResolver + data migration (behind flag)
4. **PR: Phase S4** — Gateway unification (behind flag, flip after deploy)
5. **PR: Phase S5** — Record-level enforcement (behind flag)
6. **PR: Phase S6** — Cleanup deprecated tables/code

---

## Verification

```bash
# Java tests
mvn clean install -DskipTests -f emf-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events -am -B
mvn verify -f emf-control-plane/pom.xml -B
mvn verify -f emf-gateway/pom.xml -B

# Frontend (unchanged but must pass)
cd emf-web && npm install && npm run lint && npm run typecheck && npm run format:check && npm run test:coverage
```

Each phase should have comprehensive unit tests for:
- `GroupMembershipResolver` — nesting, cycles, depth limits
- `OidcGroupSyncService` — create, update, remove stale groups
- `GrantResolver` — OR-merge semantics, user+group grants, all resource types
- `RouteAuthorizationFilter` — single-path authorization
- `RecordAccessService` — 7-step algorithm with group-based checks
