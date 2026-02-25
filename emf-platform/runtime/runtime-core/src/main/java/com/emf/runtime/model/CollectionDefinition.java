package com.emf.runtime.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * @param systemCollection Whether this is a system-defined collection (managed by the platform)
 * @param tenantScoped Whether this collection's data is scoped to a tenant
 * @param readOnly Whether this collection is read-only (no create/update/delete via API)
 * @param immutableFields Set of field names that cannot be updated after creation
 * @param columnMapping Map of API field name to physical database column name
 * @param displayFieldName Name of the field used for display-value lookups (nullable).
 *                         When set and the referenced field is unique and required,
 *                         GET-by-id requests accept either a UUID or this field's value.
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
    Instant updatedAt,
    boolean systemCollection,
    boolean tenantScoped,
    boolean readOnly,
    Set<String> immutableFields,
    Map<String, String> columnMapping,
    String displayFieldName
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
        // Defensive copy for immutableFields and columnMapping
        immutableFields = immutableFields != null ? Set.copyOf(immutableFields) : Set.of();
        columnMapping = columnMapping != null ? Map.copyOf(columnMapping) : Map.of();
    }

    /**
     * Backward-compatible constructor without system collection parameters.
     * Defaults: systemCollection=false, tenantScoped=true, readOnly=false,
     * immutableFields=empty, columnMapping=empty.
     */
    public CollectionDefinition(
            String name, String displayName, String description,
            List<FieldDefinition> fields, StorageConfig storageConfig,
            ApiConfig apiConfig, AuthzConfig authzConfig, EventsConfig eventsConfig,
            long version, Instant createdAt, Instant updatedAt) {
        this(name, displayName, description, fields, storageConfig, apiConfig,
             authzConfig, eventsConfig, version, createdAt, updatedAt,
             false, true, false, Set.of(), Map.of(), null);
    }

    /**
     * Backward-compatible constructor without displayFieldName parameter.
     */
    public CollectionDefinition(
            String name, String displayName, String description,
            List<FieldDefinition> fields, StorageConfig storageConfig,
            ApiConfig apiConfig, AuthzConfig authzConfig, EventsConfig eventsConfig,
            long version, Instant createdAt, Instant updatedAt,
            boolean systemCollection, boolean tenantScoped, boolean readOnly,
            Set<String> immutableFields, Map<String, String> columnMapping) {
        this(name, displayName, description, fields, storageConfig, apiConfig,
             authzConfig, eventsConfig, version, createdAt, updatedAt,
             systemCollection, tenantScoped, readOnly, immutableFields, columnMapping, null);
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
            name, displayName, description, fields,
            storageConfig, apiConfig, authzConfig, eventsConfig,
            version + 1, createdAt, Instant.now(),
            systemCollection, tenantScoped, readOnly,
            immutableFields, columnMapping, displayFieldName
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
            name, displayName, description, newFields,
            storageConfig, apiConfig, authzConfig, eventsConfig,
            version + 1, createdAt, Instant.now(),
            systemCollection, tenantScoped, readOnly,
            immutableFields, columnMapping, displayFieldName
        );
    }

    /**
     * Gets the effective column name for an API field name.
     * Checks the field-level columnName first, then falls back to collection-level
     * columnMapping, then uses the field name as-is.
     *
     * @param fieldName the API field name
     * @return the physical database column name
     */
    public String getEffectiveColumnName(String fieldName) {
        // Check field-level columnName first
        FieldDefinition field = getField(fieldName);
        if (field != null && field.columnName() != null) {
            return field.columnName();
        }
        // Fall back to collection-level columnMapping
        return columnMapping.getOrDefault(fieldName, fieldName);
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
