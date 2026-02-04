package com.emf.runtime.model;

import java.util.Objects;

/**
 * Reference configuration for a field that references another collection.
 * 
 * <p>Defines a foreign key relationship between collections.
 * 
 * @param targetCollection Name of the referenced collection
 * @param targetField Field in the target collection being referenced (typically "id")
 * @param cascadeDelete Whether to cascade delete operations to referencing records
 * 
 * @since 1.0.0
 */
public record ReferenceConfig(
    String targetCollection,
    String targetField,
    boolean cascadeDelete
) {
    /**
     * Compact constructor with validation.
     */
    public ReferenceConfig {
        Objects.requireNonNull(targetCollection, "targetCollection cannot be null");
        if (targetField == null || targetField.isBlank()) {
            targetField = "id";
        }
    }
    
    /**
     * Creates a reference configuration to the ID field of another collection.
     * 
     * @param targetCollection the target collection name
     * @return reference configuration
     */
    public static ReferenceConfig toCollection(String targetCollection) {
        return new ReferenceConfig(targetCollection, "id", false);
    }
    
    /**
     * Creates a reference configuration with cascade delete enabled.
     * 
     * @param targetCollection the target collection name
     * @return reference configuration with cascade delete
     */
    public static ReferenceConfig toCollectionWithCascade(String targetCollection) {
        return new ReferenceConfig(targetCollection, "id", true);
    }
    
    /**
     * Creates a reference configuration to a specific field in another collection.
     * 
     * @param targetCollection the target collection name
     * @param targetField the target field name
     * @param cascadeDelete whether to cascade deletes
     * @return reference configuration
     */
    public static ReferenceConfig toField(String targetCollection, String targetField, boolean cascadeDelete) {
        return new ReferenceConfig(targetCollection, targetField, cascadeDelete);
    }
}
