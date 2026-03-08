package io.kelta.runtime.model;

import java.util.Map;
import java.util.Objects;

/**
 * Storage configuration for a collection.
 *
 * @param tableName Table name for physical table storage
 * @param adapterConfig Additional adapter-specific configuration
 *
 * @since 1.0.0
 */
public record StorageConfig(
    String tableName,
    Map<String, String> adapterConfig
) {
    /**
     * Compact constructor with defensive copying and defaults.
     */
    public StorageConfig {
        adapterConfig = adapterConfig != null ? Map.copyOf(adapterConfig) : Map.of();
    }

    /**
     * Creates a storage configuration for the given table name.
     *
     * @param tableName the table name
     * @return storage configuration
     */
    public static StorageConfig physicalTable(String tableName) {
        Objects.requireNonNull(tableName, "tableName cannot be null");
        return new StorageConfig(tableName, Map.of());
    }
}
