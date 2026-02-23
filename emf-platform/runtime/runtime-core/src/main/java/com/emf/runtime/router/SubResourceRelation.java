package com.emf.runtime.router;

import com.emf.runtime.model.CollectionDefinition;

import java.util.Objects;

/**
 * Describes the relationship between a parent and child collection
 * for sub-resource routing.
 *
 * <p>Holds the child field name that references the parent collection,
 * along with both collection definitions.
 *
 * @param parentRefFieldName the field on the child collection that references the parent (e.g., "workflowRuleId")
 * @param parentDef the parent collection definition
 * @param childDef the child collection definition
 *
 * @since 1.0.0
 */
public record SubResourceRelation(
    String parentRefFieldName,
    CollectionDefinition parentDef,
    CollectionDefinition childDef
) {
    /**
     * Compact constructor with validation.
     */
    public SubResourceRelation {
        Objects.requireNonNull(parentRefFieldName, "parentRefFieldName cannot be null");
        if (parentRefFieldName.isBlank()) {
            throw new IllegalArgumentException("parentRefFieldName cannot be blank");
        }
        Objects.requireNonNull(parentDef, "parentDef cannot be null");
        Objects.requireNonNull(childDef, "childDef cannot be null");
    }
}
