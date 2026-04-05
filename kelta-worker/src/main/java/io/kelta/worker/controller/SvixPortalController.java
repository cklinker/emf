package io.kelta.worker.controller;

import com.svix.Svix;
import com.svix.models.AppPortalAccessIn;
import io.kelta.worker.service.SvixTenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Provides Svix authentication credentials for the current tenant so the
 * frontend can render a custom webhook management UI using {@code svix-react}.
 *
 * <p>{@code GET /api/svix/portal} returns a short-lived token, the tenant's
 * Svix application ID, and the Svix server URL.
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
    private final SvixTenantService svixTenantService;
    private final String svixServerUrl;

    public SvixPortalController(Svix svix,
                                SvixTenantService svixTenantService,
                                @Value("${kelta.svix.public-url:${kelta.svix.server-url}}") String svixServerUrl) {
        this.svix = svix;
        this.svixTenantService = svixTenantService;
        this.svixServerUrl = svixServerUrl;
    }

    @GetMapping("/portal")
    public ResponseEntity<Map<String, String>> getPortalAccess(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("No tenant ID in request — cannot generate Svix portal access");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing tenant context"));
        }

        try {
            svixTenantService.ensureApplication(tenantId, tenantId);
            var accessIn = new AppPortalAccessIn();
            var accessOut = svix.getAuthentication().appPortalAccess(tenantId, accessIn);
            return ResponseEntity.ok(Map.of(
                    "token", accessOut.getToken(),
                    "appId", tenantId,
                    "serverUrl", svixServerUrl
            ));
        } catch (Exception e) {
            log.error("Failed to generate Svix portal access for tenant '{}': {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate webhook portal access"));
        }
    }
}
