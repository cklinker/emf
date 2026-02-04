package com.emf.runtime.validation;

/**
 * Type of operation being validated.
 * 
 * <p>Used by the validation engine to apply different validation rules
 * based on whether a record is being created or updated.
 * 
 * @since 1.0.0
 */
public enum OperationType {
    /**
     * Creating a new record.
     * All non-nullable fields must be provided.
     */
    CREATE,
    
    /**
     * Updating an existing record.
     * Immutable fields cannot be modified.
     */
    UPDATE
}
