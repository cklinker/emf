package io.kelta.worker.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves a tenant's slug from its ID, with an in-memory Caffeine cache.
 *
 * <p>Used by background paths (NATS event listeners, scheduled jobs) that
 * receive only a {@code tenantId} but need to bind the slug into
 * {@link io.kelta.runtime.context.TenantContext} so that schema-per-tenant
 * SQL resolves to the correct schema. HTTP request paths get the slug from
 * the request filter directly and do not need this resolver.
 *
 * <p>The {@code tenant} table is small and slugs are immutable for the
 * lifetime of a tenant, so a long TTL is safe. Negative lookups are NOT
 * cached — callers should treat unknown tenant IDs as unrecoverable.
 *
 * @since 1.0.0
 */
@Service
public class TenantSlugResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantSlugResolver.class);

    private static final Duration TTL = Duration.ofHours(1);
    private static final int MAX_SIZE = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, String> cache;

    public TenantSlugResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(TTL)
                .build();
    }

    /**
     * Returns the slug for the given tenant ID, or empty if the tenant is
     * unknown. Cached on first hit.
     */
    public Optional<String> resolveSlug(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        String cached = cache.getIfPresent(tenantId);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            String slug = jdbcTemplate.queryForObject(
                    "SELECT slug FROM tenant WHERE id = ?", String.class, tenantId);
            if (slug != null && !slug.isBlank()) {
                cache.put(tenantId, slug);
                return Optional.of(slug);
            }
            return Optional.empty();
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to resolve slug for tenant {}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Evicts a single tenant from the cache (used when slug changes). */
    public void evict(String tenantId) {
        if (tenantId != null) {
            cache.invalidate(tenantId);
        }
    }
}
