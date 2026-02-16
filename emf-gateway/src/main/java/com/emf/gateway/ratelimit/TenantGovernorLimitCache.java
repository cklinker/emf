package com.emf.gateway.ratelimit;

import com.emf.gateway.config.GovernorLimitConfig;
import com.emf.gateway.route.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of per-tenant governor limits for gateway rate limiting.
 *
 * <p>Populated on startup from the bootstrap configuration and refreshed
 * when tenant governor limits change. The gateway uses these limits to
 * enforce per-tenant API call rate limiting (apiCallsPerDay).
 *
 * <p>The daily limit is converted into a per-minute rate limit window
 * for practical rate limiting enforcement:
 * {@code requestsPerWindow = apiCallsPerDay / 1440} (minutes per day).
 */
@Component
public class TenantGovernorLimitCache {

    private static final Logger log = LoggerFactory.getLogger(TenantGovernorLimitCache.class);

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

    private final ConcurrentHashMap<String, Integer> tenantApiCallsPerDay = new ConcurrentHashMap<>();

    /**
     * Loads governor limits from the bootstrap configuration.
     *
     * @param governorLimits map of tenantId to GovernorLimitConfig
     */
    public void loadFromBootstrap(Map<String, GovernorLimitConfig> governorLimits) {
        if (governorLimits == null || governorLimits.isEmpty()) {
            log.warn("No governor limits received from bootstrap; using defaults for all tenants");
            return;
        }

        tenantApiCallsPerDay.clear();
        for (Map.Entry<String, GovernorLimitConfig> entry : governorLimits.entrySet()) {
            tenantApiCallsPerDay.put(entry.getKey(), entry.getValue().getApiCallsPerDay());
        }

        log.info("Loaded governor limits for {} tenants from bootstrap", governorLimits.size());
    }

    /**
     * Updates the governor limit for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @param apiCallsPerDay the tenant's daily API call limit
     */
    public void updateTenantLimit(String tenantId, int apiCallsPerDay) {
        tenantApiCallsPerDay.put(tenantId, apiCallsPerDay);
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
        int dailyLimit = tenantApiCallsPerDay.getOrDefault(tenantId, DEFAULT_API_CALLS_PER_DAY);
        int requestsPerMinute = Math.max(1, dailyLimit / WINDOWS_PER_DAY);
        return new RateLimitConfig(requestsPerMinute, WINDOW_DURATION);
    }

    /**
     * Gets the raw daily API call limit for a tenant.
     *
     * @param tenantId the tenant ID
     * @return optional containing the daily limit, or empty if tenant not in cache
     */
    public Optional<Integer> getApiCallsPerDay(String tenantId) {
        return Optional.ofNullable(tenantApiCallsPerDay.get(tenantId));
    }

    /**
     * Returns the number of cached tenant limits.
     */
    public int size() {
        return tenantApiCallsPerDay.size();
    }
}
