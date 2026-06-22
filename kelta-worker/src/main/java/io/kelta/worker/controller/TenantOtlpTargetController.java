package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.observability.DbTenantOtlpRegistry;
import io.kelta.worker.observability.TenantOtlpTargetRepository;
import io.kelta.worker.observability.TenantOtlpTargetRepository.StoredTarget;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Admin endpoint for a tenant's OTLP trace-export target (Rec 7). Lives under
 * {@code /api/admin/**} (already gateway-routed) and is gated on the {@code VIEW_SETUP}
 * system permission. Writes update {@code tenant_otlp_target} and invalidate the
 * registry cache so the change takes effect (other pods refresh within the TTL).
 */
@RestController
@RequestMapping("/api/admin/observability/otlp-target")
public class TenantOtlpTargetController {

    private static final String SETUP_PERMISSION = "VIEW_SETUP";

    private final TenantOtlpTargetRepository repository;
    private final DbTenantOtlpRegistry registry;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;

    public TenantOtlpTargetController(TenantOtlpTargetRepository repository,
                                      DbTenantOtlpRegistry registry,
                                      CerbosPermissionResolver permissionResolver,
                                      BootstrapRepository bootstrapRepository) {
        this.repository = repository;
        this.registry = registry;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(HttpServletRequest request) {
        requireSetupPermission(request);
        Optional<StoredTarget> target = repository.find(tenantId());
        if (target.isEmpty()) {
            return ResponseEntity.ok(Map.of("enabled", false));
        }
        return ResponseEntity.ok(toResponse(target.get()));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> put(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        requireSetupPermission(request);
        String endpoint = asString(body.get("endpoint"));
        if (endpoint == null || endpoint.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endpoint is required");
        }
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));
        Map<String, String> headers = asStringMap(body.get("headers"));

        String tenantId = tenantId();
        repository.upsert(tenantId, endpoint, headers, enabled);
        registry.invalidate(tenantId);
        return ResponseEntity.ok(toResponse(new StoredTarget(endpoint, headers, enabled)));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(HttpServletRequest request) {
        requireSetupPermission(request);
        String tenantId = tenantId();
        repository.delete(tenantId);
        registry.invalidate(tenantId);
        return ResponseEntity.noContent().build();
    }

    // --- helpers ------------------------------------------------------------

    private String tenantId() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    private void requireSetupPermission(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        boolean granted = bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> SETUP_PERMISSION.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
        if (!granted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SETUP_PERMISSION + " permission required");
        }
    }

    private static Map<String, Object> toResponse(StoredTarget target) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("endpoint", target.endpoint());
        out.put("headers", target.headers());
        out.put("enabled", target.enabled());
        return out;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        ((Map<String, Object>) map).forEach((k, v) -> {
            if (v != null) {
                result.put(k, v.toString());
            }
        });
        return result;
    }
}
