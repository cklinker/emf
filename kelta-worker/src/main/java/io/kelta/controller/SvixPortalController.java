package io.kelta.worker.controller;

import com.svix.Svix;
import com.svix.models.AppPortalAccessIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Provides an embeddable Svix App Portal URL for the current tenant.
 *
 * <p>{@code GET /api/svix/portal} returns a short-lived magic link that can be
 * embedded in an iframe to give tenant admins a full webhook management dashboard
 * (endpoint configuration, delivery logs, retry controls).
 *
 * <p>The tenant ID is resolved from the {@code X-Tenant-ID} header set by the gateway.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/svix")
public class SvixPortalController {

    private static final Logger log = LoggerFactory.getLogger(SvixPortalController.class);

    private final Svix svix;

    public SvixPortalController(Svix svix) {
        this.svix = svix;
    }

    @GetMapping("/portal")
    public ResponseEntity<Map<String, String>> getPortalUrl(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("No tenant ID in request — cannot generate Svix portal URL");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }

        try {
            var accessIn = new AppPortalAccessIn();
            var accessOut = svix.getAuthentication().appPortalAccess(tenantId, accessIn);
            return ResponseEntity.ok(Map.of("url", accessOut.getUrl().toString()));
        } catch (Exception e) {
            log.error("Failed to generate Svix portal URL for tenant '{}': {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate webhook portal URL"));
        }
    }
}
