package com.emf.runtime.model;

import java.util.List;
import java.util.Objects;

/**
 * Definition of a single field within a collection.
 * 
 * <p>Specifies the field's name, data type, validation constraints, and relationships.
 * This record is immutable and uses defensive copying for collection fields.
 * 
 * @param name Field name (required, must be non-null and non-blank)
 * @param type Data type of the field (required)
 * @param nullable Whether the field accepts null values (default: true)
 * @param immutable Whether the field can be updated after creation (default: false)
 * @param unique Whether the field value must be unique across all records (default: false)
 * @param defaultValue Default value for the field when not provided
 * @param validationRules Validation constraints (min/max value, length, pattern)
 * @param enumValues List of allowed values for enum-type fields
 * @param referenceConfig Configuration for foreign key relationships
 * 
 * @since 1.0.0
 */
public record FieldDefinition(
    String name,
    FieldType type,
    boolean nullable,
    boolean immutable,
    boolean unique,
    Object defaultValue,
    ValidationRules validationRules,
    List<String> enumValues,
    ReferenceConfig referenceConfig
) {
    /**
     * Compact constructor with validation and defensive copying.
     */
    public FieldDefinition {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        Objects.requireNonNull(type, "type cannot be null");
        
        // Defensive copy for enumValues
        enumValues = enumValues != null ? List.copyOf(enumValues) : null;
    }
    
    /**
     * Creates a simple string field.
     * 
     * @param name the field name
     * @return a nullable string field definition
     */
    public static FieldDefinition string(String name) {
        return new FieldDefinition(name, FieldType.STRING, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a required string field.
     * 
     * @param name the field name
     * @return a non-nullable string field definition
     */
    public static FieldDefinition requiredString(String name) {
        return new FieldDefinition(name, FieldType.STRING, false, false, false, null, null, null, null);
    }
    
    /**
     * Creates a simple integer field.
     * 
     * @param name the field name
     * @return a nullable integer field definition
     */
    public static FieldDefinition integer(String name) {
        return new FieldDefinition(name, FieldType.INTEGER, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a required integer field.
     * 
     * @param name the field name
     * @return a non-nullable integer field definition
     */
    public static FieldDefinition requiredInteger(String name) {
        return new FieldDefinition(name, FieldType.INTEGER, false, false, false, null, null, null, null);
    }
    
    /**
     * Creates a simple long field.
     * 
     * @param name the field name
     * @return a nullable long field definition
     */
    public static FieldDefinition longField(String name) {
        return new FieldDefinition(name, FieldType.LONG, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a simple double field.
     * 
     * @param name the field name
     * @return a nullable double field definition
     */
    public static FieldDefinition doubleField(String name) {
        return new FieldDefinition(name, FieldType.DOUBLE, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a simple boolean field.
     * 
     * @param name the field name
     * @return a nullable boolean field definition
     */
    public static FieldDefinition bool(String name) {
        return new FieldDefinition(name, FieldType.BOOLEAN, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a boolean field with a default value.
     * 
     * @param name the field name
     * @param defaultValue the default value
     * @return a boolean field definition with default
     */
    public static FieldDefinition bool(String name, boolean defaultValue) {
        return new FieldDefinition(name, FieldType.BOOLEAN, false, false, false, defaultValue, null, null, null);
    }
    
    /**
     * Creates a date field.
     * 
     * @param name the field name
     * @return a nullable date field definition
     */
    public static FieldDefinition date(String name) {
        return new FieldDefinition(name, FieldType.DATE, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a datetime field.
     * 
     * @param name the field name
     * @return a nullable datetime field definition
     */
    public static FieldDefinition datetime(String name) {
        return new FieldDefinition(name, FieldType.DATETIME, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates a JSON field.
     * 
     * @param name the field name
     * @return a nullable JSON field definition
     */
    public static FieldDefinition json(String name) {
        return new FieldDefinition(name, FieldType.JSON, true, false, false, null, null, null, null);
    }
    
    /**
     * Creates an enum field with allowed values.
     * 
     * @param name the field name
     * @param values the allowed enum values
     * @return an enum field definition
     */
    public static FieldDefinition enumField(String name, List<String> values) {
        Objects.requireNonNull(values, "values cannot be null");
        return new FieldDefinition(name, FieldType.STRING, false, false, false, null, null, values, null);
    }
    
    /**
     * Creates a reference field to another collection.
     * 
     * @param name the field name
     * @param targetCollection the target collection name
     * @return a reference field definition
     */
    public static FieldDefinition reference(String name, String targetCollection) {
        return new FieldDefinition(name, FieldType.STRING, true, false, false, null, null, null, 
            ReferenceConfig.toCollection(targetCollection));
    }
}
