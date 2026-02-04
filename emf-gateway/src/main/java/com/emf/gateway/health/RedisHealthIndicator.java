package com.emf.gateway.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Custom health indicator for Redis connectivity.
 * 
 * This indicator checks if Redis is reachable by executing a simple
 * operation through the reactive Redis template. It provides detailed 
 * information about the connection status and any errors encountered.
 * 
 * Validates: Requirements 12.2
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);
    private static final String HEALTH_CHECK_KEY = "health:check";
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    public RedisHealthIndicator(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Health health() {
        try {
            // Attempt to execute a simple Redis operation (check if key exists)
            Boolean result = redisTemplate.hasKey(HEALTH_CHECK_KEY)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .block();
            
            // If we got a result (even false), Redis is reachable
            if (result != null) {
                log.debug("Redis health check passed");
                return Health.up()
                    .withDetail("connection", "active")
                    .build();
            } else {
                log.warn("Redis health check failed: null response");
                return Health.down()
                    .withDetail("connection", "degraded")
                    .withDetail("reason", "Null response from Redis")
                    .build();
            }
        } catch (Exception e) {
            log.error("Redis health check failed with exception", e);
            return Health.down()
                .withDetail("connection", "failed")
                .withDetail("error", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
