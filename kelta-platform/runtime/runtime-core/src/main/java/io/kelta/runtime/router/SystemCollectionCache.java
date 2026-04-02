package io.kelta.runtime.router;

import java.util.Map;
import java.util.Optional;

/**
 * Cache abstraction for system collection query results.
 *
 * <p>Implementations cache the JSON:API response for list and get-by-id
 * operations on system collections (e.g., {@code ui_pages}, {@code ui_menus},
 * {@code collections}, {@code tenants}). These collections change infrequently
 * but are queried on every page load.
 *
 * <p>Cache keys are scoped by tenant ID + collection name + query fingerprint
 * so that different tenants and different query parameters produce distinct
 * cache entries.
 *
 * <p>This interface is injected into {@link DynamicCollectionRouter} via
 * {@code @Autowired(required = false)}. When no implementation is available
 * (e.g., in tests), the router falls back to querying the database directly.
 *
 * @since 1.0.0
 */
public interface SystemCollectionCache {

    /**
     * Returns a cached JSON:API response for a system collection list query.
     *
     * @param tenantId       the tenant ID (may be {@code null} for non-tenant-scoped collections)
     * @param collectionName the collection name
     * @param queryHash      a deterministic hash of the query parameters (filters, sort, pagination)
     * @return the cached response if present, empty otherwise
     */
    Optional<Map<String, Object>> getListResponse(String tenantId, String collectionName, String queryHash);

    /**
     * Caches a JSON:API response for a system collection list query.
     *
     * @param tenantId       the tenant ID (may be {@code null} for non-tenant-scoped collections)
     * @param collectionName the collection name
     * @param queryHash      a deterministic hash of the query parameters
     * @param response       the JSON:API response to cache
     */
    void putListResponse(String tenantId, String collectionName, String queryHash, Map<String, Object> response);

    /**
     * Returns a cached JSON:API response for a system collection get-by-id query.
     *
     * @param tenantId       the tenant ID (may be {@code null} for non-tenant-scoped collections)
     * @param collectionName the collection name
     * @param recordId       the record ID
     * @return the cached response if present, empty otherwise
     */
    Optional<Map<String, Object>> getByIdResponse(String tenantId, String collectionName, String recordId);

    /**
     * Caches a JSON:API response for a system collection get-by-id query.
     *
     * @param tenantId       the tenant ID (may be {@code null} for non-tenant-scoped collections)
     * @param collectionName the collection name
     * @param recordId       the record ID
     * @param response       the JSON:API response to cache
     */
    void putByIdResponse(String tenantId, String collectionName, String recordId, Map<String, Object> response);

    /**
     * Evicts all cached entries for a specific system collection within a tenant.
     *
     * <p>Called after any write operation (create, update, delete) on a system
     * collection to ensure subsequent reads return fresh data.
     *
     * @param tenantId       the tenant ID (may be {@code null} for non-tenant-scoped collections)
     * @param collectionName the collection name
     */
    void evict(String tenantId, String collectionName);

    /**
     * Evicts all cached system collection entries across all tenants.
     *
     * <p>Used as a fallback when a broad invalidation is needed (e.g., schema changes).
     */
    void evictAll();
}
