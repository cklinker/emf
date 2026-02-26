package com.emf.gateway.ratelimit;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.filter.TenantResolutionFilter;
import com.emf.gateway.route.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Global filter that enforces per-tenant rate limiting based on governor limits.
 * Runs after authentication (order -50, after -100) but before routing decisions.
 *
 * This filter:
 * - Extracts the tenant ID from the request context (set by TenantResolutionFilter)
 * - Looks up the tenant's governor limits (apiCallsPerDay) from the cache
 * - Calls RedisRateLimiter to check if the request is allowed within the per-minute window
 * - Returns 429 Too Many Requests if the rate limit is exceeded
 * - Adds rate limit headers to the response (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset)
 * - Includes Retry-After header when rate limit is exceeded
 *
 * Rate limits are derived from the tenant's governor limit (apiCallsPerDay)
 * divided into per-minute windows: requestsPerMinute = apiCallsPerDay / 1440.
 *
 * Validates: Requirements 8.2, 8.4, 8.5
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RedisRateLimiter rateLimiter;
    private final TenantGovernorLimitCache governorLimitCache;

    /**
     * Creates a new RateLimitFilter.
     *
     * @param rateLimiter the Redis-based rate limiter
     * @param governorLimitCache the tenant governor limit cache
     */
    public RateLimitFilter(RedisRateLimiter rateLimiter,
                          TenantGovernorLimitCache governorLimitCache) {
        this.rateLimiter = rateLimiter;
        this.governorLimitCache = governorLimitCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get the authenticated principal (set by JwtAuthenticationFilter)
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);

        // If no principal, skip rate limiting (authentication filter will handle this)
        if (principal == null) {
            log.debug("No principal found, skipping rate limiting for path: {}",
                exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        }

        // Get the tenant ID from the request context
        String tenantId = TenantResolutionFilter.getTenantId(exchange);
        if (tenantId == null || tenantId.isBlank()) {
            log.debug("No tenant context, skipping rate limiting for path: {}",
                exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        }

        // Get the rate limit config from the tenant's governor limits
        RateLimitConfig config = governorLimitCache.getRateLimitForTenant(tenantId);
        String rateLimitKey = tenantId;

        log.debug("Checking rate limit for tenant: {}, principal: {}, limit: {} requests per {}",
            tenantId, principal.getUsername(), config.getRequestsPerWindow(), config.getWindowDuration());

        // Check rate limit keyed by tenant (all users in a tenant share the limit)
        return rateLimiter.checkRateLimit(rateLimitKey, "tenant", config)
            .flatMap(result -> {
                if (result.isAllowed()) {
                    // Request allowed - add rate limit headers and continue
                    log.debug("Rate limit check passed for tenant: {}, remaining: {}",
                        tenantId, result.getRemainingRequests());

                    addRateLimitHeaders(exchange, config, result);

                    // Increment daily API call counter (fire-and-forget for the Governor Limits page)
                    rateLimiter.incrementDailyCounter(tenantId).subscribe();

                    return chain.filter(exchange);
                } else {
                    // Rate limit exceeded - return 429
                    log.warn("Rate limit exceeded for tenant: {}, principal: {}, retry after: {}",
                        tenantId, principal.getUsername(), result.getRetryAfter());

                    return tooManyRequests(exchange, config, result);
                }
            });
    }

    /**
     * Adds rate limit headers to the response.
     *
     * @param exchange the server web exchange
     * @param config the rate limit configuration
     * @param result the rate limit check result
     */
    private void addRateLimitHeaders(ServerWebExchange exchange, RateLimitConfig config, RateLimitResult result) {
        HttpHeaders headers = exchange.getResponse().getHeaders();

        // X-RateLimit-Limit: Maximum requests per window
        headers.add("X-RateLimit-Limit", String.valueOf(config.getRequestsPerWindow()));

        // X-RateLimit-Remaining: Remaining requests in current window
        headers.add("X-RateLimit-Remaining", String.valueOf(result.getRemainingRequests()));

        // X-RateLimit-Reset: Timestamp when window resets (current time + window duration)
        long resetTimestamp = Instant.now().plus(config.getWindowDuration()).getEpochSecond();
        headers.add("X-RateLimit-Reset", String.valueOf(resetTimestamp));
    }

    /**
     * Returns a 429 Too Many Requests response with rate limit information.
     *
     * @param exchange the server web exchange
     * @param config the rate limit configuration
     * @param result the rate limit check result
     * @return a Mono that completes the response
     */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, RateLimitConfig config, RateLimitResult result) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        // Add rate limit headers
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(config.getRequestsPerWindow()));
        headers.add("X-RateLimit-Remaining", "0");

        long resetTimestamp = Instant.now().plus(config.getWindowDuration()).getEpochSecond();
        headers.add("X-RateLimit-Reset", String.valueOf(resetTimestamp));

        // Add Retry-After header (in seconds)
        long retryAfterSeconds = result.getRetryAfter().getSeconds();
        headers.add("Retry-After", String.valueOf(retryAfterSeconds));

        String errorJson = String.format(
            "{\"error\":{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Retry after %d seconds.\",\"path\":\"%s\"}}",
            retryAfterSeconds,
            exchange.getRequest().getPath().value()
        );

        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorJson.getBytes()))
        );
    }

    @Override
    public int getOrder() {
        return -50; // Run after authentication (-100) but before routing (0)
    }
}
