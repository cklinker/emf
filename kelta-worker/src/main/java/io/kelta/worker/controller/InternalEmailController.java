package io.kelta.worker.controller;

import io.kelta.runtime.module.integration.spi.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoint for cross-service email delivery (e.g., auth → worker).
 *
 * <p>Secured with a shared internal token ({@code X-Internal-Token} header).
 * This endpoint must NOT be routable through the gateway — it is for
 * service-to-service calls only within the Kubernetes cluster.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/internal/email")
public class InternalEmailController {

    private static final Logger log = LoggerFactory.getLogger(InternalEmailController.class);

    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;
    private final String internalToken;
    private final String externalBaseUrl;

    public InternalEmailController(
            EmailService emailService,
            JdbcTemplate jdbcTemplate,
            @Value("${kelta.internal.token:}") String internalToken,
            @Value("${kelta.external-base-url:}") String externalBaseUrl) {
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
        this.internalToken = internalToken;
        this.externalBaseUrl = externalBaseUrl == null ? "" : externalBaseUrl;
    }

    public record SendEmailRequest(
            @NotBlank String tenantId,
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String body,
            String source,
            String sourceId
    ) {}

    public record SendTemplateRequest(
            @NotBlank String tenantId,
            @NotBlank @Email String to,
            @NotBlank String templateKey,
            Map<String, Object> vars,
            String source,
            String sourceId
    ) {}

    public record SendInviteRequest(
            @NotBlank @Email String email,
            @NotBlank String tenantId,
            @NotBlank String inviteToken
    ) {}

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendEmail(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SendEmailRequest request) {

        ResponseEntity<Map<String, String>> denial = checkInternalToken(token);
        if (denial != null) return denial;

        String emailLogId = emailService.queueEmail(
                request.tenantId(),
                request.to(),
                request.subject(),
                request.body(),
                request.source(),
                request.sourceId()
        );

        return ResponseEntity.ok(Map.of(
                "emailLogId", emailLogId,
                "status", "QUEUED"
        ));
    }

    @PostMapping("/send-template")
    public ResponseEntity<Map<String, String>> sendTemplate(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SendTemplateRequest request) {

        ResponseEntity<Map<String, String>> denial = checkInternalToken(token);
        if (denial != null) return denial;

        return emailService.sendByKey(
                request.tenantId(), request.to(), request.templateKey(),
                request.vars(), request.source(), request.sourceId())
                .map(id -> ResponseEntity.ok(Map.of("emailLogId", id, "status", "QUEUED")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Template not found: " + request.templateKey())));
    }

    /**
     * Sends a user-invitation email using the {@code user_invite} system template.
     *
     * <p>Resolves the template via {@link io.kelta.worker.repository.EmailRepository#findTemplateByName}
     * (tenant override → {@code system} default seeded in V141), substitutes
     * {@code ${inviteLink}} and {@code ${tenantName}}, and queues delivery through
     * {@link EmailService}. Tenant SMTP credentials apply when configured; otherwise
     * the platform default SMTP is used.
     */
    @PostMapping("/invite")
    public ResponseEntity<Map<String, String>> sendInvite(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SendInviteRequest request) {

        ResponseEntity<Map<String, String>> denial = checkInternalToken(token);
        if (denial != null) return denial;

        String tenantName = lookupTenantName(request.tenantId());
        String inviteLink = buildInviteLink(request.inviteToken(), request.email());
        Map<String, Object> vars = Map.of(
                "inviteLink", inviteLink,
                "tenantName", tenantName);

        return emailService.sendByName(
                request.tenantId(), request.email(), "user_invite",
                vars, "USER_INVITE", request.inviteToken())
                .map(id -> ResponseEntity.ok(Map.of("emailLogId", id, "status", "QUEUED")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Template not found: user_invite")));
    }

    private String lookupTenantName(String tenantId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT name FROM tenant WHERE id = ?", tenantId);
            if (!rows.isEmpty()) {
                Object name = rows.get(0).get("name");
                if (name instanceof String s && !s.isBlank()) return s;
            }
        } catch (Exception e) {
            log.warn("Failed to look up tenant name for {}: {}", tenantId, e.getMessage());
        }
        return "Kelta";
    }

    private String buildInviteLink(String inviteToken, String email) {
        String base = externalBaseUrl == null ? "" : externalBaseUrl;
        return base + "/accept-invite?token="
                + URLEncoder.encode(inviteToken, StandardCharsets.UTF_8)
                + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }

    private ResponseEntity<Map<String, String>> checkInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("Internal email endpoint called but kelta.internal.token is not configured");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Internal token not configured"));
        }
        if (!internalToken.equals(token)) {
            log.warn("Internal email endpoint called with invalid token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid internal token"));
        }
        return null;
    }
}
