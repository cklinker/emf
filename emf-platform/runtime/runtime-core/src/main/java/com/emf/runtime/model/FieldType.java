package com.emf.runtime.model;

/**
 * Supported data types for collection fields.
 * 
 * <p>Each field type maps to appropriate PostgreSQL types in Mode A storage
 * and JSON types in Mode B storage.
 * 
 * @since 1.0.0
 */
public enum FieldType {
    /**
     * String/text data type.
     * Maps to TEXT in PostgreSQL.
     */
    STRING,
    
    /**
     * 32-bit integer data type.
     * Maps to INTEGER in PostgreSQL.
     */
    INTEGER,
    
    /**
     * 64-bit long integer data type.
     * Maps to BIGINT in PostgreSQL.
     */
    LONG,
    
    /**
     * Double-precision floating point data type.
     * Maps to DOUBLE PRECISION in PostgreSQL.
     */
    DOUBLE,
    
    /**
     * Boolean data type.
     * Maps to BOOLEAN in PostgreSQL.
     */
    BOOLEAN,
    
    /**
     * Date data type (without time component).
     * Maps to DATE in PostgreSQL.
     * Expected format: ISO-8601 date string (yyyy-MM-dd).
     */
    DATE,
    
    /**
     * Date and time data type.
     * Maps to TIMESTAMP in PostgreSQL.
     * Expected format: ISO-8601 datetime string.
     */
    DATETIME,
    
    /**
     * JSON/structured data type.
     * Maps to JSONB in PostgreSQL.
     * Can hold nested objects or arrays.
     */
    JSON
}
