package io.kelta.gateway.ratelimit;

import io.kelta.gateway.route.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Redis-based rate limiter implementation.
 *
 * Uses Redis INCR command to track request counts per route and principal.
 * Implements a fixed window rate limiting algorithm with automatic TTL management.
 */
@Service("keltaRedisRateLimiter")
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:";
    private static final String DAILY_KEY_PREFIX = "api-calls-daily:";

    /**
     * Atomically increments the window counter and sets its TTL only when the
     * window is new (count == 1) or a previous EXPIRE was lost (TTL < 0).
     *
     * <p>The TTL must NOT be refreshed on every request: doing so turns the
     * fixed window into an idle-expiry, so under continuous traffic the counter
     * never resets and the tenant ends up permanently locked out once the
     * cumulative count crosses the limit (each rejected request re-extends the
     * TTL, sustaining the lockout indefinitely).
     */
    private static final RedisScript<Long> INCREMENT_WINDOW_SCRIPT = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) < 0 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if a request is allowed based on the rate limit configuration.
     *
     * Algorithm:
     * 1. Build Redis key: "ratelimit:{routeId}:{principal}"
     * 2. Atomically increment counter, setting TTL only for a new window
     * 3. If counter > limit, return not allowed with retry-after = remaining window TTL
     * 4. If counter <= limit, return allowed with remaining count
     *
     * @param routeId The route identifier
     * @param principal The authenticated principal (username)
     * @param config The rate limit configuration
     * @return Mono<RateLimitResult> indicating if the request is allowed
     */
    public Mono<RateLimitResult> checkRateLimit(String routeId, String principal, RateLimitConfig config) {
        String key = buildKey(routeId, principal);
        String windowSeconds = String.valueOf(Math.max(1, config.getWindowDuration().toSeconds()));

        return redisTemplate.execute(INCREMENT_WINDOW_SCRIPT, List.of(key), List.of(windowSeconds))
            .next()
            .flatMap(count -> {
                long limit = config.getRequestsPerWindow();

                if (count > limit) {
                    // Rate limit exceeded — report the true time until the window
                    // resets, not the full window duration.
                    log.debug("Rate limit exceeded for route={}, principal={}, count={}, limit={}",
                        routeId, principal, count, limit);
                    return redisTemplate.getExpire(key)
                        .map(ttl -> ttl.isPositive() ? ttl : config.getWindowDuration())
                        .defaultIfEmpty(config.getWindowDuration())
                        .map(RateLimitResult::notAllowed);
                } else {
                    // Request allowed
                    long remaining = limit - count;
                    log.debug("Rate limit check passed for route={}, principal={}, count={}, remaining={}",
                        routeId, principal, count, remaining);
                    return Mono.just(RateLimitResult.allowed(remaining));
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
