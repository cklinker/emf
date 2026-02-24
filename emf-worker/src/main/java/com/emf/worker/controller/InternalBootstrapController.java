package com.emf.worker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Internal API endpoints for the gateway to bootstrap its configuration.
 *
 * <p>These endpoints replace the control plane's bootstrap/internal APIs.
 * They are called by the gateway on startup and periodically:
 * <ul>
 *   <li>{@code /internal/bootstrap} — collections + governor limits for route setup</li>
 *   <li>{@code /internal/tenants/slug-map} — tenant slug → ID mapping</li>
 *   <li>{@code /internal/oidc/by-issuer} — OIDC provider lookup for JWT validation</li>
 *   <li>{@code /internal/permissions} — effective permission resolution for a user</li>
 * </ul>
 *
 * <p>These endpoints are unauthenticated (internal network only, same as the
 * control plane's corresponding endpoints).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/internal")
public class InternalBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(InternalBootstrapController.class);

    private static final String SELECT_ACTIVE_COLLECTIONS = """
            SELECT id, name, path, system_collection
            FROM collection WHERE active = true
            """;

    private static final String SELECT_ROUTABLE_TENANTS = """
            SELECT id, slug FROM tenant
            WHERE status != 'DECOMMISSIONED' AND slug IS NOT NULL
            """;

    private static final String SELECT_TENANT_LIMITS = """
            SELECT id, limits FROM tenant
            WHERE status NOT IN ('DECOMMISSIONED', 'SUSPENDED')
            """;

    private static final String SELECT_OIDC_PROVIDER_BY_ISSUER = """
            SELECT id, name, issuer, jwks_uri, audience, active,
                   client_id, roles_claim, roles_mapping
            FROM oidc_provider WHERE issuer = ? AND active = true
            LIMIT 1
            """;

    // Permission resolution queries
    private static final String SELECT_USER_BY_EMAIL = """
            SELECT id, profile_id FROM platform_user
            WHERE email = ? AND tenant_id = ? AND status = 'ACTIVE'
            LIMIT 1
            """;

    private static final String SELECT_PROFILE_SYSTEM_PERMISSIONS = """
            SELECT permission_name, granted
            FROM profile_system_permission WHERE profile_id = ?
            """;

    private static final String SELECT_PROFILE_OBJECT_PERMISSIONS = """
            SELECT collection_id, can_create, can_read, can_edit,
                   can_delete, can_view_all, can_modify_all
            FROM profile_object_permission WHERE profile_id = ?
            """;

    private static final String SELECT_PROFILE_FIELD_PERMISSIONS = """
            SELECT collection_id, field_id, visibility
            FROM profile_field_permission WHERE profile_id = ?
            """;

    private static final String SELECT_USER_PERMSET_IDS = """
            SELECT permission_set_id FROM user_permission_set WHERE user_id = ?
            """;

    private static final String SELECT_USER_GROUP_IDS = """
            SELECT group_id FROM group_membership
            WHERE member_type = 'USER' AND member_id = ?
            """;

    private static final String SELECT_PERMSET_SYSTEM_PERMISSIONS = """
            SELECT permission_name, granted
            FROM permset_system_permission WHERE permission_set_id = ? AND granted = true
            """;

    private static final String SELECT_PERMSET_OBJECT_PERMISSIONS = """
            SELECT collection_id, can_create, can_read, can_edit,
                   can_delete, can_view_all, can_modify_all
            FROM permset_object_permission WHERE permission_set_id = ?
            """;

    private static final String SELECT_PERMSET_FIELD_PERMISSIONS = """
            SELECT collection_id, field_id, visibility
            FROM permset_field_permission WHERE permission_set_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InternalBootstrapController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the bootstrap configuration needed by the gateway on startup.
     *
     * <p>Includes all active collections (for route setup) and per-tenant
     * governor limits (for rate limiting).
     *
     * <p>This replaces the control plane's {@code GET /control/bootstrap} endpoint.
     *
     * @return bootstrap configuration with collections and governor limits
     */
    @GetMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> getBootstrapConfig() {
        log.debug("REST request to get gateway bootstrap configuration");

        List<Map<String, Object>> collections = loadCollections();
        Map<String, Map<String, Object>> governorLimits = loadGovernorLimits();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("collections", collections);
        response.put("governorLimits", governorLimits);

        log.info("Returning bootstrap config: {} collections, {} tenant governor limits",
                collections.size(), governorLimits.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns a slug → tenantId mapping for all routable tenants.
     *
     * <p>Includes ACTIVE and PROVISIONING tenants (excludes DECOMMISSIONED
     * and tenants without a slug). Used by the gateway's TenantSlugCache.
     *
     * <p>This replaces the control plane's {@code GET /control/tenants/slug-map} endpoint.
     *
     * @return map of tenant slug to tenant ID
     */
    @GetMapping("/tenants/slug-map")
    public ResponseEntity<Map<String, String>> getSlugMap() {
        log.debug("REST request to get tenant slug map");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_ROUTABLE_TENANTS);

        Map<String, String> slugMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            String slug = (String) row.get("slug");
            if (id != null && slug != null && !slug.isBlank()) {
                slugMap.put(slug, id);
            }
        }

        log.info("Returning tenant slug map with {} entries", slugMap.size());
        return ResponseEntity.ok(slugMap);
    }

    /**
     * Looks up an OIDC provider by its issuer URI.
     *
     * <p>Returns the provider's JWKS URI and audience for JWT validation.
     * The gateway uses this to resolve the correct JWKS endpoint for each
     * JWT issuer it encounters.
     *
     * <p>This replaces the control plane's {@code GET /internal/oidc/by-issuer} endpoint.
     *
     * @param issuer the OIDC issuer URI to look up
     * @return OIDC provider info including jwksUri and audience
     */
    @GetMapping("/oidc/by-issuer")
    public ResponseEntity<Map<String, Object>> getOidcProviderByIssuer(
            @RequestParam String issuer) {
        log.debug("Internal lookup: OIDC provider by issuer: {}", issuer);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_OIDC_PROVIDER_BY_ISSUER, issuer);

        if (rows.isEmpty()) {
            log.warn("No active OIDC provider found for issuer: {}", issuer);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> provider = rows.get(0);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", provider.get("id"));
        response.put("name", provider.get("name"));
        response.put("issuer", provider.get("issuer"));
        response.put("jwksUri", provider.get("jwks_uri"));
        response.put("audience", provider.get("audience"));
        response.put("clientId", provider.get("client_id"));
        response.put("rolesClaim", provider.get("roles_claim"));
        response.put("rolesMapping", provider.get("roles_mapping"));

        return ResponseEntity.ok(response);
    }

    /**
     * Resolves the effective permissions for a user by combining profile permissions
     * with direct and group-inherited permission set permissions.
     *
     * <p>The resolution algorithm mirrors the control plane's
     * {@code PermissionResolutionService.resolveForUser()} logic:
     * <ol>
     *   <li>Look up the user by email and tenant</li>
     *   <li>Load profile system, object, and field permissions</li>
     *   <li>Find all applicable permission set IDs (direct + group-inherited)</li>
     *   <li>Merge permission set permissions (most-permissive-wins)</li>
     * </ol>
     *
     * @param email    the user's email address
     * @param tenantId the tenant UUID
     * @return resolved permissions with system, object, and field permissions
     */
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> resolvePermissions(
            @RequestParam String email,
            @RequestParam String tenantId) {
        log.debug("Internal permission resolution for email={} tenant={}", email, tenantId);

        // 1. Find user
        List<Map<String, Object>> userRows = jdbcTemplate.queryForList(
                SELECT_USER_BY_EMAIL, email, tenantId);

        if (userRows.isEmpty()) {
            log.warn("No active user found for email={} tenant={}", email, tenantId);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> userRow = userRows.get(0);
        String userId = (String) userRow.get("id");
        String profileId = (String) userRow.get("profile_id");

        // Start with empty permissions
        Map<String, Boolean> systemPerms = new LinkedHashMap<>();
        Map<String, Map<String, Object>> objectPerms = new LinkedHashMap<>();
        Map<String, Map<String, String>> fieldPerms = new LinkedHashMap<>();

        // 2. Load profile permissions if user has a profile
        if (profileId != null) {
            loadSystemPermissions(profileId, SELECT_PROFILE_SYSTEM_PERMISSIONS, systemPerms);
            loadObjectPermissions(profileId, SELECT_PROFILE_OBJECT_PERMISSIONS, objectPerms);
            loadFieldPermissions(profileId, SELECT_PROFILE_FIELD_PERMISSIONS, fieldPerms);
        }

        // 3. Collect all applicable permission set IDs
        Set<String> permissionSetIds = new LinkedHashSet<>();

        // Direct user assignments
        List<Map<String, Object>> directPermSets = jdbcTemplate.queryForList(
                SELECT_USER_PERMSET_IDS, userId);
        for (Map<String, Object> row : directPermSets) {
            String psId = (String) row.get("permission_set_id");
            if (psId != null) {
                permissionSetIds.add(psId);
            }
        }

        // Group-inherited assignments
        List<Map<String, Object>> groupRows = jdbcTemplate.queryForList(
                SELECT_USER_GROUP_IDS, userId);
        if (!groupRows.isEmpty()) {
            List<String> groupIds = new ArrayList<>();
            for (Map<String, Object> row : groupRows) {
                String gId = (String) row.get("group_id");
                if (gId != null) {
                    groupIds.add(gId);
                }
            }
            if (!groupIds.isEmpty()) {
                String placeholders = String.join(",",
                        groupIds.stream().map(id -> "?").toList());
                String sql = String.format(
                        "SELECT DISTINCT permission_set_id FROM group_permission_set WHERE group_id IN (%s)",
                        placeholders);
                List<Map<String, Object>> groupPermSets = jdbcTemplate.queryForList(
                        sql, groupIds.toArray());
                for (Map<String, Object> row : groupPermSets) {
                    String psId = (String) row.get("permission_set_id");
                    if (psId != null) {
                        permissionSetIds.add(psId);
                    }
                }
            }
        }

        // 4. Merge permission set permissions (most-permissive-wins)
        for (String permSetId : permissionSetIds) {
            mergeSystemPermissions(permSetId, systemPerms);
            mergeObjectPermissions(permSetId, objectPerms);
            mergeFieldPermissions(permSetId, fieldPerms);
        }

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("systemPermissions", systemPerms);
        response.put("objectPermissions", objectPerms);
        response.put("fieldPermissions", fieldPerms);

        log.info("Resolved permissions for email={}: {} system perms, {} object perms, {} field perm collections",
                email, systemPerms.size(), objectPerms.size(), fieldPerms.size());

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Permission resolution helpers
    // =========================================================================

    private void loadSystemPermissions(String sourceId, String sql,
                                        Map<String, Boolean> systemPerms) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, sourceId);
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("permission_name");
            Boolean granted = toBoolean(row.get("granted"));
            if (name != null && Boolean.TRUE.equals(granted)) {
                systemPerms.put(name, true);
            }
        }
    }

    private void loadObjectPermissions(String sourceId, String sql,
                                        Map<String, Map<String, Object>> objectPerms) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, sourceId);
        for (Map<String, Object> row : rows) {
            String collectionId = (String) row.get("collection_id");
            if (collectionId == null) continue;

            Map<String, Object> perms = new LinkedHashMap<>();
            perms.put("canCreate", toBoolean(row.get("can_create")));
            perms.put("canRead", toBoolean(row.get("can_read")));
            perms.put("canEdit", toBoolean(row.get("can_edit")));
            perms.put("canDelete", toBoolean(row.get("can_delete")));
            perms.put("canViewAll", toBoolean(row.get("can_view_all")));
            perms.put("canModifyAll", toBoolean(row.get("can_modify_all")));

            // Merge: most-permissive-wins (OR)
            objectPerms.merge(collectionId, perms, this::mergeObjectPermissionMaps);
        }
    }

    private void loadFieldPermissions(String sourceId, String sql,
                                       Map<String, Map<String, String>> fieldPerms) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, sourceId);
        for (Map<String, Object> row : rows) {
            String collectionId = (String) row.get("collection_id");
            String fieldId = (String) row.get("field_id");
            String visibility = (String) row.get("visibility");
            if (collectionId == null || fieldId == null || visibility == null) continue;

            Map<String, String> collFields =
                    fieldPerms.computeIfAbsent(collectionId, k -> new LinkedHashMap<>());

            // Most permissive wins: VISIBLE > READ_ONLY > HIDDEN
            String existing = collFields.get(fieldId);
            if (existing == null || morePermissive(visibility, existing)) {
                collFields.put(fieldId, visibility);
            }
        }
    }

    private void mergeSystemPermissions(String permSetId, Map<String, Boolean> systemPerms) {
        loadSystemPermissions(permSetId, SELECT_PERMSET_SYSTEM_PERMISSIONS, systemPerms);
    }

    private void mergeObjectPermissions(String permSetId,
                                         Map<String, Map<String, Object>> objectPerms) {
        loadObjectPermissions(permSetId, SELECT_PERMSET_OBJECT_PERMISSIONS, objectPerms);
    }

    private void mergeFieldPermissions(String permSetId,
                                        Map<String, Map<String, String>> fieldPerms) {
        loadFieldPermissions(permSetId, SELECT_PERMSET_FIELD_PERMISSIONS, fieldPerms);
    }

    private Map<String, Object> mergeObjectPermissionMaps(Map<String, Object> existing,
                                                           Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            Boolean existingVal = toBoolean(merged.get(entry.getKey()));
            Boolean incomingVal = toBoolean(entry.getValue());
            // OR logic: most permissive wins
            merged.put(entry.getKey(), Boolean.TRUE.equals(existingVal) || Boolean.TRUE.equals(incomingVal));
        }
        return merged;
    }

    private boolean morePermissive(String a, String b) {
        return visibilityOrdinal(a) < visibilityOrdinal(b);
    }

    private int visibilityOrdinal(String visibility) {
        return switch (visibility) {
            case "VISIBLE" -> 0;
            case "READ_ONLY" -> 1;
            case "HIDDEN" -> 2;
            default -> 3;
        };
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private List<Map<String, Object>> loadCollections() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_ACTIVE_COLLECTIONS);

        List<Map<String, Object>> collections = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("name");

            // Skip the virtual __control-plane collection
            if ("__control-plane".equals(name)) {
                continue;
            }

            Map<String, Object> collection = new LinkedHashMap<>();
            collection.put("id", row.get("id"));
            collection.put("name", name);
            collection.put("path", row.get("path"));
            collection.put("systemCollection", Boolean.TRUE.equals(row.get("system_collection")));
            collections.add(collection);
        }

        return collections;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadGovernorLimits() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_TENANT_LIMITS);

        Map<String, Map<String, Object>> governorLimits = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String tenantId = (String) row.get("id");
            Object limitsObj = row.get("limits");

            int apiCallsPerDay = 100_000; // default
            if (limitsObj instanceof String limitsStr && !limitsStr.isBlank()) {
                try {
                    Map<String, Object> limits = objectMapper.readValue(limitsStr,
                            objectMapper.getTypeFactory().constructMapType(
                                    HashMap.class, String.class, Object.class));
                    Object apiCalls = limits.get("apiCallsPerDay");
                    if (apiCalls instanceof Number num) {
                        apiCallsPerDay = num.intValue();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse limits for tenant {}: {}", tenantId, e.getMessage());
                }
            } else if (limitsObj instanceof Map) {
                Map<String, Object> limits = (Map<String, Object>) limitsObj;
                Object apiCalls = limits.get("apiCallsPerDay");
                if (apiCalls instanceof Number num) {
                    apiCallsPerDay = num.intValue();
                }
            }

            Map<String, Object> limitConfig = new LinkedHashMap<>();
            limitConfig.put("apiCallsPerDay", apiCallsPerDay);
            governorLimits.put(tenantId, limitConfig);
        }

        return governorLimits;
    }
}
