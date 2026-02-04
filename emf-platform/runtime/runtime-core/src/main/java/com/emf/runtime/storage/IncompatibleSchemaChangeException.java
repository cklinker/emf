package com.emf.runtime.storage;

import com.emf.runtime.model.FieldType;

/**
 * Exception thrown when a schema change is incompatible and cannot be applied.
 * 
 * <p>This exception is thrown when attempting to change a field's type to an
 * incompatible type that would result in data loss or corruption.
 * 
 * @since 1.0.0
 */
public class IncompatibleSchemaChangeException extends StorageException {
    
    private final String collectionName;
    private final String fieldName;
    private final FieldType oldType;
    private final FieldType newType;
    
    /**
     * Creates a new IncompatibleSchemaChangeException.
     * 
     * @param collectionName the collection where the change was attempted
     * @param fieldName the field being changed
     * @param oldType the current field type
     * @param newType the requested new field type
     */
    public IncompatibleSchemaChangeException(String collectionName, String fieldName, 
            FieldType oldType, FieldType newType) {
        super(String.format(
            "Incompatible schema change: cannot change field '%s' in collection '%s' from %s to %s",
            fieldName, collectionName, oldType, newType));
        this.collectionName = collectionName;
        this.fieldName = fieldName;
        this.oldType = oldType;
        this.newType = newType;
    }
    
    /**
     * Creates a new IncompatibleSchemaChangeException with a custom message.
     * 
     * @param collectionName the collection where the change was attempted
     * @param fieldName the field being changed
     * @param oldType the current field type
     * @param newType the requested new field type
     * @param reason additional reason for the incompatibility
     */
    public IncompatibleSchemaChangeException(String collectionName, String fieldName,
            FieldType oldType, FieldType newType, String reason) {
        super(String.format(
            "Incompatible schema change: cannot change field '%s' in collection '%s' from %s to %s. Reason: %s",
            fieldName, collectionName, oldType, newType, reason));
        this.collectionName = collectionName;
        this.fieldName = fieldName;
        this.oldType = oldType;
        this.newType = newType;
    }
    
    /**
     * Gets the collection name where the change was attempted.
     * 
     * @return the collection name
     */
    public String getCollectionName() {
        return collectionName;
    }
    
    /**
     * Gets the field name being changed.
     * 
     * @return the field name
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Gets the current field type.
     * 
     * @return the old field type
     */
    public FieldType getOldType() {
        return oldType;
    }
    
    /**
     * Gets the requested new field type.
     * 
     * @return the new field type
     */
    public FieldType getNewType() {
        return newType;
    }
}
