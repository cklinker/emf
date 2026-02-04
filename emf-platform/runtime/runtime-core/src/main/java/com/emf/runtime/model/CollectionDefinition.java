package com.emf.runtime.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * In-memory representation of a collection definition.
 * 
 * <p>Contains all configuration for a runtime-defined resource type including fields,
 * validation rules, storage configuration, API configuration, authorization configuration,
 * and event configuration.
 * 
 * <p>This record is immutable and uses defensive copying for collection fields.
 * 
 * @param name Collection name (required, must be non-null and non-blank)
 * @param displayName Human-readable display name
 * @param description Description of the collection
 * @param fields List of field definitions (required, must have at least one field)
 * @param storageConfig Storage configuration (Mode A or Mode B)
 * @param apiConfig API configuration (enabled operations, base path)
 * @param authzConfig Authorization configuration (roles)
 * @param eventsConfig Event publishing configuration (Kafka)
 * @param version Version number for optimistic locking
 * @param createdAt Timestamp when the collection was created
 * @param updatedAt Timestamp when the collection was last updated
 * 
 * @since 1.0.0
 */
public record CollectionDefinition(
    String name,
    String displayName,
    String description,
    List<FieldDefinition> fields,
    StorageConfig storageConfig,
    ApiConfig apiConfig,
    AuthzConfig authzConfig,
    EventsConfig eventsConfig,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * Compact constructor with validation and defensive copying.
     */
    public CollectionDefinition {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        Objects.requireNonNull(fields, "fields cannot be null");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields cannot be empty");
        }
        
        // Defensive copy for fields list
        fields = List.copyOf(fields);
    }
    
    /**
     * Gets a field definition by name.
     * 
     * @param fieldName the field name to look up
     * @return the field definition, or null if not found
     */
    public FieldDefinition getField(String fieldName) {
        return fields.stream()
            .filter(f -> f.name().equals(fieldName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Checks if a field exists in this collection.
     * 
     * @param fieldName the field name to check
     * @return true if the field exists, false otherwise
     */
    public boolean hasField(String fieldName) {
        return getField(fieldName) != null;
    }
    
    /**
     * Gets all field names in this collection.
     * 
     * @return list of field names
     */
    public List<String> getFieldNames() {
        return fields.stream()
            .map(FieldDefinition::name)
            .toList();
    }
    
    /**
     * Creates a new collection definition with an incremented version.
     * 
     * @return a new collection definition with version + 1 and updated timestamp
     */
    public CollectionDefinition withIncrementedVersion() {
        return new CollectionDefinition(
            name,
            displayName,
            description,
            fields,
            storageConfig,
            apiConfig,
            authzConfig,
            eventsConfig,
            version + 1,
            createdAt,
            Instant.now()
        );
    }
    
    /**
     * Creates a new collection definition with updated fields.
     * 
     * @param newFields the new field definitions
     * @return a new collection definition with the updated fields
     */
    public CollectionDefinition withFields(List<FieldDefinition> newFields) {
        return new CollectionDefinition(
            name,
            displayName,
            description,
            newFields,
            storageConfig,
            apiConfig,
            authzConfig,
            eventsConfig,
            version + 1,
            createdAt,
            Instant.now()
        );
    }
    
    /**
     * Creates a new builder for constructing collection definitions.
     * 
     * @return a new builder instance
     */
    public static CollectionDefinitionBuilder builder() {
        return new CollectionDefinitionBuilder();
    }
}
