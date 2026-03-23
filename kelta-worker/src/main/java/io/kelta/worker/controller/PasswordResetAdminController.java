package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * Admin API for password management: force password reset on next login.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/users")
public class PasswordResetAdminController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetAdminController.class);

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Force a user to change their password on next login.
     * Sets the force_change_on_login flag to true on the user_credential table.
     */
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String userId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        // Verify user belongs to tenant
        var users = jdbcTemplate.queryForList(
                "SELECT id, email FROM platform_user WHERE id = ? AND tenant_id = ?",
                userId, tenantId);
        if (users.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Set force_change_on_login flag
        int updated = jdbcTemplate.update(
                "UPDATE user_credential SET force_change_on_login = true, updated_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now()), userId);

        if (updated == 0) {
            log.warn("No user_credential row found for user {} in tenant {}", userId, tenantId);
            return ResponseEntity.status(404).body(Map.of(
                    "error", "No credential record found for this user"));
        }

        SecurityAuditLogger.log(SecurityAuditLogger.EventType.PASSWORD_RESET_ADMIN,
                "admin", userId, tenantId, "success", "force_change_on_login=true");
        log.info("Password reset initiated for user {} by admin in tenant {}", userId, tenantId);

        return ResponseEntity.ok(Map.of("status", "reset_initiated", "userId", userId));
    }
}
