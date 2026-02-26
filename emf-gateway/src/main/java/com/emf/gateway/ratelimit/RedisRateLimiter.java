package com.emf.gateway.ratelimit;

import com.emf.gateway.route.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Redis-based rate limiter implementation.
 * 
 * Uses Redis INCR command to track request counts per route and principal.
 * Implements a fixed window rate limiting algorithm with automatic TTL management.
 */
@Service("emfRedisRateLimiter")
public class RedisRateLimiter {
    
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:";
    private static final String DAILY_KEY_PREFIX = "api-calls-daily:";
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    public RedisRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Checks if a request is allowed based on the rate limit configuration.
     * 
     * Algorithm:
     * 1. Build Redis key: "ratelimit:{routeId}:{principal}"
     * 2. Increment counter in Redis
     * 3. If counter == 1, set TTL to window duration
     * 4. If counter > limit, return not allowed with retry-after
     * 5. If counter <= limit, return allowed with remaining count
     * 
     * @param routeId The route identifier
     * @param principal The authenticated principal (username)
     * @param config The rate limit configuration
     * @return Mono<RateLimitResult> indicating if the request is allowed
     */
    public Mono<RateLimitResult> checkRateLimit(String routeId, String principal, RateLimitConfig config) {
        String key = buildKey(routeId, principal);
        
        return redisTemplate.opsForValue()
            .increment(key)
            .flatMap(count -> {
                // If this is the first request in the window, set TTL
                if (count == 1) {
                    return redisTemplate.expire(key, config.getWindowDuration())
                        .thenReturn(count);
                }
                return Mono.just(count);
            })
            .map(count -> {
                long limit = config.getRequestsPerWindow();
                
                if (count > limit) {
                    // Rate limit exceeded
                    log.debug("Rate limit exceeded for route={}, principal={}, count={}, limit={}", 
                        routeId, principal, count, limit);
                    return RateLimitResult.notAllowed(config.getWindowDuration());
                } else {
                    // Request allowed
                    long remaining = limit - count;
                    log.debug("Rate limit check passed for route={}, principal={}, count={}, remaining={}", 
                        routeId, principal, count, remaining);
                    return RateLimitResult.allowed(remaining);
                }
            })
            .onErrorResume(error -> {
                // Graceful degradation: if Redis is unavailable, allow the request
                log.warn("Redis error during rate limit check for route={}, principal={}. Allowing request. Error: {}", 
                    routeId, principal, error.getMessage());
                return Mono.just(RateLimitResult.allowed(config.getRequestsPerWindow()));
            });
    }
    
    /**
     * Increments the daily API call counter for a tenant.
     *
     * <p>Uses a Redis key with today's UTC date: {@code api-calls-daily:<tenantId>:<yyyy-MM-dd>}.
     * The key has a 48-hour TTL to ensure cleanup after the day ends.
     * This counter is read by the worker's GovernorLimitsController to display
     * daily API call usage on the Governor Limits page.
     *
     * @param tenantId the tenant ID to track
     * @return Mono that completes when the counter is incremented
     */
    public Mono<Void> incrementDailyCounter(String tenantId) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String key = DAILY_KEY_PREFIX + tenantId + ":" + today;

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // First call today — set TTL to 48 hours for cleanup
                        return redisTemplate.expire(key, Duration.ofHours(48))
                                .then();
                    }
                    return Mono.empty();
                })
                .onErrorResume(error -> {
                    log.warn("Failed to increment daily API call counter for tenant {}: {}",
                            tenantId, error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Builds the Redis key for rate limiting.
     *
     * @param routeId The route identifier
     * @param principal The authenticated principal
     * @return Redis key in format "ratelimit:{routeId}:{principal}"
     */
    private String buildKey(String routeId, String principal) {
        return KEY_PREFIX + routeId + ":" + principal;
    }
}
