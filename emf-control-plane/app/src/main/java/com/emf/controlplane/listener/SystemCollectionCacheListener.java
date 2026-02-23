package com.emf.controlplane.listener;

import com.emf.controlplane.config.CacheConfig;
import com.emf.runtime.event.RecordChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Kafka listener that invalidates caches when system collection records change.
 *
 * <p>When the worker's {@code DynamicCollectionRouter} handles CRUD operations for
 * system collections (users, profiles, collections, fields, etc.), it publishes
 * {@link RecordChangeEvent} to the {@code emf.record.changed} topic. This listener
 * consumes those events and evicts the relevant caches so that stale data is never
 * served by the control plane.
 *
 * <p>Cache invalidation mapping:
 * <ul>
 *   <li>{@code collections}, {@code fields}, {@code record-types} → collections cache + bootstrap cache</li>
 *   <li>{@code profiles}, {@code permission-sets} → permissions cache</li>
 *   <li>{@code users} → user ID cache + permissions cache</li>
 *   <li>{@code workflow-rules}, {@code validation-rules} → workflow rules cache</li>
 *   <li>{@code tenants} → governor limits cache + bootstrap cache</li>
 *   <li>{@code page-layouts}, {@code layout-assignments} → layouts cache</li>
 *   <li>{@code workers}, {@code collection-assignments} → bootstrap cache</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(name = "emf.control-plane.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class SystemCollectionCacheListener {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionCacheListener.class);

    /** Collections that affect the collection schema cache. */
    private static final Set<String> COLLECTION_CACHE_COLLECTIONS = Set.of(
            "collections", "fields", "record-types"
    );

    /** Collections that affect the permissions cache. */
    private static final Set<String> PERMISSION_CACHE_COLLECTIONS = Set.of(
            "profiles", "permission-sets"
    );

    /** Collections that affect the workflow rules cache. */
    private static final Set<String> WORKFLOW_CACHE_COLLECTIONS = Set.of(
            "workflow-rules", "validation-rules"
    );

    /** Collections that affect the page layouts cache. */
    private static final Set<String> LAYOUT_CACHE_COLLECTIONS = Set.of(
            "page-layouts", "layout-assignments"
    );

    /** Collections that affect the gateway bootstrap cache. */
    private static final Set<String> BOOTSTRAP_CACHE_COLLECTIONS = Set.of(
            "collections", "fields", "record-types", "tenants",
            "workers", "collection-assignments"
    );

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public SystemCollectionCacheListener(CacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles record change events for system collections.
     * Uses the {@code workflowKafkaListenerContainerFactory} which deserializes
     * RecordChangeEvent as JSON strings (matching the worker's StringSerializer).
     *
     * <p>Uses a different group ID from WorkflowEventListener so both listeners
     * receive all messages independently.
     */
    @KafkaListener(
            topics = "${emf.control-plane.kafka.topics.record-changed:emf.record.changed}",
            groupId = "${emf.control-plane.kafka.group-id:emf-control-plane}-cache",
            containerFactory = "workflowKafkaListenerContainerFactory"
    )
    public void onRecordChanged(String message) {
        try {
            RecordChangeEvent event = objectMapper.readValue(message, RecordChangeEvent.class);
            String collectionName = event.getCollectionName();

            if (collectionName == null) {
                return;
            }

            evictCachesForCollection(collectionName, event);
        } catch (Exception e) {
            log.error("Error processing record change event for cache invalidation: {}", e.getMessage(), e);
        }
    }

    /**
     * Evicts caches based on which system collection was modified.
     */
    private void evictCachesForCollection(String collectionName, RecordChangeEvent event) {
        if (COLLECTION_CACHE_COLLECTIONS.contains(collectionName)) {
            evictCache(CacheConfig.COLLECTIONS_CACHE);
            evictCache(CacheConfig.COLLECTIONS_LIST_CACHE);
            log.info("Evicted collection caches due to {} change: recordId={}, changeType={}",
                    collectionName, event.getRecordId(), event.getChangeType());
        }

        if (PERMISSION_CACHE_COLLECTIONS.contains(collectionName)) {
            evictCache(CacheConfig.PERMISSIONS_CACHE);
            log.info("Evicted permissions cache due to {} change: recordId={}, changeType={}",
                    collectionName, event.getRecordId(), event.getChangeType());
        }

        if ("users".equals(collectionName)) {
            evictCache(CacheConfig.USER_ID_CACHE);
            evictCache(CacheConfig.PERMISSIONS_CACHE);
            log.info("Evicted user ID and permissions caches due to user change: recordId={}, changeType={}",
                    event.getRecordId(), event.getChangeType());
        }

        if (WORKFLOW_CACHE_COLLECTIONS.contains(collectionName)) {
            evictCache(CacheConfig.WORKFLOW_RULES_CACHE);
            log.info("Evicted workflow rules cache due to {} change: recordId={}, changeType={}",
                    collectionName, event.getRecordId(), event.getChangeType());
        }

        if ("tenants".equals(collectionName)) {
            evictCache(CacheConfig.GOVERNOR_LIMITS_CACHE);
            log.info("Evicted governor limits cache due to tenant change: recordId={}, changeType={}",
                    event.getRecordId(), event.getChangeType());
        }

        if (LAYOUT_CACHE_COLLECTIONS.contains(collectionName)) {
            evictCache(CacheConfig.LAYOUTS_CACHE);
            log.info("Evicted layouts cache due to {} change: recordId={}, changeType={}",
                    collectionName, event.getRecordId(), event.getChangeType());
        }

        if (BOOTSTRAP_CACHE_COLLECTIONS.contains(collectionName)) {
            evictCache(CacheConfig.BOOTSTRAP_CACHE);
            log.info("Evicted bootstrap cache due to {} change: recordId={}, changeType={}",
                    collectionName, event.getRecordId(), event.getChangeType());
        }
    }

    /**
     * Evicts all entries from the given cache.
     */
    private void evictCache(String cacheName) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.debug("Cleared cache: {}", cacheName);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache '{}': {}", cacheName, e.getMessage());
        }
    }
}
