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
import org.springframework.web.bind.annotation.*;

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
    private final String internalToken;

    public InternalEmailController(
            EmailService emailService,
            @Value("${kelta.internal.token:}") String internalToken) {
        this.emailService = emailService;
        this.internalToken = internalToken;
    }

    public record SendEmailRequest(
            @NotBlank String tenantId,
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String body,
            String source,
            String sourceId
    ) {}

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendEmail(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SendEmailRequest request) {

        // Validate internal token
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
}
