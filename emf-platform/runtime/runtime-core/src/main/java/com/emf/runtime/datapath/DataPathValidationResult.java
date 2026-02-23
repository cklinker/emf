package com.emf.runtime.datapath;

import com.emf.runtime.model.FieldType;

/**
 * Result of validating a DataPath expression against collection schemas.
 *
 * <p>Used at save time when configuring workflow rules, email templates,
 * or other features that reference DataPath expressions. This validates
 * that the path is structurally sound and all intermediate fields are
 * relationship types.
 *
 * @param valid                 whether the path is valid
 * @param errorMessage          error description, null if valid
 * @param terminalFieldName     the terminal field name (e.g., "email")
 * @param terminalFieldType     the terminal field's type (e.g., STRING)
 * @param terminalCollectionName the collection containing the terminal field (e.g., "customers")
 * @since 1.0.0
 */
public record DataPathValidationResult(
    boolean valid,
    String errorMessage,
    String terminalFieldName,
    FieldType terminalFieldType,
    String terminalCollectionName
) {

    /**
     * Creates a successful validation result.
     */
    public static DataPathValidationResult success(String terminalFieldName,
                                                    FieldType terminalFieldType,
                                                    String terminalCollectionName) {
        return new DataPathValidationResult(true, null,
            terminalFieldName, terminalFieldType, terminalCollectionName);
    }

    /**
     * Creates a failed validation result.
     */
    public static DataPathValidationResult failure(String errorMessage) {
        return new DataPathValidationResult(false, errorMessage, null, null, null);
    }
}
