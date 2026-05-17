package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.UserInviteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoint to re-send (or first-send) the {@code user.invite} email for a
 * specific user. Useful when the original invite expired or never arrived.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminInviteController {

    private static final Logger log = LoggerFactory.getLogger(AdminInviteController.class);

    private final UserInviteService userInviteService;

    public AdminInviteController(UserInviteService userInviteService) {
        this.userInviteService = userInviteService;
    }

    @PostMapping("/{userId}/invite")
    public ResponseEntity<Map<String, String>> invite(@PathVariable String userId) {
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
}
