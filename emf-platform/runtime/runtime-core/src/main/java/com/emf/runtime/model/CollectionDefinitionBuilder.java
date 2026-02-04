package com.emf.runtime.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder for constructing {@link CollectionDefinition} instances.
 * 
 * <p>Provides a fluent API with method chaining for building collection definitions.
 * Required fields (name and at least one field) must be set before calling {@link #build()}.
 * 
 * <p>Sensible defaults are applied for optional configuration:
 * <ul>
 *   <li>Storage: Mode A (Physical Tables) with table name "tbl_{name}"</li>
 *   <li>API: All operations enabled</li>
 *   <li>Authorization: Disabled</li>
 *   <li>Events: Disabled</li>
 *   <li>Version: 1</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * CollectionDefinition collection = CollectionDefinition.builder()
 *     .name("products")
 *     .displayName("Products")
 *     .description("Product catalog")
 *     .addField(FieldDefinition.requiredString("sku"))
 *     .addField(FieldDefinition.requiredString("name"))
 *     .addField(FieldDefinition.doubleField("price"))
 *     .storageConfig(StorageConfig.physicalTable("tbl_products"))
 *     .build();
 * }</pre>
 * 
 * @since 1.0.0
 */
public class CollectionDefinitionBuilder {
    
    private String name;
    private String displayName;
    private String description;
    private List<FieldDefinition> fields = new ArrayList<>();
    private StorageConfig storageConfig;
    private ApiConfig apiConfig;
    private AuthzConfig authzConfig;
    private EventsConfig eventsConfig;
    private long version = 1L;
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * Creates a new collection definition builder.
     */
    public CollectionDefinitionBuilder() {
    }
    
    /**
     * Sets the collection name.
     * 
     * @param name the collection name (required)
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    /**
     * Sets the display name.
     * 
     * @param displayName the human-readable display name
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }
    
    /**
     * Sets the description.
     * 
     * @param description the collection description
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder description(String description) {
        this.description = description;
        return this;
    }
    
    /**
     * Adds a field definition to the collection.
     * 
     * @param field the field definition to add
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder addField(FieldDefinition field) {
        this.fields.add(field);
        return this;
    }
    
    /**
     * Sets all field definitions for the collection.
     * 
     * @param fields the list of field definitions
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder fields(List<FieldDefinition> fields) {
        this.fields = new ArrayList<>(fields);
        return this;
    }
    
    /**
     * Sets the storage configuration.
     * 
     * @param storageConfig the storage configuration
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder storageConfig(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
        return this;
    }
    
    /**
     * Sets the API configuration.
     * 
     * @param apiConfig the API configuration
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder apiConfig(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        return this;
    }
    
    /**
     * Sets the authorization configuration.
     * 
     * @param authzConfig the authorization configuration
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder authzConfig(AuthzConfig authzConfig) {
        this.authzConfig = authzConfig;
        return this;
    }
    
    /**
     * Sets the events configuration.
     * 
     * @param eventsConfig the events configuration
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder eventsConfig(EventsConfig eventsConfig) {
        this.eventsConfig = eventsConfig;
        return this;
    }
    
    /**
     * Sets the version number.
     * 
     * @param version the version number
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder version(long version) {
        this.version = version;
        return this;
    }
    
    /**
     * Sets the creation timestamp.
     * 
     * @param createdAt the creation timestamp
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    /**
     * Sets the last update timestamp.
     * 
     * @param updatedAt the last update timestamp
     * @return this builder for method chaining
     */
    public CollectionDefinitionBuilder updatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
    
    /**
     * Builds the collection definition.
     * 
     * <p>Applies sensible defaults for optional configuration:
     * <ul>
     *   <li>Storage: Mode A (Physical Tables) with table name "tbl_{name}"</li>
     *   <li>API: All operations enabled at "/api/collections/{name}"</li>
     *   <li>Authorization: Disabled</li>
     *   <li>Events: Disabled</li>
     *   <li>Timestamps: Current time if not set</li>
     * </ul>
     * 
     * @return the constructed collection definition
     * @throws IllegalStateException if required fields (name, fields) are not set
     */
    public CollectionDefinition build() {
        // Validate required fields
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Collection name is required");
        }
        if (fields.isEmpty()) {
            throw new IllegalStateException("At least one field is required");
        }
        
        // Apply defaults
        if (storageConfig == null) {
            storageConfig = new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_" + name, Map.of());
        }
        if (apiConfig == null) {
            apiConfig = ApiConfig.allEnabled("/api/collections/" + name);
        }
        if (authzConfig == null) {
            authzConfig = AuthzConfig.disabled();
        }
        if (eventsConfig == null) {
            eventsConfig = EventsConfig.disabled();
        }
        
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        
        return new CollectionDefinition(
            name,
            displayName != null ? displayName : name,
            description,
            fields,
            storageConfig,
            apiConfig,
            authzConfig,
            eventsConfig,
            version,
            createdAt,
            updatedAt
        );
    }
}
