package com.emf.runtime.model;

import java.util.Map;
import java.util.Objects;

/**
 * Storage configuration for a collection.
 * 
 * @param mode Storage mode (PHYSICAL_TABLES or JSONB_STORE)
 * @param tableName Table name for Mode A storage (ignored in Mode B)
 * @param adapterConfig Additional adapter-specific configuration
 * 
 * @since 1.0.0
 */
public record StorageConfig(
    StorageMode mode,
    String tableName,
    Map<String, String> adapterConfig
) {
    /**
     * Compact constructor with defensive copying and defaults.
     */
    public StorageConfig {
        if (mode == null) {
            mode = StorageMode.PHYSICAL_TABLES;
        }
        adapterConfig = adapterConfig != null ? Map.copyOf(adapterConfig) : Map.of();
    }
    
    /**
     * Creates a default storage configuration for Mode A with the given table name.
     * 
     * @param tableName the table name
     * @return storage configuration for Mode A
     */
    public static StorageConfig physicalTable(String tableName) {
        Objects.requireNonNull(tableName, "tableName cannot be null");
        return new StorageConfig(StorageMode.PHYSICAL_TABLES, tableName, Map.of());
    }
    
    /**
     * Creates a storage configuration for Mode B (JSONB store).
     * 
     * @return storage configuration for Mode B
     */
    public static StorageConfig jsonbStore() {
        return new StorageConfig(StorageMode.JSONB_STORE, null, Map.of());
    }
}
