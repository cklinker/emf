package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;

import java.util.Map;
import java.util.Optional;

/**
 * Storage interface for persisting collection data to PostgreSQL physical tables.
 *
 * <p>Each collection maps to a real PostgreSQL table with columns matching field definitions.
 * Implementations must be thread-safe and support concurrent access.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * StorageAdapter adapter = new PhysicalTableStorageAdapter(jdbcTemplate, migrationEngine, objectMapper);
 *
 * // Initialize collection storage
 * adapter.initializeCollection(collectionDefinition);
 *
 * // Create a record
 * Map<String, Object> data = Map.of("name", "Product A", "price", 99.99);
 * Map<String, Object> created = adapter.create(collectionDefinition, data);
 *
 * // Query records
 * QueryResult result = adapter.query(collectionDefinition, queryRequest);
 * }</pre>
 *
 * @see io.kelta.runtime.model.CollectionDefinition
 * @since 1.0.0
 */
public interface StorageAdapter {
    
    /**
     * Initializes storage for a collection.
     * 
     * <p>Creates a database table with columns matching the field definitions.
     * This method is idempotent - calling it multiple times for the same collection
     * should not cause errors if the storage already exists.
     * 
     * @param definition the collection definition containing field definitions and storage config
     * @throws StorageException if storage initialization fails
     */
    void initializeCollection(CollectionDefinition definition);
    
    /**
     * Updates storage schema when a collection definition changes.
     * 
     * <p>Generates and executes appropriate ALTER TABLE statements:
     * <ul>
     *   <li>Added fields result in new columns</li>
     *   <li>Removed fields are marked as deprecated (not dropped)</li>
     *   <li>Type changes are validated for compatibility</li>
     * </ul>
     * 
     * @param oldDefinition the previous collection definition
     * @param newDefinition the new collection definition
     * @throws StorageException if schema update fails
     * @throws IncompatibleSchemaChangeException if a type change is incompatible
     */
    void updateCollectionSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition);
    
    /**
     * Queries records from a collection.
     * 
     * <p>Supports pagination, sorting, filtering, and field selection as specified
     * in the query request.
     * 
     * @param definition the collection definition
     * @param request the query request containing pagination, sorting, filtering, and field selection
     * @return the query result containing matching records and pagination metadata
     * @throws StorageException if the query fails
     */
    QueryResult query(CollectionDefinition definition, QueryRequest request);
    
    /**
     * Gets a single record by its ID.
     * 
     * @param definition the collection definition
     * @param id the record ID
     * @return an Optional containing the record data if found, or empty if not found
     * @throws StorageException if the lookup fails
     */
    Optional<Map<String, Object>> getById(CollectionDefinition definition, String id);
    
    /**
     * Creates a new record in the collection.
     * 
     * <p>System fields (id, createdAt, updatedAt) should already be present in the data map.
     * 
     * @param definition the collection definition
     * @param data the record data to create (including system fields)
     * @return the created record data
     * @throws StorageException if the create operation fails
     * @throws UniqueConstraintViolationException if a unique constraint is violated
     */
    Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data);
    
    /**
     * Updates an existing record in the collection.
     * 
     * <p>Only the fields present in the data map are updated.
     * 
     * @param definition the collection definition
     * @param id the record ID to update
     * @param data the record data to update
     * @return an Optional containing the updated record if found, or empty if not found
     * @throws StorageException if the update operation fails
     * @throws UniqueConstraintViolationException if a unique constraint is violated
     */
    Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data);
    
    /**
     * Deletes a record from the collection.
     * 
     * @param definition the collection definition
     * @param id the record ID to delete
     * @return true if the record was deleted, false if it was not found
     * @throws StorageException if the delete operation fails
     */
    boolean delete(CollectionDefinition definition, String id);
    
    /**
     * Checks if a value is unique for a field in the collection.
     * 
     * <p>This method is used by the validation engine to enforce unique constraints.
     * When updating a record, the excludeId parameter should be set to the record's
     * own ID to avoid false positives.
     * 
     * @param definition the collection definition
     * @param fieldName the field name to check uniqueness for
     * @param value the value to check
     * @param excludeId optional record ID to exclude from the check (for updates), may be null
     * @return true if the value is unique (not found in other records), false otherwise
     * @throws StorageException if the uniqueness check fails
     */
    boolean isUnique(CollectionDefinition definition, String fieldName, Object value, String excludeId);
}
