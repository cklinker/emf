package io.kelta.auth.controller;

import io.kelta.auth.config.AuthProperties;
import io.kelta.auth.service.PasswordPolicyService;
import io.kelta.auth.service.WorkerClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth/password")
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final WorkerClient workerClient;
    private final PasswordPolicyService policyService;
    private final String uiBaseUrl;

    public PasswordController(JdbcTemplate jdbcTemplate,
                              PasswordEncoder passwordEncoder,
                              WorkerClient workerClient,
                              PasswordPolicyService policyService,
                              AuthProperties authProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.workerClient = workerClient;
        this.policyService = policyService;
        this.uiBaseUrl = authProperties.getUiBaseUrl();
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    public record ResetRequestBody(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    @PostMapping("/change")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String email = authentication.getName();

        // Verify current password. If no user/credential row matches the
        // authenticated principal — shouldn't happen in normal flows but can if
        // the account was deleted mid-session — return the same generic error
        // as a wrong password rather than bubbling an unchecked 500.
        String currentHash;
        try {
            currentHash = jdbcTemplate.queryForObject(
                    "SELECT uc.password_hash FROM user_credential uc " +
                            "JOIN platform_user pu ON pu.id = uc.user_id " +
                            "WHERE pu.email = ?",
                    String.class, email
            );
        } catch (EmptyResultDataAccessException e) {
            log.warn("Password change attempted for unknown email: {}", email);
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        if (currentHash == null || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        // Look up user details for policy validation
        var userInfo = jdbcTemplate.queryForList(
                "SELECT pu.id, pu.tenant_id, pu.first_name, pu.last_name FROM platform_user pu WHERE pu.email = ?",
                email
        );
        String userId = userInfo.isEmpty() ? null : (String) userInfo.get(0).get("id");
        String tenantId = userInfo.isEmpty() ? null : (String) userInfo.get(0).get("tenant_id");
        String displayName = userInfo.isEmpty() ? "" :
                ((String) userInfo.get(0).getOrDefault("first_name", "")) + " " +
                ((String) userInfo.get(0).getOrDefault("last_name", ""));

        // Validate against password policy
        List<String> violations = policyService.validatePassword(request.newPassword(), email, displayName.trim(), tenantId);
        if (!violations.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", String.join("; ", violations)));
        }

        // Check password history
        if (userId != null) {
            Optional<String> historyViolation = policyService.checkHistory(userId, request.newPassword(), tenantId);
            if (historyViolation.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", historyViolation.get()));
            }
        }

        // Update password
        String newHash = passwordEncoder.encode(request.newPassword());
        jdbcTemplate.update(
                "UPDATE user_credential SET password_hash = ?, password_changed_at = ?, " +
                        "force_change_on_login = false, updated_at = ? " +
                        "WHERE user_id = (SELECT id FROM platform_user WHERE email = ?)",
                newHash, Instant.now(), Instant.now(), email
        );

        // Save to password history
        if (userId != null) {
            policyService.saveToHistory(userId, newHash, tenantId);
        }

        log.info("Password changed for user {}", email);
        if (userId != null) sendPasswordChangedEmail(userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/reset-request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody ResetRequestBody request) {

        // Invalidate any existing reset token before generating a new one
        jdbcTemplate.update(
                "UPDATE user_credential SET reset_token = NULL, reset_token_expires_at = NULL " +
                        "WHERE user_id = (SELECT id FROM platform_user WHERE email = ? AND status = 'ACTIVE')",
                request.email()
        );

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(3600); // 1 hour

        int updated = jdbcTemplate.update(
                "UPDATE user_credential SET reset_token = ?, reset_token_expires_at = ?, updated_at = ? " +
                        "WHERE user_id = (SELECT id FROM platform_user WHERE email = ? AND status = 'ACTIVE')",
                resetToken, expiresAt, Instant.now(), request.email()
        );

        // Always return success to prevent email enumeration
        if (updated > 0) {
            log.info("Password reset requested for {}", request.email());
            sendPasswordResetEmail(request.email(), resetToken);
        }

        return ResponseEntity.ok(Map.of("status", "ok",
                "message", "If an account exists with that email, a reset link has been sent."));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        // Validate reset token
        var results = jdbcTemplate.queryForList(
                "SELECT uc.user_id, uc.reset_token_expires_at FROM user_credential uc " +
                        "WHERE uc.reset_token = ?",
                request.token()
        );

        if (results.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset token"));
        }

        var row = results.get(0);
        // Defensive: a row with a reset_token but no expires_at is an invariant
        // violation. Treat it as invalid rather than NPE on the .toInstant() cast.
        java.sql.Timestamp expiresTs = (java.sql.Timestamp) row.get("reset_token_expires_at");
        if (expiresTs == null) {
            log.warn("Reset token row has null expires_at — treating as invalid (user_id={})",
                    row.get("user_id"));
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset token"));
        }
        Instant expiresAt = expiresTs.toInstant();
        if (Instant.now().isAfter(expiresAt)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset token"));
        }

        // Update password and clear reset token
        String userId = (String) row.get("user_id");
        String newHash = passwordEncoder.encode(request.newPassword());

        jdbcTemplate.update(
                "UPDATE user_credential SET password_hash = ?, password_changed_at = ?, " +
                        "reset_token = NULL, reset_token_expires_at = NULL, " +
                        "force_change_on_login = false, failed_attempts = 0, locked_until = NULL, " +
                        "updated_at = ? WHERE user_id = ?",
                newHash, Instant.now(), Instant.now(), userId
        );

        log.info("Password reset completed for user_id {}", userId);
        sendPasswordChangedEmail(userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void sendPasswordResetEmail(String email, String resetToken) {
        try {
            var rows = jdbcTemplate.queryForList(
                    "SELECT pu.tenant_id, pu.first_name, t.name AS tenant_name "
                            + "FROM platform_user pu JOIN tenant t ON t.id = pu.tenant_id "
                            + "WHERE pu.email = ? AND pu.status = 'ACTIVE'",
                    email
            );
            if (rows.isEmpty()) return;
            String tenantId   = (String) rows.get(0).get("tenant_id");
            String firstName  = (String) rows.getFirst().getOrDefault("first_name", "");
            String tenantName = (String) rows.getFirst().getOrDefault("tenant_name", "Kelta");

            String actionUrl = uiBaseUrl + "/reset-password?token=" + resetToken + "&email=" + email;
            workerClient.sendTemplateEmail(tenantId, email, "user.password_reset",
                    Map.of(
                            "tenantName", tenantName,
                            "firstName", firstName == null ? "" : firstName,
                            "actionUrl", actionUrl,
                            "expiresIn", "1 hour"),
                    "PASSWORD_RESET", null);
            log.info("Password reset email queued for {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
        }
    }

    private void sendPasswordChangedEmail(String userId) {
        try {
            var rows = jdbcTemplate.queryForList(
                    "SELECT pu.email, pu.first_name, pu.tenant_id, t.name AS tenant_name "
                            + "FROM platform_user pu JOIN tenant t ON t.id = pu.tenant_id "
                            + "WHERE pu.id = ?",
                    userId
            );
            if (rows.isEmpty()) return;
            var row = rows.get(0);
            workerClient.sendTemplateEmail(
                    (String) row.get("tenant_id"),
                    (String) row.get("email"),
                    "user.password_changed",
                    Map.of(
                            "tenantName", row.getOrDefault("tenant_name", "Kelta"),
                            "firstName",  row.getOrDefault("first_name", ""),
                            "changedAt",  Instant.now().toString(),
                            "ipAddress",  "unknown"),
                    "PASSWORD_CHANGED", userId);
        } catch (Exception e) {
            log.warn("Failed to send password-changed notification: {}", e.getMessage());
        }
    }
}
