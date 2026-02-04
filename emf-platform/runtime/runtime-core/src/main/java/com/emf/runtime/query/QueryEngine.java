package com.emf.runtime.query;

import com.emf.runtime.model.CollectionDefinition;

import java.util.Map;
import java.util.Optional;

/**
 * Query engine interface for executing queries and CRUD operations against collections.
 * 
 * <p>The query engine provides a high-level API for:
 * <ul>
 *   <li>Executing queries with pagination, sorting, filtering, and field selection</li>
 *   <li>CRUD operations (create, read, update, delete)</li>
 *   <li>Validation of query parameters against collection definitions</li>
 * </ul>
 * 
 * <p>Implementations integrate with:
 * <ul>
 *   <li>{@link com.emf.runtime.storage.StorageAdapter} for persistence</li>
 *   <li>{@link com.emf.runtime.validation.ValidationEngine} for data validation</li>
 *   <li>Event publishers for lifecycle events</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * QueryEngine engine = new DefaultQueryEngine(storageAdapter, validationEngine, eventPublisher);
 * 
 * // Execute a query
 * QueryRequest request = QueryRequest.fromParams(params);
 * QueryResult result = engine.executeQuery(collectionDefinition, request);
 * 
 * // Create a record
 * Map<String, Object> data = Map.of("name", "Product A", "price", 99.99);
 * Map<String, Object> created = engine.create(collectionDefinition, data);
 * }</pre>
 * 
 * @see QueryRequest
 * @see QueryResult
 * @see com.emf.runtime.storage.StorageAdapter
 * @since 1.0.0
 */
public interface QueryEngine {
    
    /**
     * Executes a query against a collection.
     * 
     * <p>This method validates the query parameters (sort fields, filter fields,
     * requested fields) against the collection definition before executing.
     * 
     * @param definition the collection definition
     * @param request the query request containing pagination, sorting, filtering, and field selection
     * @return the query result containing matching records and pagination metadata
     * @throws InvalidQueryException if query parameters reference non-existent fields
     * @throws com.emf.runtime.storage.StorageException if the query fails
     */
    QueryResult executeQuery(CollectionDefinition definition, QueryRequest request);
    
    /**
     * Gets a single record by its ID.
     * 
     * @param definition the collection definition
     * @param id the record ID
     * @return an Optional containing the record data if found, or empty if not found
     * @throws com.emf.runtime.storage.StorageException if the lookup fails
     */
    Optional<Map<String, Object>> getById(CollectionDefinition definition, String id);
    
    /**
     * Creates a new record in the collection.
     * 
     * <p>This method:
     * <ol>
     *   <li>Validates the data against the collection definition</li>
     *   <li>Adds system fields (id, createdAt, updatedAt)</li>
     *   <li>Persists the record via the storage adapter</li>
     *   <li>Publishes a create event</li>
     * </ol>
     * 
     * @param definition the collection definition
     * @param data the record data to create
     * @return the created record data including system fields
     * @throws com.emf.runtime.validation.ValidationException if validation fails
     * @throws com.emf.runtime.storage.StorageException if the create operation fails
     * @throws com.emf.runtime.storage.UniqueConstraintViolationException if a unique constraint is violated
     */
    Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data);
    
    /**
     * Updates an existing record in the collection.
     * 
     * <p>This method:
     * <ol>
     *   <li>Validates the data against the collection definition</li>
     *   <li>Updates the updatedAt timestamp</li>
     *   <li>Persists the changes via the storage adapter</li>
     *   <li>Publishes an update event</li>
     * </ol>
     * 
     * @param definition the collection definition
     * @param id the record ID to update
     * @param data the record data to update
     * @return an Optional containing the updated record if found, or empty if not found
     * @throws com.emf.runtime.validation.ValidationException if validation fails
     * @throws com.emf.runtime.storage.StorageException if the update operation fails
     * @throws com.emf.runtime.storage.UniqueConstraintViolationException if a unique constraint is violated
     */
    Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data);
    
    /**
     * Deletes a record from the collection.
     * 
     * <p>This method:
     * <ol>
     *   <li>Deletes the record via the storage adapter</li>
     *   <li>Publishes a delete event if successful</li>
     * </ol>
     * 
     * @param definition the collection definition
     * @param id the record ID to delete
     * @return true if the record was deleted, false if it was not found
     * @throws com.emf.runtime.storage.StorageException if the delete operation fails
     */
    boolean delete(CollectionDefinition definition, String id);
}
