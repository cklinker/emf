package com.emf.runtime.router;

import java.util.Map;

/**
 * Listener interface for collection write operations.
 *
 * <p>Implementations are notified after successful create, update, and delete
 * operations on collections handled by {@link DynamicCollectionRouter}. This
 * enables cross-cutting concerns like audit logging without modifying the
 * router itself.
 *
 * <p>Listener methods are called synchronously within the request thread.
 * Implementations should be fast and non-blocking. Any exceptions thrown by
 * listener methods are caught and logged by the router — they never interrupt
 * the response to the client.
 *
 * @since 1.0.0
 */
public interface CollectionWriteListener {

    /**
     * Called after a record is successfully created.
     *
     * @param collectionName the collection the record was created in
     * @param tenantId       the tenant ID from the request
     * @param userId         the resolved user ID (may be null for unauthenticated requests)
     * @param recordId       the ID of the newly created record
     * @param data           the record data that was written
     */
    void afterCreate(String collectionName, String tenantId, String userId,
                     String recordId, Map<String, Object> data);

    /**
     * Called after a record is successfully updated.
     *
     * @param collectionName the collection the record belongs to
     * @param tenantId       the tenant ID from the request
     * @param userId         the resolved user ID (may be null for unauthenticated requests)
     * @param recordId       the ID of the updated record
     * @param data           the update data that was applied (partial — only changed fields)
     */
    void afterUpdate(String collectionName, String tenantId, String userId,
                     String recordId, Map<String, Object> data);

    /**
     * Called after a record is successfully deleted.
     *
     * @param collectionName the collection the record was deleted from
     * @param tenantId       the tenant ID from the request
     * @param userId         the resolved user ID (may be null)
     * @param recordId       the ID of the deleted record
     */
    void afterDelete(String collectionName, String tenantId, String userId,
                     String recordId);
}
