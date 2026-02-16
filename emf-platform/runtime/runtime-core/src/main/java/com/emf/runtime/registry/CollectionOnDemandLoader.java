package com.emf.runtime.registry;

import com.emf.runtime.model.CollectionDefinition;

/**
 * Strategy interface for loading a collection on demand when it is not found
 * in the local {@link CollectionRegistry}.
 *
 * <p>Workers implement this to fetch collection definitions from the control
 * plane when a request arrives for a collection that hasn't been loaded yet.
 * This acts as a safety net in case the startup bootstrap or Kafka events
 * missed a collection.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface CollectionOnDemandLoader {

    /**
     * Attempts to load and register a collection by tenant and name.
     *
     * <p>Implementations should:
     * <ol>
     *   <li>Look up the collection from the control plane by tenant ID and name</li>
     *   <li>If found, initialize the collection (register in registry + create storage)</li>
     *   <li>Return the loaded definition, or {@code null} if the collection doesn't exist</li>
     * </ol>
     *
     * <p>This method may be called concurrently from multiple request threads.
     * Implementations must be thread-safe and should handle concurrent calls
     * for the same collection gracefully (e.g., only initialize once).
     *
     * @param collectionName the name of the collection to load
     * @param tenantId the tenant ID from the request, may be {@code null}
     * @return the loaded collection definition, or {@code null} if not found
     */
    CollectionDefinition load(String collectionName, String tenantId);
}
