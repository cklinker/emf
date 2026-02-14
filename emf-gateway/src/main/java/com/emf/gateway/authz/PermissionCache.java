package com.emf.gateway.authz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache for user effective permissions.
 * Stores EffectivePermissions as JSON strings indexed by user ID with 5-minute TTL.
 */
@Component
public class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);
    private static final Duration TTL = Duration.ofSeconds(300); // 5 minutes
    private static final String KEY_PREFIX = "emf:perms:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public PermissionCache(ReactiveRedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<Optional<EffectivePermissions>> getPermissions(String userId) {
        if (userId == null) {
            return Mono.just(Optional.empty());
        }

        String key = KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .map(json -> {
                    try {
                        EffectivePermissions perms = objectMapper.readValue(json, EffectivePermissions.class);
                        log.debug("Permission cache hit for user: {}", userId);
                        return Optional.of(perms);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached permissions for user {}: {}", userId, e.getMessage());
                        return Optional.<EffectivePermissions>empty();
                    }
                })
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(err -> {
                    log.warn("Failed to read permissions from Redis for user {}: {}", userId, err.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    public Mono<Boolean> putPermissions(String userId, EffectivePermissions permissions) {
        if (userId == null || permissions == null) {
            return Mono.just(false);
        }

        String key = KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(permissions);
            return redisTemplate.opsForValue().set(key, json, TTL)
                    .doOnNext(result -> {
                        if (result) {
                            log.debug("Cached permissions for user: {}", userId);
                        }
                    })
                    .onErrorResume(err -> {
                        log.warn("Failed to cache permissions in Redis for user {}: {}", userId, err.getMessage());
                        return Mono.just(false);
                    });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize permissions for user {}: {}", userId, e.getMessage());
            return Mono.just(false);
        }
    }

    public Mono<Long> evict(String userId) {
        if (userId == null) {
            return Mono.just(0L);
        }

        String key = KEY_PREFIX + userId;
        return redisTemplate.delete(key)
                .doOnNext(count -> log.debug("Evicted permissions cache for user: {}", userId))
                .onErrorResume(err -> {
                    log.warn("Failed to evict permissions from Redis for user {}: {}", userId, err.getMessage());
                    return Mono.just(0L);
                });
    }

    public Mono<Void> clear() {
        Flux<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        return redisTemplate.delete(keys)
                .doOnNext(count -> log.debug("Cleared {} permission cache entries", count))
                .onErrorResume(err -> {
                    log.warn("Failed to clear permission caches from Redis: {}", err.getMessage());
                    return Mono.just(0L);
                })
                .then();
    }
}
