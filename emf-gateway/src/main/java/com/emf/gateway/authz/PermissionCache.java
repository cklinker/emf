package com.emf.gateway.authz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for user effective permissions.
 * Stores EffectivePermissions indexed by user ID with TTL support.
 */
@Component
public class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);
    private static final long DEFAULT_TTL_SECONDS = 300; // 5 minutes

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Optional<EffectivePermissions> getPermissions(String userId) {
        if (userId == null) {
            return Optional.empty();
        }

        CacheEntry entry = cache.get(userId);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(userId);
            log.debug("Permission cache expired for user: {}", userId);
            return Optional.empty();
        }

        return Optional.of(entry.permissions);
    }

    public void putPermissions(String userId, EffectivePermissions permissions) {
        if (userId == null || permissions == null) {
            return;
        }
        cache.put(userId, new CacheEntry(permissions, Instant.now().plusSeconds(DEFAULT_TTL_SECONDS)));
        log.debug("Cached permissions for user: {}", userId);
    }

    public void evict(String userId) {
        if (userId != null) {
            cache.remove(userId);
            log.debug("Evicted permissions cache for user: {}", userId);
        }
    }

    public void clear() {
        cache.clear();
        log.debug("Cleared all permission caches");
    }

    private record CacheEntry(EffectivePermissions permissions, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
