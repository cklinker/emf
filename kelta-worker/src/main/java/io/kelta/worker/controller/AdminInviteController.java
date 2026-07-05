package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.UserInviteService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Admin endpoint to re-send (or first-send) the {@code user.invite} email for a
 * specific user. Useful when the original invite expired or never arrived.
 *
 * <p>Requires {@code MANAGE_USERS} — {@code /api/admin/**} is a static gateway route with only
 * the blanket API_ACCESS check, so the permission is enforced here. Delegated admins re-invite
 * through {@code POST /api/admin/delegated/users/{id}/invite}, which enforces their scope.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminInviteController {

    private static final Logger log = LoggerFactory.getLogger(AdminInviteController.class);
    private static final String PERMISSION = "MANAGE_USERS";

    private final UserInviteService userInviteService;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public AdminInviteController(UserInviteService userInviteService,
                                 CerbosPermissionResolver permissionResolver,
                                 BootstrapRepository bootstrapRepository) {
        this.userInviteService = userInviteService;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    @PostMapping("/{userId}/invite")
    public ResponseEntity<Map<String, String>> invite(HttpServletRequest request,
                                                      @PathVariable String userId) {
        requirePermission(request);
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tenant context required"));
        }
        String token = userInviteService.inviteUser(tenantId, userId);
        if (token == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        log.info("Admin re-invite issued for user {} in tenant {}", userId, tenantId);
        return ResponseEntity.ok(Map.of("status", "QUEUED"));
    }

    private void requirePermission(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        boolean granted = bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> PERMISSION.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
        if (!granted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, PERMISSION + " permission required");
        }
    }
}
