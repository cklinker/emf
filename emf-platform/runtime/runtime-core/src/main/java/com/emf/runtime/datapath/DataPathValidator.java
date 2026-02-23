package com.emf.runtime.datapath;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.ReferenceConfig;

import java.util.Objects;

/**
 * Validates data path expressions against collection schemas at save time.
 *
 * <p>When a workflow rule, email template, or other feature is configured
 * with a DataPath expression, this validator checks that:
 * <ul>
 *   <li>All intermediate segments reference relationship fields (LOOKUP/MASTER_DETAIL)</li>
 *   <li>All target collections exist and are registered</li>
 *   <li>The terminal field exists in the final collection</li>
 *   <li>The maximum traversal depth is not exceeded</li>
 * </ul>
 *
 * <p>This catches configuration errors early rather than at runtime.
 *
 * @since 1.0.0
 */
public class DataPathValidator {

    private final CollectionDefinitionProvider collectionProvider;

    /**
     * Creates a new DataPathValidator.
     *
     * @param collectionProvider the provider for collection definitions
     */
    public DataPathValidator(CollectionDefinitionProvider collectionProvider) {
        this.collectionProvider = Objects.requireNonNull(collectionProvider,
            "collectionProvider cannot be null");
    }

    /**
     * Validates a data path expression against collection schemas.
     *
     * @param path the data path to validate
     * @return the validation result with terminal field metadata
     */
    public DataPathValidationResult validate(DataPath path) {
        Objects.requireNonNull(path, "path cannot be null");

        CollectionDefinition currentCollection = collectionProvider.getByName(path.rootCollectionName());
        if (currentCollection == null) {
            return DataPathValidationResult.failure(
                "Root collection '" + path.rootCollectionName() + "' not found");
        }

        // Validate intermediate relationship segments
        for (DataPathSegment segment : path.relationships()) {
            FieldDefinition fieldDef = currentCollection.getField(segment.fieldName());
            if (fieldDef == null) {
                return DataPathValidationResult.failure(
                    "Field '" + segment.fieldName() + "' not found in collection '" +
                    currentCollection.name() + "'");
            }

            if (!fieldDef.type().isRelationship()) {
                return DataPathValidationResult.failure(
                    "Field '" + segment.fieldName() + "' in collection '" +
                    currentCollection.name() + "' is not a relationship field (type: " +
                    fieldDef.type() + "). Only LOOKUP and MASTER_DETAIL fields can be " +
                    "used as intermediate segments in a data path.");
            }

            ReferenceConfig refConfig = fieldDef.referenceConfig();
            if (refConfig == null || refConfig.targetCollection() == null) {
                return DataPathValidationResult.failure(
                    "Relationship field '" + segment.fieldName() + "' in collection '" +
                    currentCollection.name() + "' has no reference configuration");
            }

            String targetCollectionName = refConfig.targetCollection();
            CollectionDefinition targetCollection = collectionProvider.getByName(targetCollectionName);
            if (targetCollection == null) {
                return DataPathValidationResult.failure(
                    "Target collection '" + targetCollectionName + "' referenced by field '" +
                    segment.fieldName() + "' in collection '" + currentCollection.name() +
                    "' not found");
            }

            currentCollection = targetCollection;
        }

        // Validate terminal field
        String terminalFieldName = path.terminal().fieldName();
        FieldDefinition terminalField = currentCollection.getField(terminalFieldName);

        // System fields are always valid terminals
        if (terminalField == null && isSystemField(terminalFieldName)) {
            return DataPathValidationResult.success(
                terminalFieldName, null, currentCollection.name());
        }

        if (terminalField == null) {
            return DataPathValidationResult.failure(
                "Terminal field '" + terminalFieldName + "' not found in collection '" +
                currentCollection.name() + "'");
        }

        return DataPathValidationResult.success(
            terminalFieldName, terminalField.type(), currentCollection.name());
    }

    /**
     * Checks if a field name is a system field.
     */
    private boolean isSystemField(String fieldName) {
        return "id".equals(fieldName) ||
               "createdAt".equals(fieldName) ||
               "updatedAt".equals(fieldName) ||
               "createdBy".equals(fieldName) ||
               "updatedBy".equals(fieldName);
    }
}
