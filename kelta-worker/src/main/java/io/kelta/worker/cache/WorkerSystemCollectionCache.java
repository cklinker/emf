package io.kelta.worker.cache;

import io.kelta.runtime.router.SystemCollectionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Worker-side implementation of {@link SystemCollectionCache} backed by
 * the {@link WorkerCacheManager}'s Caffeine cache.
 *
 * <p>Cache keys use the format {@code tenantId:collectionName:qualifier} where
 * qualifier is either a query hash (for list queries) or a record ID (for
 * get-by-id queries). The tenantId component is {@code "_"} for
 * non-tenant-scoped collections.
 *
 * @since 1.0.0
 */
@Component
public class WorkerSystemCollectionCache implements SystemCollectionCache {

    private static final Logger log = LoggerFactory.getLogger(WorkerSystemCollectionCache.class);

    private final WorkerCacheManager cacheManager;

    public WorkerSystemCollectionCache(WorkerCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Optional<Map<String, Object>> getListResponse(String tenantId, String collectionName, String queryHash) {
        String key = buildKey(tenantId, collectionName, "list:" + queryHash);
        return cacheManager.getSystemCollectionResponse(key);
    }

    @Override
    public void putListResponse(String tenantId, String collectionName, String queryHash, Map<String, Object> response) {
        String key = buildKey(tenantId, collectionName, "list:" + queryHash);
        cacheManager.putSystemCollectionResponse(key, response);
    }

    @Override
    public Optional<Map<String, Object>> getByIdResponse(String tenantId, String collectionName, String recordId) {
        String key = buildKey(tenantId, collectionName, "id:" + recordId);
        return cacheManager.getSystemCollectionResponse(key);
    }

    @Override
    public void putByIdResponse(String tenantId, String collectionName, String recordId, Map<String, Object> response) {
        String key = buildKey(tenantId, collectionName, "id:" + recordId);
        cacheManager.putSystemCollectionResponse(key, response);
    }

    @Override
    public void evict(String tenantId, String collectionName) {
        cacheManager.evictSystemCollection(tenantId, collectionName);
    }

    @Override
    public void evictAll() {
        cacheManager.evictAllSystemCollections();
    }

    private String buildKey(String tenantId, String collectionName, String qualifier) {
        return (tenantId != null ? tenantId : "_") + ":" + collectionName + ":" + qualifier;
    }
}
