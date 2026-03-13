package io.kelta.worker.controller;

import io.kelta.worker.repository.BootstrapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * REST controller that exposes the current user's resolved permissions.
 *
 * <p>Unlike {@code /internal/permissions} (called by the gateway internally),
 * this endpoint is routed through the gateway and requires authentication.
 * The gateway's header transformation adds {@code X-User-Id} and
 * {@code X-Tenant-ID} headers from the authenticated JWT.
 *
 * <p>The permission resolution logic is identical to
 * {@link InternalBootstrapController#resolvePermissions}: profile permissions
 * combined with direct and group-inherited permission set permissions using
 * most-permissive-wins merging.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/me")
public class UserPermissionsController {

    private static final Logger log = LoggerFactory.getLogger(UserPermissionsController.class);

    private final BootstrapRepository repository;

    public UserPermissionsController(BootstrapRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the current user's resolved system permissions.
     *
     * <p>Reads identity from gateway-provided headers:
     * <ul>
     *   <li>{@code X-User-Id} — user's email address</li>
     *   <li>{@code X-Tenant-ID} — resolved tenant UUID</li>
     * </ul>
     *
     * @return system permissions map, or 401 if headers are missing
     */
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getMyPermissions(HttpServletRequest request) {
        String email = request.getHeader("X-User-Id");
        String tenantId = request.getHeader("X-Tenant-ID");

        if (email == null || email.isBlank() || tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        log.debug("Fetching permissions for current user email={} tenant={}", email, tenantId);

        // Find user
        Optional<Map<String, Object>> userOpt = repository.findActiveUserByEmail(email, tenantId);
        if (userOpt.isEmpty()) {
            log.warn("No active user found for email={} tenant={}", email, tenantId);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> userRow = userOpt.get();
        String userId = (String) userRow.get("id");
        String profileId = (String) userRow.get("profile_id");

        // Resolve system permissions
        Map<String, Boolean> systemPerms = new LinkedHashMap<>();

        // Load profile permissions
        if (profileId != null) {
            loadSystemPermissions(repository.findProfileSystemPermissions(profileId), systemPerms);
        }

        // Collect all applicable permission set IDs
        Set<String> permissionSetIds = new LinkedHashSet<>();

        // Direct user assignments
        for (Map<String, Object> row : repository.findUserPermissionSetIds(userId)) {
            String psId = (String) row.get("permission_set_id");
            if (psId != null) {
                permissionSetIds.add(psId);
            }
        }

        // Group-inherited assignments
        List<Map<String, Object>> groupRows = repository.findUserGroupIds(userId);
        if (!groupRows.isEmpty()) {
            List<String> groupIds = new ArrayList<>();
            for (Map<String, Object> row : groupRows) {
                String gId = (String) row.get("group_id");
                if (gId != null) {
                    groupIds.add(gId);
                }
            }
            if (!groupIds.isEmpty()) {
                for (Map<String, Object> row : repository.findGroupPermissionSetIds(groupIds)) {
                    String psId = (String) row.get("permission_set_id");
                    if (psId != null) {
                        permissionSetIds.add(psId);
                    }
                }
            }
        }

        // Merge permission set permissions (most-permissive-wins)
        for (String permSetId : permissionSetIds) {
            loadSystemPermissions(repository.findPermsetSystemPermissions(permSetId), systemPerms);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("systemPermissions", systemPerms);

        return ResponseEntity.ok(response);
    }

    private void loadSystemPermissions(List<Map<String, Object>> rows,
                                        Map<String, Boolean> systemPerms) {
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("permission_name");
            Boolean granted = (Boolean) row.get("granted");
            if (name != null && Boolean.TRUE.equals(granted)) {
                systemPerms.merge(name, true, (existing, newVal) -> existing || newVal);
            }
        }
    }
}
