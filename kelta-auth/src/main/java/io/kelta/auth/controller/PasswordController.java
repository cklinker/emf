package io.kelta.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/password")
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PasswordController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
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

        // Verify current password
        String currentHash = jdbcTemplate.queryForObject(
                "SELECT uc.password_hash FROM user_credential uc " +
                        "JOIN platform_user pu ON pu.id = uc.user_id " +
                        "WHERE pu.email = ?",
                String.class, email
        );

        if (currentHash == null || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }

        // Update password
        String newHash = passwordEncoder.encode(request.newPassword());
        jdbcTemplate.update(
                "UPDATE user_credential SET password_hash = ?, password_changed_at = ?, " +
                        "force_change_on_login = false, updated_at = ? " +
                        "WHERE user_id = (SELECT id FROM platform_user WHERE email = ?)",
                newHash, Instant.now(), Instant.now(), email
        );

        log.info("Password changed for user {}", email);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/reset-request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody ResetRequestBody request) {

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
            // TODO: Send reset email via worker's email service
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
        Instant expiresAt = ((java.sql.Timestamp) row.get("reset_token_expires_at")).toInstant();
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
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
