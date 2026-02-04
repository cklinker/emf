package com.emf.sample.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Service for caching resources in Redis for JSON:API include processing.
 * 
 * <p>Resources are cached with the key pattern "jsonapi:{type}:{id}" and a TTL of 10 minutes.
 * This cache is used by the EnhancedCollectionRouter to efficiently fetch related resources
 * when processing include parameters.
 */
@Service
public class ResourceCacheService {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceCacheService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "jsonapi:";
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Caches a resource after creation or update in JSON:API format.
     * 
     * @param type the resource type (collection name)
     * @param id the resource ID
     * @param resource the resource data to cache (raw format from database)
     */
    public void cacheResource(String type, String id, Map<String, Object> resource) {
        String key = buildKey(type, id);
        try {
            // Convert to JSON:API format
            Map<String, Object> jsonApiResource = toJsonApiFormat(type, id, resource);
            
            String json = objectMapper.writeValueAsString(jsonApiResource);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            log.debug("Cached resource {}:{} in JSON:API format", type, id);
        } catch (JsonProcessingException e) {
            // Log error but don't fail the request
            log.warn("Failed to cache resource {}:{}", type, id, e);
        }
    }
    
    /**
     * Converts raw resource data to JSON:API format.
     * 
     * @param type the resource type
     * @param id the resource ID
     * @param resource the raw resource data
     * @return JSON:API formatted resource
     */
    private Map<String, Object> toJsonApiFormat(String type, String id, Map<String, Object> resource) {
        Map<String, Object> jsonApiResource = new java.util.HashMap<>();
        jsonApiResource.put("type", type);
        jsonApiResource.put("id", id);
        
        // Separate attributes from relationships
        Map<String, Object> attributes = new java.util.HashMap<>();
        Map<String, Object> relationships = new java.util.HashMap<>();
        
        for (Map.Entry<String, Object> entry : resource.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Skip id (it's in the top level)
            if ("id".equals(key)) {
                continue;
            }
            
            // Check if it's a foreign key field (ends with _id)
            if (key.endsWith("_id") && value != null) {
                String relationshipName = key.substring(0, key.length() - 3);
                Map<String, Object> relationshipData = new java.util.HashMap<>();
                relationshipData.put("data", Map.of(
                    "type", relationshipName + "s", // Pluralize (simple approach)
                    "id", value.toString()
                ));
                relationships.put(relationshipName, relationshipData);
            } else {
                attributes.put(key, value);
            }
        }
        
        jsonApiResource.put("attributes", attributes);
        if (!relationships.isEmpty()) {
            jsonApiResource.put("relationships", relationships);
        }
        
        return jsonApiResource;
    }
    
    /**
     * Retrieves a cached resource.
     * 
     * @param type the resource type (collection name)
     * @param id the resource ID
     * @return an Optional containing the resource if found in cache, empty otherwise
     */
    public Optional<Map<String, Object>> getCachedResource(String type, String id) {
        String key = buildKey(type, id);
        String json = redisTemplate.opsForValue().get(key);
        
        if (json != null) {
            try {
                Map<String, Object> resource = objectMapper.readValue(json, 
                    new TypeReference<Map<String, Object>>() {});
                log.debug("Cache hit for resource {}:{}", type, id);
                return Optional.of(resource);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached resource {}:{}", type, id, e);
                // Invalidate corrupted cache entry
                redisTemplate.delete(key);
                return Optional.empty();
            }
        }
        
        log.debug("Cache miss for resource {}:{}", type, id);
        return Optional.empty();
    }
    
    /**
     * Invalidates a cached resource.
     * 
     * @param type the resource type (collection name)
     * @param id the resource ID
     */
    public void invalidateResource(String type, String id) {
        String key = buildKey(type, id);
        redisTemplate.delete(key);
        log.debug("Invalidated cache for resource {}:{}", type, id);
    }
    
    /**
     * Builds the Redis key for a resource.
     * 
     * @param type the resource type
     * @param id the resource ID
     * @return the Redis key in format "jsonapi:{type}:{id}"
     */
    private String buildKey(String type, String id) {
        return KEY_PREFIX + type + ":" + id;
    }
}
