package com.emf.runtime.storage;

/**
 * Exception thrown when a unique constraint is violated during a storage operation.
 * 
 * <p>This exception is thrown when attempting to create or update a record with
 * a value that already exists for a field marked as unique.
 * 
 * @since 1.0.0
 */
public class UniqueConstraintViolationException extends StorageException {
    
    private final String collectionName;
    private final String fieldName;
    private final Object value;
    
    /**
     * Creates a new UniqueConstraintViolationException.
     * 
     * @param collectionName the collection where the violation occurred
     * @param fieldName the field with the unique constraint
     * @param value the duplicate value
     */
    public UniqueConstraintViolationException(String collectionName, String fieldName, Object value) {
        super(String.format("Unique constraint violation: field '%s' in collection '%s' already has value '%s'",
            fieldName, collectionName, value));
        this.collectionName = collectionName;
        this.fieldName = fieldName;
        this.value = value;
    }
    
    /**
     * Creates a new UniqueConstraintViolationException with a cause.
     * 
     * @param collectionName the collection where the violation occurred
     * @param fieldName the field with the unique constraint
     * @param value the duplicate value
     * @param cause the underlying cause
     */
    public UniqueConstraintViolationException(String collectionName, String fieldName, Object value, Throwable cause) {
        super(String.format("Unique constraint violation: field '%s' in collection '%s' already has value '%s'",
            fieldName, collectionName, value), cause);
        this.collectionName = collectionName;
        this.fieldName = fieldName;
        this.value = value;
    }
    
    /**
     * Gets the collection name where the violation occurred.
     * 
     * @return the collection name
     */
    public String getCollectionName() {
        return collectionName;
    }
    
    /**
     * Gets the field name with the unique constraint.
     * 
     * @return the field name
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Gets the duplicate value that caused the violation.
     * 
     * @return the duplicate value
     */
    public Object getValue() {
        return value;
    }
}
