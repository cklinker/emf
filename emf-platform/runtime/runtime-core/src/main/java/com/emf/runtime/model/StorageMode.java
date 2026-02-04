package com.emf.runtime.model;

/**
 * Storage mode for collection data persistence.
 * 
 * @since 1.0.0
 */
public enum StorageMode {
    /**
     * Mode A: Physical Tables.
     * Each collection maps to a real PostgreSQL table with columns matching field definitions.
     * This is the default storage mode.
     */
    PHYSICAL_TABLES,
    
    /**
     * Mode B: JSONB Document Store.
     * Collections are stored in a single shared table with JSONB columns.
     * Useful for highly dynamic schemas or when table creation is restricted.
     */
    JSONB_STORE
}
