package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for bootstrap-related database queries.
 *
 * <p>Encapsulates SQL access for collections, tenants, OIDC providers,
 * and permission resolution used by the gateway bootstrap endpoints.
 */
@Repository
public class BootstrapRepository {

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
                   client_id, roles_claim, roles_mapping,
                   groups_claim, groups_profile_mapping
            FROM oidc_provider WHERE issuer = ? AND active = true
            LIMIT 1
            """;

    private static final String SELECT_OIDC_PROVIDER_BY_ISSUER_AND_TENANT = """
            SELECT id, name, issuer, jwks_uri, audience, active,
                   client_id, roles_claim, roles_mapping,
                   groups_claim, groups_profile_mapping
            FROM oidc_provider WHERE issuer = ? AND tenant_id = ? AND active = true
            LIMIT 1
            """;

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
            SELECT collection_id, can_create, can_read, can_edit, can_delete
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
            SELECT collection_id, can_create, can_read, can_edit, can_delete
            FROM permset_object_permission WHERE permission_set_id = ?
            """;

    private static final String SELECT_PERMSET_FIELD_PERMISSIONS = """
            SELECT collection_id, field_id, visibility
            FROM permset_field_permission WHERE permission_set_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public BootstrapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findActiveCollections() {
        return jdbcTemplate.queryForList(SELECT_ACTIVE_COLLECTIONS);
    }

    public List<Map<String, Object>> findRoutableTenants() {
        return jdbcTemplate.queryForList(SELECT_ROUTABLE_TENANTS);
    }

    public List<Map<String, Object>> findTenantLimits() {
        return jdbcTemplate.queryForList(SELECT_TENANT_LIMITS);
    }

    public Optional<Map<String, Object>> findOidcProviderByIssuer(String issuer) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_OIDC_PROVIDER_BY_ISSUER, issuer);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Map<String, Object>> findOidcProviderByIssuerAndTenant(String issuer, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_OIDC_PROVIDER_BY_ISSUER_AND_TENANT, issuer, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Map<String, Object>> findActiveUserByEmail(String email, String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_USER_BY_EMAIL, email, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map<String, Object>> findProfileSystemPermissions(String profileId) {
        return jdbcTemplate.queryForList(SELECT_PROFILE_SYSTEM_PERMISSIONS, profileId);
    }

    public List<Map<String, Object>> findProfileObjectPermissions(String profileId) {
        return jdbcTemplate.queryForList(SELECT_PROFILE_OBJECT_PERMISSIONS, profileId);
    }

    public List<Map<String, Object>> findProfileFieldPermissions(String profileId) {
        return jdbcTemplate.queryForList(SELECT_PROFILE_FIELD_PERMISSIONS, profileId);
    }

    public List<Map<String, Object>> findUserPermissionSetIds(String userId) {
        return jdbcTemplate.queryForList(SELECT_USER_PERMSET_IDS, userId);
    }

    public List<Map<String, Object>> findUserGroupIds(String userId) {
        return jdbcTemplate.queryForList(SELECT_USER_GROUP_IDS, userId);
    }

    public List<Map<String, Object>> findGroupPermissionSetIds(List<String> groupIds) {
        String placeholders = String.join(",",
                groupIds.stream().map(id -> "?").toList());
        String sql = String.format(
                "SELECT DISTINCT permission_set_id FROM group_permission_set WHERE group_id IN (%s)",
                placeholders);
        return jdbcTemplate.queryForList(sql, groupIds.toArray());
    }

    public List<Map<String, Object>> findPermsetSystemPermissions(String permSetId) {
        return jdbcTemplate.queryForList(SELECT_PERMSET_SYSTEM_PERMISSIONS, permSetId);
    }

    public List<Map<String, Object>> findPermsetObjectPermissions(String permSetId) {
        return jdbcTemplate.queryForList(SELECT_PERMSET_OBJECT_PERMISSIONS, permSetId);
    }

    public List<Map<String, Object>> findPermsetFieldPermissions(String permSetId) {
        return jdbcTemplate.queryForList(SELECT_PERMSET_FIELD_PERMISSIONS, permSetId);
    }
}
