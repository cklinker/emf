package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin API for managing custom tenant domains.
 *
 * @since 1.0.0
 */
@RestController
public class TenantDomainController {

    private static final Logger log = LoggerFactory.getLogger(TenantDomainController.class);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.-]{0,253}[a-zA-Z0-9]$");

    /** Platform domains that cannot be registered as custom domains. */
    private static final Set<String> RESERVED_DOMAIN_PATTERNS = Set.of(
            "kelta.io", "kelta.com", "rzware.com"
    );

    private final JdbcTemplate jdbcTemplate;

    public TenantDomainController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/admin/domains")
    public ResponseEntity<?> listDomains() {
        String tenantId = TenantContext.get();
        if (tenantId == null) return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));

        var domains = jdbcTemplate.queryForList(
                "SELECT id, domain, verified, created_at FROM tenant_custom_domain WHERE tenant_id = ? ORDER BY created_at DESC",
                tenantId);
        return ResponseEntity.ok(Map.of("data", domains));
    }

    @PostMapping("/api/admin/domains")
    public ResponseEntity<?> registerDomain(@RequestBody Map<String, String> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));

        String domain = body.get("domain");
        if (domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "domain is required"));
        }

        domain = domain.toLowerCase().trim();

        // Validate format
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid domain format"));
        }

        // Check reserved domains
        if (isReservedDomain(domain)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reserved domain — cannot register platform domains"));
        }

        // Check uniqueness
        var existing = jdbcTemplate.queryForList(
                "SELECT id FROM tenant_custom_domain WHERE domain = ?", domain);
        if (!existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Domain already registered"));
        }

        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO tenant_custom_domain (id, tenant_id, domain, created_at) VALUES (?, ?, ?, NOW())",
                id, tenantId, domain);

        log.info("Custom domain registered: {} → tenant {}", domain, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "domain", domain)));
    }

    @DeleteMapping("/api/admin/domains/{domainId}")
    public ResponseEntity<?> removeDomain(@PathVariable String domainId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));

        int deleted = jdbcTemplate.update(
                "DELETE FROM tenant_custom_domain WHERE id = ? AND tenant_id = ?", domainId, tenantId);
        if (deleted == 0) {
            return ResponseEntity.notFound().build();
        }

        log.info("Custom domain removed: id={} tenant={}", domainId, tenantId);
        return ResponseEntity.noContent().build();
    }

    // Internal endpoint for gateway domain resolution
    @GetMapping("/internal/domains/resolve")
    public ResponseEntity<String> resolveDomain(@RequestParam String domain) {
        var results = jdbcTemplate.queryForList(
                "SELECT t.slug FROM tenant_custom_domain tcd JOIN tenant t ON t.id = tcd.tenant_id WHERE tcd.domain = ?",
                domain);
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok((String) results.get(0).get("slug"));
    }

    private boolean isReservedDomain(String domain) {
        for (String reserved : RESERVED_DOMAIN_PATTERNS) {
            if (domain.equals(reserved) || domain.endsWith("." + reserved)) {
                return true;
            }
        }
        return false;
    }
}
