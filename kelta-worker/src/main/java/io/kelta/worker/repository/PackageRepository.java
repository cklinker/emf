package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PackageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PackageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public List<Map<String, Object>> findAllByTenantId(String tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM package_history WHERE tenant_id = ? ORDER BY created_at DESC",
                tenantId
        );
    }

    public Optional<Map<String, Object>> findByIdAndTenantId(String id, String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT * FROM package_history WHERE id = ? AND tenant_id = ?",
                id, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public String save(String tenantId, String name, String version, String description,
                       String type, String status, String itemsJson) {
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO package_history (id, tenant_id, name, version, description, type, status, items, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, name, version, description, type, status, itemsJson, now, now
        );
        return id;
    }

    public void updateStatus(String id, String status, String errorMessage) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "UPDATE package_history SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status, errorMessage, now, id
        );
    }

    public List<Map<String, Object>> findCollectionsByIds(String tenantId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM collection WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findFieldsByCollectionIds(String tenantId, List<String> collectionIds) {
        if (collectionIds.isEmpty()) return List.of();
        String placeholders = String.join(",", collectionIds.stream().map(i -> "?").toList());
        Object[] params = new Object[collectionIds.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < collectionIds.size(); i++) params[i + 1] = collectionIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT f.* FROM field f JOIN collection c ON f.collection_id = c.id " +
                        "WHERE c.tenant_id = ? AND f.collection_id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findRolesByIds(String tenantId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM role WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findPoliciesByIds(String tenantId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM policy WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findRoutePoliciesByPolicyIds(String tenantId, List<String> policyIds) {
        if (policyIds.isEmpty()) return List.of();
        String placeholders = String.join(",", policyIds.stream().map(i -> "?").toList());
        Object[] params = new Object[policyIds.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < policyIds.size(); i++) params[i + 1] = policyIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT rp.* FROM route_policy rp JOIN collection c ON rp.collection_id = c.id " +
                        "WHERE c.tenant_id = ? AND rp.policy_id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findFieldPoliciesByPolicyIds(String tenantId, List<String> policyIds) {
        if (policyIds.isEmpty()) return List.of();
        String placeholders = String.join(",", policyIds.stream().map(i -> "?").toList());
        Object[] params = new Object[policyIds.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < policyIds.size(); i++) params[i + 1] = policyIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT fp.* FROM field_policy fp JOIN field f ON fp.field_id = f.id " +
                        "JOIN collection c ON f.collection_id = c.id " +
                        "WHERE c.tenant_id = ? AND fp.policy_id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findUiPagesByIds(String tenantId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM ui_page WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findUiMenusByIds(String tenantId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM ui_menu WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findUiMenuItemsByMenuIds(String tenantId, List<String> menuIds) {
        if (menuIds.isEmpty()) return List.of();
        String placeholders = String.join(",", menuIds.stream().map(i -> "?").toList());
        Object[] params = new Object[menuIds.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < menuIds.size(); i++) params[i + 1] = menuIds.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM ui_menu_item WHERE tenant_id = ? AND menu_id IN (" + placeholders + ") ORDER BY display_order ASC",
                params
        );
    }

    public List<Map<String, Object>> findCollectionsByNames(String tenantId, List<String> names) {
        if (names.isEmpty()) return List.of();
        String placeholders = String.join(",", names.stream().map(i -> "?").toList());
        Object[] params = new Object[names.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < names.size(); i++) params[i + 1] = names.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM collection WHERE tenant_id = ? AND name IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findRolesByNames(String tenantId, List<String> names) {
        if (names.isEmpty()) return List.of();
        String placeholders = String.join(",", names.stream().map(i -> "?").toList());
        Object[] params = new Object[names.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < names.size(); i++) params[i + 1] = names.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM role WHERE tenant_id = ? AND name IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findPoliciesByNames(String tenantId, List<String> names) {
        if (names.isEmpty()) return List.of();
        String placeholders = String.join(",", names.stream().map(i -> "?").toList());
        Object[] params = new Object[names.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < names.size(); i++) params[i + 1] = names.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM policy WHERE tenant_id = ? AND name IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findUiPagesByPaths(String tenantId, List<String> paths) {
        if (paths.isEmpty()) return List.of();
        String placeholders = String.join(",", paths.stream().map(i -> "?").toList());
        Object[] params = new Object[paths.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < paths.size(); i++) params[i + 1] = paths.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM ui_page WHERE tenant_id = ? AND path IN (" + placeholders + ")",
                params
        );
    }

    public List<Map<String, Object>> findUiMenusByNames(String tenantId, List<String> names) {
        if (names.isEmpty()) return List.of();
        String placeholders = String.join(",", names.stream().map(i -> "?").toList());
        Object[] params = new Object[names.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < names.size(); i++) params[i + 1] = names.get(i);
        return jdbcTemplate.queryForList(
                "SELECT * FROM ui_menu WHERE tenant_id = ? AND name IN (" + placeholders + ")",
                params
        );
    }
}
