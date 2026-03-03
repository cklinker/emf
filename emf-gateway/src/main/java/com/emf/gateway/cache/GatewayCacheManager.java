package com.emf.gateway.cache;

import com.emf.gateway.config.GovernorLimitConfig;
import com.emf.gateway.route.RateLimitConfig;
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
 *       Populated from bootstrap configuration and updated when tenant governor limits change.
 *       Expires after 5 minutes.</li>
 * </ul>
 */
@Component
public class GatewayCacheManager {

    private static final Logger log = LoggerFactory.getLogger(GatewayCacheManager.class);

    /**
     * Window duration for rate limiting. We use 1-minute windows and divide
     * the daily limit into per-minute buckets.
     */
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    /**
     * Number of windows per day (1440 minutes).
     */
    private static final int WINDOWS_PER_DAY = 1440;

    /**
     * Default apiCallsPerDay when a tenant is not found in the cache.
     * Matches GovernorLimits.defaults().apiCallsPerDay().
     */
    private static final int DEFAULT_API_CALLS_PER_DAY = 100_000;

    private final Cache<String, String> tenantSlugCache;
    private final Cache<String, Integer> governorLimitCache;
    private final WebClient webClient;

    public GatewayCacheManager(
            WebClient.Builder webClientBuilder,
            @Value("${emf.gateway.worker-service-url:http://emf-worker:80}") String workerServiceUrl) {

        this.webClient = webClientBuilder.baseUrl(workerServiceUrl).build();

        this.tenantSlugCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .recordStats()
                .build();

        this.governorLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
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
        return Optional.ofNullable(tenantSlugCache.getIfPresent(slug));
    }

    /**
     * Checks whether the given string is a known tenant slug.
     */
    public boolean isKnownSlug(String slug) {
        return slug != null && tenantSlugCache.getIfPresent(slug) != null;
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
     * Called on startup by {@link com.emf.gateway.config.RouteInitializer}
     * and periodically via {@code @Scheduled}.
     */
    @Scheduled(fixedDelayString = "${emf.gateway.tenant-slug.cache-refresh-ms:60000}")
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
        int requestsPerMinute = Math.max(1, limit / WINDOWS_PER_DAY);
        return new RateLimitConfig(requestsPerMinute, WINDOW_DURATION);
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
     * <p>Called when a tenant record change event is received (e.g., governor
     * limits updated via the admin UI). Fetches the lightweight governor-limits
     * map from the worker's {@code /internal/governor-limits} endpoint and
     * updates the cache.
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
}
