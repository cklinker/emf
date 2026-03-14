package io.kelta.worker.controller;

import io.kelta.worker.service.CerbosPermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the current user's system-level permissions.
 *
 * <p>{@code GET /api/me/permissions} returns a JSON object with a
 * {@code systemPermissions} map (permission name → boolean). The UI's
 * {@code useSystemPermissions} hook calls this endpoint to conditionally
 * render pages based on the user's profile permissions.
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

    public UserPermissionsController(CerbosPermissionResolver permissionResolver,
                                     JdbcTemplate jdbcTemplate) {
        this.permissionResolver = permissionResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getPermissions(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);

        if (profileId == null || profileId.isBlank()) {
            log.warn("No profile ID in request headers — returning empty permissions");
            return ResponseEntity.ok(Map.of("systemPermissions", Map.of()));
        }

        Map<String, Boolean> systemPermissions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT permission_name, granted FROM profile_system_permission WHERE profile_id = ?",
                rs -> {
                    systemPermissions.put(rs.getString("permission_name"), rs.getBoolean("granted"));
                },
                profileId
        );

        log.debug("Resolved {} system permissions for profile {}", systemPermissions.size(), profileId);

        return ResponseEntity.ok(Map.of("systemPermissions", systemPermissions));
    }
}
