package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Echoes the tenant context the gateway resolved for the current request.
 *
 * <p>The frontend uses this on custom-domain boot to learn the tenant slug
 * without having to parse it from the URL path. The gateway has already
 * injected {@code X-Tenant-Id} / {@code X-Tenant-Slug} headers by the time the
 * request reaches the worker; we simply return them.
 */
@RestController
public class WhoAmIController {

    @GetMapping("/api/whoami")
    public ResponseEntity<Map<String, Object>> whoAmI(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", firstNonBlank(TenantContext.get(), request.getHeader("X-Tenant-Id")));
        body.put("tenantSlug", request.getHeader("X-Tenant-Slug"));
        body.put("userId", request.getHeader("X-User-Id"));
        body.put("username", request.getHeader("X-Forwarded-User"));
        return ResponseEntity.ok(body);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
