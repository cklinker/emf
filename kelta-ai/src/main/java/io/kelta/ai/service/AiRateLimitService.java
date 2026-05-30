package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Per-tenant request-rate limiter for AI chat endpoints. Uses a Redis-backed
 * fixed 60-second window keyed by {@code ai-ratelimit:<tenantId>:<minute>}.
 *
 * <p>Fail-open: if Redis is unreachable the request is allowed and the failure
 * is logged. This matches the gateway's existing rate-limit behavior and avoids
 * cascading a Redis outage into an AI-feature outage.
 *
 * @since 1.0.0
 */
@Service
public class AiRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitService.class);
    private static final String KEY_PREFIX = "ai-ratelimit:";
    private static final Duration KEY_TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;
    private final AiConfigProperties config;

    public AiRateLimitService(StringRedisTemplate redisTemplate, AiConfigProperties config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    public Decision check(String tenantId) {
        AiConfigProperties.RateLimitProperties props = config.rateLimit();
        if (props == null || !props.enabled()) {
            return Decision.allow();
        }
        long now = Instant.now().getEpochSecond();
        long bucket = now / 60L;
        String key = KEY_PREFIX + tenantId + ":" + bucket;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, KEY_TTL);
            }
            if (count != null && count > props.requestsPerMinute()) {
                long retryAfter = 60L - (now % 60L);
                return Decision.deny(retryAfter);
            }
            return Decision.allow();
        } catch (Exception e) {
            log.warn("Rate-limit check failed for tenant {}, allowing request: {}", tenantId, e.getMessage());
            return Decision.allow();
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static Decision allow() {
            return new Decision(true, 0L);
        }

        public static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
