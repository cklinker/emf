package io.kelta.gateway.cache;

import io.kelta.gateway.config.GovernorLimitConfig;
import io.kelta.gateway.route.RateLimitConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Centralized cache manager for the gateway, backed by Caffeine caches.
 *
 * <p>Replaces the previous {@code TenantSlugCache} and {@code TenantGovernorLimitCache}
 * with a unified caching strategy using Caffeine's high-performance caches with
 * time-based expiration, maximum size bounds, and statistics recording.
 *
 * <p>Manages two caches:
 * <ul>
 *   <li><strong>Tenant slug cache</strong> — maps tenant slugs to tenant IDs.
 *       Populated on startup and refreshed periodically from the worker's
 *       {@code /internal/tenants/slug-map} endpoint. Expires after 10 minutes.</li>
 *   <li><strong>Governor limit cache</strong> — maps tenant IDs to daily API call limits.
 *       Populated from bootstrap configuration on startup and updated via NATS when
 *       tenant governor limits change. No time-based expiration — entries persist until
 *       explicitly invalidated.</li>
 * </ul>
 */
@Component
public class GatewayCacheManager {

    private static final Logger log = LoggerFactory.getLogger(GatewayCacheManager.class);

    /**
     * Window duration for rate limiting. We use 5-minute windows to allow
     * short bursts (e.g., AI creating a collection + multiple fields)
     * without hitting the per-window limit.
     */
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(5);

    /**
     * Number of windows per day (288 five-minute windows).
     */
    private static final int WINDOWS_PER_DAY = 288;

    /**
     * Default apiCallsPerDay when a tenant is not found in the cache.
     * Matches GovernorLimits.defaults().apiCallsPerDay().
     */
    private static final int DEFAULT_API_CALLS_PER_DAY = 100_000;

    private final Cache<String, String> tenantSlugCache;
    private final Cache<String, Integer> governorLimitCache;
    private final Cache<String, String> customDomainCache; // domain → tenantSlug
    private final Cache<String, byte[]> systemCollectionResponseCache; // tenantId:path → response body
    private final WebClient webClient;

    public GatewayCacheManager(
            WebClient.Builder webClientBuilder,
            @Value("${kelta.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {

        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();

        this.tenantSlugCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .recordStats()
                .build();

        this.governorLimitCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .recordStats()
                .build();

        this.customDomainCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();

        this.systemCollectionResponseCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .recordStats()
                .build();
    }

    // ── Tenant Slug Cache ─────────────────────────────────────────────────

    /**
     * Resolves a tenant slug to a tenant ID.
     *
     * @param slug the tenant slug from the URL path
     * @return the tenant ID if the slug is known, empty otherwise
     */
    public Optional<String> resolveTenantSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String cached = tenantSlugCache.getIfPresent(slug);
        if (cached != null) {
            return SLUG_NOT_FOUND.equals(cached) ? Optional.empty() : Optional.of(cached);
        }

        // Cache miss. The scheduled refresh may not have run yet (cold start, or a refresh that
        // failed while the worker was briefly unreachable), or this is a brand-new tenant. Without a
        // fallback, a VALID tenant 404s for up to one refresh interval — every request to
        // /{slug}/api/** fails, so the UI can't even load. Lazily pull the slug→tenant map from the
        // worker, merge it, and re-check (mirrors resolveCustomDomain's lazy lookup).
        Map<String, String> mapping = fetchTenantSlugMap();
        if (mapping != null && !mapping.isEmpty()) {
            tenantSlugCache.putAll(mapping);
            String resolved = mapping.get(slug);
            if (resolved != null) {
                return Optional.of(resolved);
            }
            // Fetched successfully but the slug genuinely isn't a tenant — negative-cache so an
            // unknown slug doesn't re-fetch on every request (cleared by the next full refresh).
            tenantSlugCache.put(slug, SLUG_NOT_FOUND);
        }
        // On a fetch error we intentionally do NOT negative-cache: a transient worker blip must not
        // pin a valid tenant to "not found" — the next request retries.
        return Optional.empty();
    }

    /**
     * Fetches the slug→tenantId map from the worker (bounded). Returns null on any failure so a
     * transient error never poisons the cache.
     */
    private Map<String, String> fetchTenantSlugMap() {
        try {
            return webClient.get()
                    .uri("/internal/tenants/slug-map")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                    .block(Duration.ofSeconds(2));
        } catch (Exception e) {
            log.warn("Lazy tenant slug-map fetch failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether the given string is a known tenant slug (lazily resolving on a cache miss).
     */
    public boolean isKnownSlug(String slug) {
        return resolveTenantSlug(slug).isPresent();
    }

    /**
     * Returns the number of cached slug mappings.
     */
    public long tenantSlugCacheSize() {
        return tenantSlugCache.estimatedSize();
    }

    /**
     * Bulk-loads tenant slug mappings, replacing existing entries.
     *
     * @param slugMap map of slug to tenantId
     */
    public void refreshTenantSlugs(Map<String, String> slugMap) {
        tenantSlugCache.invalidateAll();
        tenantSlugCache.putAll(slugMap);
        log.info("Refreshed tenant slug cache: {} entries", slugMap.size());
    }

    /**
     * Refreshes the tenant slug cache from the worker service.
     * Called on startup by {@link io.kelta.gateway.config.RouteInitializer}
     * and periodically via {@code @Scheduled}.
     */
    @Scheduled(fixedDelayString = "${kelta.gateway.tenant-slug.cache-refresh-ms:60000}")
    public void refreshTenantSlugsFromWorker() {
        try {
            Map<String, String> mapping = webClient.get()
                    .uri("/internal/tenants/slug-map")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                    .block();

            if (mapping != null && !mapping.isEmpty()) {
                tenantSlugCache.invalidateAll();
                tenantSlugCache.putAll(mapping);
                log.info("Refreshed tenant slug cache: {} entries", mapping.size());
            } else {
                log.warn("Tenant slug-map returned empty; keeping existing cache ({} entries)",
                        tenantSlugCache.estimatedSize());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh tenant slug cache (will retry): {}", e.getMessage());
        }
    }

    // ── Governor Limit Cache ──────────────────────────────────────────────

    /**
     * Loads governor limits from the bootstrap configuration.
     *
     * @param governorLimits map of tenantId to GovernorLimitConfig
     */
    public void loadGovernorLimits(Map<String, GovernorLimitConfig> governorLimits) {
        if (governorLimits == null || governorLimits.isEmpty()) {
            log.warn("No governor limits received from bootstrap; using defaults for all tenants");
            return;
        }

        governorLimitCache.invalidateAll();
        for (Map.Entry<String, GovernorLimitConfig> entry : governorLimits.entrySet()) {
            governorLimitCache.put(entry.getKey(), entry.getValue().getApiCallsPerDay());
        }

        log.info("Loaded governor limits for {} tenants from bootstrap", governorLimits.size());
    }

    /**
     * Updates the governor limit for a specific tenant.
     *
     * @param tenantId       the tenant ID
     * @param apiCallsPerDay the tenant's daily API call limit
     */
    public void updateGovernorLimit(String tenantId, int apiCallsPerDay) {
        governorLimitCache.put(tenantId, apiCallsPerDay);
        log.info("Updated governor limit for tenant {}: {} API calls/day", tenantId, apiCallsPerDay);
    }

    /**
     * Gets the rate limit configuration for a tenant.
     *
     * <p>Converts the daily API call limit into a per-minute rate limit
     * by dividing by 1440 (minutes per day). Ensures a minimum of 1 request
     * per window to avoid blocking all requests.
     *
     * @param tenantId the tenant ID
     * @return the rate limit config for the tenant
     */
    public RateLimitConfig getRateLimitForTenant(String tenantId) {
        Integer dailyLimit = governorLimitCache.getIfPresent(tenantId);
        int limit = (dailyLimit != null) ? dailyLimit : DEFAULT_API_CALLS_PER_DAY;
        int requestsPerWindow = Math.max(1, (limit / WINDOWS_PER_DAY) * 5);
        return new RateLimitConfig(requestsPerWindow, WINDOW_DURATION);
    }

    /**
     * Gets the raw daily API call limit for a tenant.
     *
     * @param tenantId the tenant ID
     * @return optional containing the daily limit, or empty if tenant not in cache
     */
    public Optional<Integer> getGovernorLimit(String tenantId) {
        return Optional.ofNullable(governorLimitCache.getIfPresent(tenantId));
    }

    /**
     * Returns the number of cached governor limits.
     */
    public long governorLimitCacheSize() {
        return governorLimitCache.estimatedSize();
    }

    /**
     * Refreshes the governor limit cache from the worker service.
     *
     * <p>Called when a tenant record change event is received via NATS
     * (e.g., governor limits updated via the admin UI). Fetches the
     * lightweight governor-limits map from the worker's
     * {@code /internal/governor-limits} endpoint and updates the cache.
     */
    public void refreshGovernorLimitsFromWorker() {
        try {
            Map<String, Integer> limitsMap = webClient.get()
                    .uri("/internal/governor-limits")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Integer>>() {})
                    .block();

            if (limitsMap != null && !limitsMap.isEmpty()) {
                governorLimitCache.invalidateAll();
                governorLimitCache.putAll(limitsMap);
                log.info("Refreshed governor limit cache from worker: {} entries", limitsMap.size());
            } else {
                log.warn("Governor limits map returned empty; keeping existing cache ({} entries)",
                        governorLimitCache.estimatedSize());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh governor limit cache from worker: {}", e.getMessage());
        }
    }

    // ── Custom Domain Cache ───────────────────────────────────────────────

    /**
     * Sentinel value indicating a domain was looked up and is not a custom domain.
     * Cached to avoid repeated worker API calls for regular subdomain-based tenants.
     */
    private static final String DOMAIN_NOT_FOUND = "__NOT_FOUND__";

    /**
     * Sentinel value indicating a slug was looked up against the worker and is not a known tenant.
     * Negative-cached so an unknown slug doesn't trigger a worker fetch on every request.
     */
    private static final String SLUG_NOT_FOUND = "__NOT_FOUND__";

    /**
     * Resolves a custom domain to a tenant slug.
     *
     * <p>Uses a three-tier lookup: local cache → worker API → not found.
     * Both positive results (domain → slug) and negative results (domain not found)
     * are cached to avoid repeated worker calls.
     *
     * @param domain the custom domain (e.g., "app.acme.com")
     * @return the tenant slug if the domain is registered, empty otherwise
     */
    public Optional<String> resolveCustomDomain(String domain) {
        String cached = customDomainCache.getIfPresent(domain);
        if (cached != null) {
            if (DOMAIN_NOT_FOUND.equals(cached)) {
                return Optional.empty();
            }
            return Optional.of(cached);
        }

        // Try loading from worker on cache miss
        try {
            String resolved = webClient.get()
                    .uri("/internal/domains/resolve?domain={domain}", domain)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(2));
            if (resolved != null && !resolved.isBlank()) {
                customDomainCache.put(domain, resolved);
                return Optional.of(resolved);
            }
        } catch (Exception e) {
            log.debug("Custom domain lookup failed for {}: {}", domain, e.getMessage());
        }

        // Cache the negative result to avoid calling the worker on every request
        customDomainCache.put(domain, DOMAIN_NOT_FOUND);
        return Optional.empty();
    }

    /**
     * Registers a custom domain mapping in the local cache.
     */
    public void registerCustomDomain(String domain, String tenantSlug) {
        customDomainCache.put(domain, tenantSlug);
    }

    /**
     * Removes a custom domain mapping from the local cache.
     * Evicts the entry entirely so the next lookup will re-fetch from the worker.
     */
    public void removeCustomDomain(String domain) {
        customDomainCache.invalidate(domain);
    }

    /**
     * Evicts all custom domain cache entries (positive and negative).
     * Used as a fallback when a domain change event lacks the specific domain name.
     */
    public void evictAllCustomDomains() {
        customDomainCache.invalidateAll();
        log.info("Evicted all custom domain cache entries");
    }

    // ── System Collection Response Cache ─────────────────────────────────

    /**
     * Returns a cached system collection response for the given cache key.
     *
     * @param cacheKey the composite key (tenantId:path with query string)
     * @return the cached response body bytes, or empty if not cached
     */
    public Optional<byte[]> getSystemCollectionResponse(String cacheKey) {
        return Optional.ofNullable(systemCollectionResponseCache.getIfPresent(cacheKey));
    }

    /**
     * Caches a system collection response.
     *
     * @param cacheKey     the composite key
     * @param responseBody the response body bytes to cache
     */
    public void putSystemCollectionResponse(String cacheKey, byte[] responseBody) {
        systemCollectionResponseCache.put(cacheKey, responseBody);
    }

    /**
     * Evicts cached system collection responses for a specific collection.
     *
     * <p>Since cache keys include the collection name in the path segment,
     * this method removes all entries whose key contains {@code /api/<collectionName>}.
     *
     * @param collectionName the collection name whose cached responses should be evicted
     */
    public void evictSystemCollectionResponses(String collectionName) {
        String pathSegment = "/api/" + collectionName;
        systemCollectionResponseCache.asMap().keySet()
                .removeIf(key -> key.contains(pathSegment));
        log.info("Evicted system collection response cache entries for: {}", collectionName);
    }

    /**
     * Evicts all cached system collection responses.
     */
    public void evictAllSystemCollectionResponses() {
        systemCollectionResponseCache.invalidateAll();
        log.info("Evicted all system collection response cache entries");
    }

    /**
     * Returns the number of cached system collection responses.
     */
    public long systemCollectionResponseCacheSize() {
        return systemCollectionResponseCache.estimatedSize();
    }
}
