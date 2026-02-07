package com.emf.runtime.model;

import java.util.Objects;

/**
 * Reference configuration for a field that references another collection.
 *
 * <p>Defines a foreign key relationship between collections, including
 * relationship type metadata for LOOKUP and MASTER_DETAIL relationships.
 *
 * @param targetCollection Name of the referenced collection
 * @param targetField Field in the target collection being referenced (typically "id")
 * @param cascadeDelete Whether to cascade delete operations to referencing records
 * @param relationshipType Type of relationship: "LOOKUP", "MASTER_DETAIL", or null for generic REFERENCE
 * @param relationshipName Human-readable relationship name (e.g., "Account" for an account_id field)
 *
 * @since 1.0.0
 */
public record ReferenceConfig(
    String targetCollection,
    String targetField,
    boolean cascadeDelete,
    String relationshipType,
    String relationshipName
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
     * Backward-compatible constructor without relationship metadata.
     */
    public ReferenceConfig(String targetCollection, String targetField, boolean cascadeDelete) {
        this(targetCollection, targetField, cascadeDelete, null, null);
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

    /**
     * Creates a LOOKUP relationship configuration.
     * Lookup relationships use ON DELETE SET NULL and are nullable by default.
     *
     * @param targetCollection the target collection name
     * @param relationshipName human-readable name for the relationship
     * @return lookup reference configuration
     */
    public static ReferenceConfig lookup(String targetCollection, String relationshipName) {
        return new ReferenceConfig(targetCollection, "id", false, "LOOKUP", relationshipName);
    }

    /**
     * Creates a MASTER_DETAIL relationship configuration.
     * Master-detail relationships use ON DELETE CASCADE and are always required.
     *
     * @param targetCollection the target collection name
     * @param relationshipName human-readable name for the relationship
     * @return master-detail reference configuration
     */
    public static ReferenceConfig masterDetail(String targetCollection, String relationshipName) {
        return new ReferenceConfig(targetCollection, "id", true, "MASTER_DETAIL", relationshipName);
    }

    /**
     * Returns true if this is a LOOKUP relationship.
     */
    public boolean isLookup() {
        return "LOOKUP".equals(relationshipType);
    }

    /**
     * Returns true if this is a MASTER_DETAIL relationship.
     */
    public boolean isMasterDetail() {
        return "MASTER_DETAIL".equals(relationshipType);
    }
}
