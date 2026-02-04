package com.emf.runtime.validation;

import com.emf.runtime.model.CollectionDefinition;

import java.util.Map;

/**
 * Validation engine interface for field-level validation based on collection definitions.
 * 
 * <p>The validation engine enforces data integrity by validating input data against
 * the constraints defined in the collection definition. It supports:
 * <ul>
 *   <li><b>Nullable constraint</b> - reject null values for non-nullable fields</li>
 *   <li><b>Type validation</b> - value matches expected FieldType</li>
 *   <li><b>Min/Max value</b> - for numeric fields</li>
 *   <li><b>Min/Max length</b> - for string fields</li>
 *   <li><b>Pattern</b> - regex validation for strings</li>
 *   <li><b>Immutable</b> - reject updates to immutable fields</li>
 *   <li><b>Unique</b> - check via StorageAdapter.isUnique()</li>
 *   <li><b>Enum</b> - value must be in enumValues list</li>
 *   <li><b>Reference</b> - referenced record must exist</li>
 * </ul>
 * 
 * <p>Implementations must be stateless and thread-safe to support concurrent validation
 * operations without shared mutable state.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ValidationEngine engine = new DefaultValidationEngine(storageAdapter, registry);
 * 
 * Map<String, Object> data = Map.of("name", "Product A", "price", 99.99);
 * ValidationResult result = engine.validate(collectionDefinition, data, OperationType.CREATE);
 * 
 * if (!result.valid()) {
 *     // Handle validation errors
 *     return ResponseEntity.badRequest().body(result.toErrorResponse());
 * }
 * }</pre>
 * 
 * @see ValidationResult
 * @see OperationType
 * @see FieldError
 * @since 1.0.0
 */
public interface ValidationEngine {
    
    /**
     * Validates the given data against the collection definition.
     * 
     * <p>The validation behavior differs based on the operation type:
     * <ul>
     *   <li><b>CREATE</b> - All non-nullable fields must be provided</li>
     *   <li><b>UPDATE</b> - Immutable fields cannot be modified; only provided fields are validated</li>
     * </ul>
     * 
     * <p>For UPDATE operations, the data map should contain an "id" field to identify
     * the record being updated. This is used for unique constraint validation to
     * exclude the current record from uniqueness checks.
     * 
     * @param definition the collection definition containing field definitions and constraints
     * @param data the data to validate (field name to value mapping)
     * @param operationType the type of operation (CREATE or UPDATE)
     * @return a ValidationResult indicating success or containing field-level errors
     * @throws NullPointerException if definition, data, or operationType is null
     */
    ValidationResult validate(CollectionDefinition definition, Map<String, Object> data, OperationType operationType);
    
    /**
     * Validates the given data against the collection definition with an explicit exclude ID.
     * 
     * <p>This overload is useful for UPDATE operations where the record ID is known
     * but not included in the data map.
     * 
     * @param definition the collection definition containing field definitions and constraints
     * @param data the data to validate (field name to value mapping)
     * @param operationType the type of operation (CREATE or UPDATE)
     * @param excludeId the record ID to exclude from uniqueness checks (for updates)
     * @return a ValidationResult indicating success or containing field-level errors
     * @throws NullPointerException if definition, data, or operationType is null
     */
    default ValidationResult validate(CollectionDefinition definition, Map<String, Object> data, 
                                      OperationType operationType, String excludeId) {
        // Default implementation ignores excludeId and delegates to the main method
        return validate(definition, data, operationType);
    }
}
