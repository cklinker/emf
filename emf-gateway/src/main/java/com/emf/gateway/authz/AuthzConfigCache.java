package com.emf.gateway.authz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for authorization configurations.
 * 
 * Stores AuthzConfig objects indexed by collection ID.
 * Updated by Kafka event listeners when authorization configuration changes.
 */
@Component
public class AuthzConfigCache {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthzConfigCache.class);
    
    private final ConcurrentHashMap<String, AuthzConfig> cache = new ConcurrentHashMap<>();
    
    /**
     * Retrieves authorization configuration for a collection.
     * 
     * @param collectionId the collection ID to lookup
     * @return Optional containing the AuthzConfig if found, empty otherwise
     */
    public Optional<AuthzConfig> getConfig(String collectionId) {
        if (collectionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(collectionId));
    }
    
    /**
     * Updates or adds authorization configuration for a collection.
     * 
     * @param collectionId the collection ID
     * @param config the authorization configuration
     */
    public void updateConfig(String collectionId, AuthzConfig config) {
        if (collectionId == null || config == null) {
            logger.warn("Attempted to update cache with null collectionId or config");
            return;
        }
        
        cache.put(collectionId, config);
        logger.debug("Updated authz config for collection: {}", collectionId);
    }
    
    /**
     * Removes authorization configuration for a collection.
     * 
     * @param collectionId the collection ID to remove
     */
    public void removeConfig(String collectionId) {
        if (collectionId == null) {
            return;
        }
        
        AuthzConfig removed = cache.remove(collectionId);
        if (removed != null) {
            logger.debug("Removed authz config for collection: {}", collectionId);
        }
    }
    
    /**
     * Clears all authorization configurations from the cache.
     * Primarily used for testing.
     */
    public void clear() {
        cache.clear();
        logger.debug("Cleared all authz configs from cache");
    }
    
    /**
     * Returns the number of configurations in the cache.
     * 
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }
}
