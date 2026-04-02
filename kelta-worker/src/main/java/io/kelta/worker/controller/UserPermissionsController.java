package io.kelta.worker.controller;

import io.kelta.worker.cache.WorkerCacheManager;
import io.kelta.worker.service.CerbosPermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the current user's permissions (system, object, and field level).
 *
 * <p>{@code GET /api/me/permissions} returns a JSON object with:
 * <ul>
 *   <li>{@code systemPermissions} — map of permission name to boolean</li>
 *   <li>{@code objectPermissions} — map of collection name to CRUD flags</li>
 *   <li>{@code fieldPermissions} — map of collection name to field visibility map</li>
 * </ul>
 *
 * <p>Identity is resolved from the gateway-forwarded headers
 * ({@code X-User-Profile-Id}).
 */
@RestController
@RequestMapping("/api/me")
public class UserPermissionsController {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionsController.class);

    private final CerbosPermissionResolver permissionResolver;
    private final JdbcTemplate jdbcTemplate;
    private final WorkerCacheManager cacheManager;

    public UserPermissionsController(CerbosPermissionResolver permissionResolver,
                                     JdbcTemplate jdbcTemplate,
                                     WorkerCacheManager cacheManager) {
        this.permissionResolver = permissionResolver;
        this.jdbcTemplate = jdbcTemplate;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getPermissions(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);

        if (profileId == null || profileId.isBlank()) {
            log.warn("No profile ID in request headers — returning empty permissions");
            return ResponseEntity.ok(Map.of(
                    "systemPermissions", Map.of(),
                    "objectPermissions", Map.of(),
                    "fieldPermissions", Map.of()
            ));
        }

        // Check cache first
        Optional<Map<String, Object>> cached = cacheManager.getPermissions(profileId);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        Map<String, Boolean> systemPermissions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT permission_name, granted FROM profile_system_permission WHERE profile_id = ?",
                rs -> {
                    systemPermissions.put(rs.getString("permission_name"), rs.getBoolean("granted"));
                },
                profileId
        );

        Map<String, Map<String, Boolean>> objectPermissions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT c.name AS collection_name, pop.can_create, pop.can_read, pop.can_edit, pop.can_delete "
                        + "FROM profile_object_permission pop "
                        + "JOIN collection c ON c.id = pop.collection_id "
                        + "WHERE pop.profile_id = ? "
                        + "ORDER BY c.name",
                rs -> {
                    Map<String, Boolean> crud = new LinkedHashMap<>();
                    crud.put("canCreate", rs.getBoolean("can_create"));
                    crud.put("canRead", rs.getBoolean("can_read"));
                    crud.put("canEdit", rs.getBoolean("can_edit"));
                    crud.put("canDelete", rs.getBoolean("can_delete"));
                    objectPermissions.put(rs.getString("collection_name"), crud);
                },
                profileId
        );

        Map<String, Map<String, String>> fieldPermissions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT c.name AS collection_name, f.name AS field_name, pfp.visibility "
                        + "FROM profile_field_permission pfp "
                        + "JOIN field f ON f.id = pfp.field_id "
                        + "JOIN collection c ON c.id = pfp.collection_id "
                        + "WHERE pfp.profile_id = ? "
                        + "ORDER BY c.name, f.name",
                rs -> {
                    String collectionName = rs.getString("collection_name");
                    fieldPermissions.computeIfAbsent(collectionName, k -> new LinkedHashMap<>())
                            .put(rs.getString("field_name"), rs.getString("visibility"));
                },
                profileId
        );

        log.debug("Resolved {} system, {} object, {} field permissions for profile {}",
                systemPermissions.size(), objectPermissions.size(), fieldPermissions.size(), profileId);

        Map<String, Object> response = new HashMap<>();
        response.put("systemPermissions", systemPermissions);
        response.put("objectPermissions", objectPermissions);
        response.put("fieldPermissions", fieldPermissions);

        cacheManager.putPermissions(profileId, response);
        return ResponseEntity.ok(response);
    }
}
