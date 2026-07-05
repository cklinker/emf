package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kelta.worker.service.delegated.PrivilegedPermissions;

/**
 * Read-side queries for delegated administration. Scope rows are read fresh on every request
 * (no cache, no NATS broadcast — the quick_action precedent for non-registry config), so
 * enforcement always sees the latest configuration.
 */
@Repository
public class DelegatedAdminScopeRepository {

    private final JdbcTemplate jdbcTemplate;

    public DelegatedAdminScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Active scopes for a tenant, JSONB columns normalized to their JSON string form
     * (mirrors {@code ScheduledJobRepository.findDueJobs}).
     */
    public List<Map<String, Object>> findActiveScopes(String tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, delegated_user_ids, manageable_profile_ids, "
                        + "assignable_permission_set_ids, can_create_users, can_deactivate_users, "
                        + "can_reset_passwords "
                        + "FROM delegated_admin_scope WHERE tenant_id = ? AND active = true",
                tenantId);
        return rows.stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>(row);
            normalized.computeIfPresent("delegated_user_ids", (k, v) -> v.toString());
            normalized.computeIfPresent("manageable_profile_ids", (k, v) -> v.toString());
            normalized.computeIfPresent("assignable_permission_set_ids", (k, v) -> v.toString());
            return normalized;
        }).toList();
    }

    /**
     * Of the given profile ids, the ones whose profile currently grants any privileged
     * permission ({@link PrivilegedPermissions#SET}). Empty input → empty result.
     */
    public Set<String> findPrivilegedProfileIds(Collection<String> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Set.of();
        }
        String sql = "SELECT DISTINCT profile_id FROM profile_system_permission "
                + "WHERE granted = true AND profile_id IN (" + placeholders(profileIds.size()) + ") "
                + "AND permission_name IN (" + placeholders(PrivilegedPermissions.SET.size()) + ")";
        Object[] args = concat(profileIds, PrivilegedPermissions.SET);
        return Set.copyOf(jdbcTemplate.queryForList(sql, String.class, args));
    }

    /**
     * Of the given permission-set ids, the ones that currently grant any privileged permission.
     */
    public Set<String> findPrivilegedPermissionSetIds(Collection<String> permissionSetIds) {
        if (permissionSetIds == null || permissionSetIds.isEmpty()) {
            return Set.of();
        }
        String sql = "SELECT DISTINCT permission_set_id FROM permset_system_permission "
                + "WHERE granted = true AND permission_set_id IN (" + placeholders(permissionSetIds.size()) + ") "
                + "AND permission_name IN (" + placeholders(PrivilegedPermissions.SET.size()) + ")";
        Object[] args = concat(permissionSetIds, PrivilegedPermissions.SET);
        return Set.copyOf(jdbcTemplate.queryForList(sql, String.class, args));
    }

    /** Of the given user ids, the ones that exist in the tenant. */
    public Set<String> findExistingUserIds(Collection<String> userIds, String tenantId) {
        return findExistingIds("platform_user", userIds, tenantId);
    }

    /** Of the given profile ids, the ones that exist in the tenant. */
    public Set<String> findExistingProfileIds(Collection<String> profileIds, String tenantId) {
        return findExistingIds("profile", profileIds, tenantId);
    }

    /** Of the given permission-set ids, the ones that exist in the tenant. */
    public Set<String> findExistingPermissionSetIds(Collection<String> permissionSetIds, String tenantId) {
        return findExistingIds("permission_set", permissionSetIds, tenantId);
    }

    /** id → name for the given profile ids within the tenant. */
    public Map<String, String> findProfileNames(Collection<String> profileIds, String tenantId) {
        return findNames("profile", profileIds, tenantId);
    }

    /** id → name for the given permission-set ids within the tenant. */
    public Map<String, String> findPermissionSetNames(Collection<String> permissionSetIds, String tenantId) {
        return findNames("permission_set", permissionSetIds, tenantId);
    }

    private Map<String, String> findNames(String table, Collection<String> ids, String tenantId) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT id, name FROM " + table
                + " WHERE tenant_id = ? AND id IN (" + placeholders(ids.size()) + ")";
        Object[] args = new Object[ids.size() + 1];
        args[0] = tenantId;
        int i = 1;
        for (String id : ids) {
            args[i++] = id;
        }
        return jdbcTemplate.queryForList(sql, args).stream()
                .collect(Collectors.toMap(r -> (String) r.get("id"), r -> (String) r.get("name")));
    }

    private Set<String> findExistingIds(String table, Collection<String> ids, String tenantId) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        String sql = "SELECT id FROM " + table
                + " WHERE tenant_id = ? AND id IN (" + placeholders(ids.size()) + ")";
        Object[] args = new Object[ids.size() + 1];
        args[0] = tenantId;
        int i = 1;
        for (String id : ids) {
            args[i++] = id;
        }
        return Set.copyOf(jdbcTemplate.queryForList(sql, String.class, args));
    }

    private static String placeholders(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> "?")
                .collect(Collectors.joining(", "));
    }

    private static Object[] concat(Collection<String> first, Collection<String> second) {
        Object[] args = new Object[first.size() + second.size()];
        int i = 0;
        for (String v : first) {
            args[i++] = v;
        }
        for (String v : second) {
            args[i++] = v;
        }
        return args;
    }
}
