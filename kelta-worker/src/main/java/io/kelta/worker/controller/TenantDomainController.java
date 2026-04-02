package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.RecordChangedPayload;
import io.kelta.runtime.events.RecordEventPublisher;
import io.kelta.worker.cache.WorkerCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin API for managing custom tenant domains.
 *
 * <p>Publishes {@code record.created} / {@code record.deleted} events to
 * {@code kelta.record.changed} for the {@code tenant_custom_domains}
 * collection, so all gateway pods can invalidate their domain caches.
 *
 * @since 1.0.0
 */
@RestController
public class TenantDomainController {

    private static final Logger log = LoggerFactory.getLogger(TenantDomainController.class);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.-]{0,253}[a-zA-Z0-9]$");

    /** Collection name used in record change events for domain CRUD. */
    static final String COLLECTION_NAME = "tenant_custom_domains";

    /** Platform domains that cannot be registered as custom domains. */
    private static final Set<String> RESERVED_DOMAIN_PATTERNS = Set.of(
            "kelta.io", "kelta.com", "rzware.com"
    );

    private final JdbcTemplate jdbcTemplate;
    private final WorkerCacheManager cacheManager;
    private final RecordEventPublisher recordEventPublisher;

    public TenantDomainController(JdbcTemplate jdbcTemplate, WorkerCacheManager cacheManager,
                                   RecordEventPublisher recordEventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.cacheManager = cacheManager;
        this.recordEventPublisher = recordEventPublisher;
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

        cacheManager.evictCustomDomain(domain);
        publishRecordEvent("record.created", tenantId, id, Map.of("domain", domain));
        log.info("Custom domain registered: {} → tenant {}", domain, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "domain", domain)));
    }

    @DeleteMapping("/api/admin/domains/{domainId}")
    public ResponseEntity<?> removeDomain(@PathVariable String domainId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));

        // Look up the domain name before deleting so we can evict the cache entry
        var domainRows = jdbcTemplate.queryForList(
                "SELECT domain FROM tenant_custom_domain WHERE id = ? AND tenant_id = ?", domainId, tenantId);

        int deleted = jdbcTemplate.update(
                "DELETE FROM tenant_custom_domain WHERE id = ? AND tenant_id = ?", domainId, tenantId);
        if (deleted == 0) {
            return ResponseEntity.notFound().build();
        }

        if (!domainRows.isEmpty()) {
            String domain = (String) domainRows.get(0).get("domain");
            cacheManager.evictCustomDomain(domain);
            publishRecordEvent("record.deleted", tenantId, domainId, Map.of("domain", domain));
        }

        log.info("Custom domain removed: id={} tenant={}", domainId, tenantId);
        return ResponseEntity.noContent().build();
    }

    // Internal endpoint for gateway domain resolution
    @GetMapping("/internal/domains/resolve")
    public ResponseEntity<String> resolveDomain(@RequestParam String domain) {
        // Check cache first (includes negative lookups)
        Optional<String> cached = cacheManager.getCustomDomain(domain);
        if (cached.isPresent()) {
            String value = cached.get();
            if (WorkerCacheManager.DOMAIN_NOT_FOUND.equals(value)) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(value);
        }

        var results = jdbcTemplate.queryForList(
                "SELECT t.slug FROM tenant_custom_domain tcd JOIN tenant t ON t.id = tcd.tenant_id WHERE tcd.domain = ?",
                domain);
        if (results.isEmpty()) {
            // Cache the negative result to avoid repeated DB queries
            cacheManager.putCustomDomainNotFound(domain);
            return ResponseEntity.notFound().build();
        }

        String slug = (String) results.get(0).get("slug");
        cacheManager.putCustomDomain(domain, slug);
        return ResponseEntity.ok(slug);
    }

    /**
     * Publishes a standard record change event for the {@code tenant_custom_domains}
     * collection. The gateway's {@code SystemCollectionRouteListener} handles this
     * event to invalidate its custom domain cache.
     */
    private void publishRecordEvent(String eventType, String tenantId, String recordId,
                                     Map<String, Object> data) {
        try {
            RecordChangedPayload payload;
            if (eventType.contains("created")) {
                payload = RecordChangedPayload.created(COLLECTION_NAME, recordId, data);
            } else {
                payload = RecordChangedPayload.deleted(COLLECTION_NAME, recordId, data);
            }

            PlatformEvent<RecordChangedPayload> event = EventFactory.createRecordEvent(
                    eventType, tenantId, null, payload);

            recordEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish {} event for {} (tenant {}): {}",
                    eventType, COLLECTION_NAME, tenantId, e.getMessage());
        }
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
