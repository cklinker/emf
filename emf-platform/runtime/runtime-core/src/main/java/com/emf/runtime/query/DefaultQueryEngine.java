package com.emf.runtime.query;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.OperationType;
import com.emf.runtime.validation.ValidationEngine;
import com.emf.runtime.validation.ValidationException;
import com.emf.runtime.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Default implementation of the QueryEngine interface.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Validates query parameters against collection definitions</li>
 *   <li>Integrates with StorageAdapter for persistence</li>
 *   <li>Integrates with ValidationEngine for data validation</li>
 *   <li>Adds system fields (id, createdAt, updatedAt) automatically</li>
 *   <li>Logs query performance metrics</li>
 * </ul>
 * 
 * <p>Thread Safety: This class is thread-safe. All operations delegate to
 * thread-safe components (StorageAdapter, ValidationEngine).
 * 
 * @since 1.0.0
 */
public class DefaultQueryEngine implements QueryEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultQueryEngine.class);
    
    private static final Set<String> SYSTEM_FIELDS = Set.of("id", "createdAt", "updatedAt");
    
    private final StorageAdapter storageAdapter;
    private final ValidationEngine validationEngine;
    
    /**
     * Creates a new DefaultQueryEngine.
     * 
     * @param storageAdapter the storage adapter for persistence
     * @param validationEngine the validation engine for data validation (may be null)
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter, ValidationEngine validationEngine) {
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter cannot be null");
        this.validationEngine = validationEngine;
    }
    
    /**
     * Creates a new DefaultQueryEngine without validation.
     * 
     * @param storageAdapter the storage adapter for persistence
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter) {
        this(storageAdapter, null);
    }
    
    @Override
    public QueryResult executeQuery(CollectionDefinition definition, QueryRequest request) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        
        long startTime = System.currentTimeMillis();
        
        // Validate sort fields exist
        validateSortFields(definition, request.sorting());
        
        // Validate filter fields exist
        validateFilterFields(definition, request.filters());
        
        // Validate requested fields exist
        validateRequestedFields(definition, request.fields());
        
        // Execute query via storage adapter
        QueryResult result = storageAdapter.query(definition, request);
        
        // Log query performance
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Query executed on collection '{}': {} records returned in {}ms",
            definition.name(), result.size(), duration);
        
        return result;
    }
    
    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        
        return storageAdapter.getById(definition, id);
    }
    
    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        
        // Create mutable copy
        Map<String, Object> recordData = new HashMap<>(data);
        
        // Add system fields
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        recordData.put("id", id);
        recordData.put("createdAt", now);
        recordData.put("updatedAt", now);
        
        // Validate data
        if (validationEngine != null) {
            ValidationResult validation = validationEngine.validate(definition, recordData, OperationType.CREATE);
            if (!validation.valid()) {
                throw new ValidationException(validation);
            }
        }
        
        // Persist via storage adapter
        Map<String, Object> created = storageAdapter.create(definition, recordData);
        
        logger.debug("Created record '{}' in collection '{}'", id, definition.name());
        
        return created;
    }

    @Override
    public Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        
        // Check if record exists
        Optional<Map<String, Object>> existing = storageAdapter.getById(definition, id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        
        // Create mutable copy
        Map<String, Object> recordData = new HashMap<>(data);
        
        // Update timestamp
        recordData.put("updatedAt", Instant.now());
        
        // Don't allow changing id or createdAt
        recordData.remove("id");
        recordData.remove("createdAt");
        
        // Validate data
        if (validationEngine != null) {
            // Merge with existing data for validation
            Map<String, Object> mergedData = new HashMap<>(existing.get());
            mergedData.putAll(recordData);
            
            ValidationResult validation = validationEngine.validate(definition, mergedData, OperationType.UPDATE, id);
            if (!validation.valid()) {
                throw new ValidationException(validation);
            }
        }
        
        // Persist via storage adapter
        Optional<Map<String, Object>> updated = storageAdapter.update(definition, id, recordData);
        
        updated.ifPresent(record -> 
            logger.debug("Updated record '{}' in collection '{}'", id, definition.name()));
        
        return updated;
    }
    
    @Override
    public boolean delete(CollectionDefinition definition, String id) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        
        boolean deleted = storageAdapter.delete(definition, id);
        
        if (deleted) {
            logger.debug("Deleted record '{}' from collection '{}'", id, definition.name());
        }
        
        return deleted;
    }
    
    /**
     * Validates that all sort fields exist in the collection definition.
     */
    private void validateSortFields(CollectionDefinition definition, List<SortField> sorting) {
        for (SortField sortField : sorting) {
            if (!isValidField(definition, sortField.fieldName())) {
                throw new InvalidQueryException(sortField.fieldName(), 
                    "Sort field does not exist in collection '" + definition.name() + "'");
            }
        }
    }
    
    /**
     * Validates that all filter fields exist in the collection definition.
     */
    private void validateFilterFields(CollectionDefinition definition, List<FilterCondition> filters) {
        for (FilterCondition filter : filters) {
            if (!isValidField(definition, filter.fieldName())) {
                throw new InvalidQueryException(filter.fieldName(),
                    "Filter field does not exist in collection '" + definition.name() + "'");
            }
        }
    }
    
    /**
     * Validates that all requested fields exist in the collection definition.
     */
    private void validateRequestedFields(CollectionDefinition definition, List<String> fields) {
        for (String field : fields) {
            if (!isValidField(definition, field)) {
                throw new InvalidQueryException(field,
                    "Requested field does not exist in collection '" + definition.name() + "'");
            }
        }
    }
    
    /**
     * Checks if a field name is valid for the collection.
     * System fields (id, createdAt, updatedAt) are always valid.
     */
    private boolean isValidField(CollectionDefinition definition, String fieldName) {
        if (SYSTEM_FIELDS.contains(fieldName)) {
            return true;
        }
        return definition.getField(fieldName) != null;
    }
}
