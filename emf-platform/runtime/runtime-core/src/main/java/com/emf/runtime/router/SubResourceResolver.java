package com.emf.runtime.router;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves sub-resource relationships between parent and child collections.
 *
 * <p>Given a parent collection definition and a child collection definition,
 * finds the child's field that references the parent via its
 * {@link com.emf.runtime.model.ReferenceConfig}.
 *
 * @since 1.0.0
 */
public final class SubResourceResolver {

    private SubResourceResolver() {
        // Utility class
    }

    /**
     * Resolves the sub-resource relationship between a parent and child collection.
     *
     * <p>Iterates over the child collection's fields and finds one where:
     * <ul>
     *   <li>The field type is a relationship ({@code type.isRelationship()} returns true)</li>
     *   <li>The field has a {@code referenceConfig} whose {@code targetCollection} matches
     *       the parent collection name</li>
     * </ul>
     *
     * @param parentDef the parent collection definition
     * @param childDef the child collection definition
     * @return an Optional containing the SubResourceRelation if a relationship is found,
     *         or empty if no matching reference field exists
     */
    public static Optional<SubResourceRelation> resolve(
            CollectionDefinition parentDef,
            CollectionDefinition childDef) {

        for (FieldDefinition field : childDef.fields()) {
            if (field.type().isRelationship()
                    && field.referenceConfig() != null
                    && parentDef.name().equals(field.referenceConfig().targetCollection())) {
                return Optional.of(new SubResourceRelation(
                        field.name(),
                        parentDef,
                        childDef
                ));
            }
        }

        // Also check non-relationship fields that have a referenceConfig
        // (e.g., STRING fields used as foreign keys in system collections)
        for (FieldDefinition field : childDef.fields()) {
            if (!field.type().isRelationship()
                    && field.referenceConfig() != null
                    && parentDef.name().equals(field.referenceConfig().targetCollection())) {
                return Optional.of(new SubResourceRelation(
                        field.name(),
                        parentDef,
                        childDef
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves ALL sub-resource relationships between a parent and child collection.
     *
     * <p>Unlike {@link #resolve(CollectionDefinition, CollectionDefinition)} which returns
     * only the first match, this method returns all matching fields. This is needed when
     * multiple fields reference the same target collection (e.g., both {@code created_by}
     * and {@code updated_by} reference {@code users}).
     *
     * @param parentDef the parent collection definition
     * @param childDef the child collection definition
     * @return a list of SubResourceRelation objects for all matching reference fields
     */
    public static List<SubResourceRelation> resolveAll(
            CollectionDefinition parentDef,
            CollectionDefinition childDef) {

        List<SubResourceRelation> results = new ArrayList<>();

        for (FieldDefinition field : childDef.fields()) {
            if (field.referenceConfig() != null
                    && parentDef.name().equals(field.referenceConfig().targetCollection())) {
                results.add(new SubResourceRelation(
                        field.name(),
                        parentDef,
                        childDef
                ));
            }
        }

        return results;
    }
}
