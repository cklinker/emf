package io.kelta.worker.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Centralized Caffeine cache manager for the worker service.
 *
 * <p>Manages in-memory caches for frequently-queried, slowly-changing data
 * to reduce database load. Mirrors the gateway's {@code GatewayCacheManager}
 * pattern with per-cache TTL, max size, and Micrometer metrics.
 *
 * <h3>Caches</h3>
 * <ul>
 *   <li><strong>Custom domain cache</strong> — maps custom domains to tenant slugs
 *       (called by gateway on every request via {@code /internal/domains/resolve})</li>
 *   <li><strong>User permissions cache</strong> — maps profileId to full permission set
 *       (called by UI on every page navigation via {@code /api/me/permissions})</li>
 *   <li><strong>Tenant limits cache</strong> — maps tenantId to parsed governor limits
 *       (called by governor-limits page and gateway periodic refresh)</li>
 * </ul>
 *
 * <h3>Invalidation</h3>
 * <p>All caches use time-based expiration as a safety net. Primary invalidation
 * is explicit: callers invoke {@code evict*} methods when the underlying data
 * changes (domain CRUD, profile permission sync, governor limit updates).
 *
 * @since 1.0.0
 */
@Component
public class WorkerCacheManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerCacheManager.class);

    private static final Duration CUSTOM_DOMAIN_TTL = Duration.ofMinutes(10);
    private static final int CUSTOM_DOMAIN_MAX_SIZE = 1_000;

    private static final Duration PERMISSIONS_TTL = Duration.ofMinutes(5);
    private static final int PERMISSIONS_MAX_SIZE = 5_000;

    private static final Duration TENANT_LIMITS_TTL = Duration.ofMinutes(10);
    private static final int TENANT_LIMITS_MAX_SIZE = 1_000;

    private static final Duration SYSTEM_COLLECTION_TTL = Duration.ofMinutes(10);
    private static final int SYSTEM_COLLECTION_MAX_SIZE = 10_000;

    private final Cache<String, String> customDomainCache;
    private final Cache<String, Map<String, Object>> permissionsCache;
    private final Cache<String, Map<String, Object>> tenantLimitsCache;
    private final Cache<String, Map<String, Object>> systemCollectionCache;

    public WorkerCacheManager(MeterRegistry meterRegistry) {
        this.customDomainCache = Caffeine.newBuilder()
                .maximumSize(CUSTOM_DOMAIN_MAX_SIZE)
                .expireAfterWrite(CUSTOM_DOMAIN_TTL)
                .recordStats()
                .build();

        this.permissionsCache = Caffeine.newBuilder()
                .maximumSize(PERMISSIONS_MAX_SIZE)
                .expireAfterWrite(PERMISSIONS_TTL)
                .recordStats()
                .build();

        this.tenantLimitsCache = Caffeine.newBuilder()
                .maximumSize(TENANT_LIMITS_MAX_SIZE)
                .expireAfterWrite(TENANT_LIMITS_TTL)
                .recordStats()
                .build();

        this.systemCollectionCache = Caffeine.newBuilder()
                .maximumSize(SYSTEM_COLLECTION_MAX_SIZE)
                .expireAfterWrite(SYSTEM_COLLECTION_TTL)
                .recordStats()
                .build();

        meterRegistry.gauge("worker.cache.size.custom-domain", customDomainCache, Cache::estimatedSize);
        meterRegistry.gauge("worker.cache.size.permissions", permissionsCache, Cache::estimatedSize);
        meterRegistry.gauge("worker.cache.size.tenant-limits", tenantLimitsCache, Cache::estimatedSize);
        meterRegistry.gauge("worker.cache.size.system-collection", systemCollectionCache, Cache::estimatedSize);
    }

    // ── Custom Domain Cache ──────────────────────────────────────────────

    /**
     * Sentinel value indicating a domain was looked up and not found in the database.
     * Cached to avoid repeated DB queries for domains that don't resolve to any tenant.
     */
    public static final String DOMAIN_NOT_FOUND = "__NOT_FOUND__";

    /**
     * Returns the cached result for a custom domain lookup.
     *
     * <p>Returns:
     * <ul>
     *   <li>{@code Optional.empty()} — domain is not in the cache (cache miss)</li>
     *   <li>{@code Optional.of(DOMAIN_NOT_FOUND)} — domain was previously looked up and not found</li>
     *   <li>{@code Optional.of(slug)} — domain resolves to the given tenant slug</li>
     * </ul>
     */
    public Optional<String> getCustomDomain(String domain) {
        return Optional.ofNullable(customDomainCache.getIfPresent(domain));
    }

    /**
     * Caches a custom domain → tenant slug mapping.
     */
    public void putCustomDomain(String domain, String tenantSlug) {
        customDomainCache.put(domain, tenantSlug);
    }

    /**
     * Caches a negative lookup — the domain does not resolve to any tenant.
     */
    public void putCustomDomainNotFound(String domain) {
        customDomainCache.put(domain, DOMAIN_NOT_FOUND);
    }

    /**
     * Evicts a custom domain from the cache.
     */
    public void evictCustomDomain(String domain) {
        customDomainCache.invalidate(domain);
        log.debug("Evicted custom domain cache entry: {}", domain);
    }

    /**
     * Evicts all custom domain entries for a tenant slug.
     */
    public void evictAllCustomDomains() {
        customDomainCache.invalidateAll();
        log.debug("Evicted all custom domain cache entries");
    }

    // ── User Permissions Cache ───────────────────────────────────────────

    /**
     * Returns the cached permissions for a profile, if present.
     *
     * <p>The returned map contains keys: {@code systemPermissions},
     * {@code objectPermissions}, {@code fieldPermissions}.
     */
    public Optional<Map<String, Object>> getPermissions(String profileId) {
        return Optional.ofNullable(permissionsCache.getIfPresent(profileId));
    }

    /**
     * Caches a profile's full permission set.
     */
    public void putPermissions(String profileId, Map<String, Object> permissions) {
        permissionsCache.put(profileId, permissions);
    }

    /**
     * Evicts a profile's permissions from the cache.
     */
    public void evictPermissions(String profileId) {
        permissionsCache.invalidate(profileId);
        log.debug("Evicted permissions cache entry for profile: {}", profileId);
    }

    /**
     * Evicts all cached permissions (e.g., after bulk policy sync).
     */
    public void evictAllPermissions() {
        permissionsCache.invalidateAll();
        log.debug("Evicted all permissions cache entries");
    }

    // ── Tenant Limits Cache ──────────────────────────────────────────────

    /**
     * Returns the cached governor limits for a tenant, if present.
     */
    public Optional<Map<String, Object>> getTenantLimits(String tenantId) {
        return Optional.ofNullable(tenantLimitsCache.getIfPresent(tenantId));
    }

    /**
     * Caches a tenant's parsed governor limits.
     */
    public void putTenantLimits(String tenantId, Map<String, Object> limits) {
        tenantLimitsCache.put(tenantId, limits);
    }

    /**
     * Evicts a tenant's limits from the cache.
     */
    public void evictTenantLimits(String tenantId) {
        tenantLimitsCache.invalidate(tenantId);
        log.debug("Evicted tenant limits cache entry: {}", tenantId);
    }

    // ── System Collection Cache ────────────────────────────────────────────

    /**
     * Returns a cached system collection query response.
     *
     * @param cacheKey the composite key (tenantId:collectionName:queryHash)
     * @return the cached response if present, empty otherwise
     */
    public Optional<Map<String, Object>> getSystemCollectionResponse(String cacheKey) {
        return Optional.ofNullable(systemCollectionCache.getIfPresent(cacheKey));
    }

    /**
     * Caches a system collection query response.
     *
     * @param cacheKey the composite key
     * @param response the JSON:API response to cache
     */
    public void putSystemCollectionResponse(String cacheKey, Map<String, Object> response) {
        systemCollectionCache.put(cacheKey, response);
    }

    /**
     * Evicts all cached entries for a specific system collection within a tenant.
     *
     * <p>Since cache keys are composite (tenantId:collectionName:queryHash), this
     * method iterates over all keys and removes those matching the given prefix.
     *
     * @param tenantId       the tenant ID (may be null)
     * @param collectionName the collection name
     */
    public void evictSystemCollection(String tenantId, String collectionName) {
        String prefix = (tenantId != null ? tenantId : "_") + ":" + collectionName + ":";
        systemCollectionCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Evicted system collection cache entries for: {} (tenant={})", collectionName, tenantId);
    }

    /**
     * Evicts all cached system collection entries across all tenants and collections.
     */
    public void evictAllSystemCollections() {
        systemCollectionCache.invalidateAll();
        log.debug("Evicted all system collection cache entries");
    }

    // ── Metrics / Diagnostics ────────────────────────────────────────────

    /**
     * Returns estimated sizes for diagnostic logging.
     */
    public String getCacheSummary() {
        return String.format("WorkerCaches[domains=%d, permissions=%d, limits=%d, sysCollections=%d]",
                customDomainCache.estimatedSize(),
                permissionsCache.estimatedSize(),
                tenantLimitsCache.estimatedSize(),
                systemCollectionCache.estimatedSize());
    }
}
