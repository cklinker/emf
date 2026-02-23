package com.emf.runtime.datapath;

import com.emf.runtime.model.CollectionDefinition;

/**
 * Provider interface for retrieving collection definitions by name.
 *
 * <p>This abstraction allows the DataPath resolution system to access
 * collection metadata without coupling to a specific storage mechanism.
 * Implementations include:
 * <ul>
 *   <li>Worker's {@code CollectionRegistry} + {@code CollectionOnDemandLoader}</li>
 *   <li>Control plane's {@code CollectionService}</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface CollectionDefinitionProvider {

    /**
     * Returns the collection definition for the given collection name.
     *
     * @param collectionName the collection name
     * @return the collection definition, or null if not found
     */
    CollectionDefinition getByName(String collectionName);
}
