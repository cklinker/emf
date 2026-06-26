package io.kelta.worker.dependency;

/**
 * The semantic reason one metadata node depends on another.
 *
 * <p>An edge always runs <em>from the dependent to the dependency</em>: {@code from} references,
 * contains, or is otherwise broken by a change to {@code to}.
 *
 * @since 1.0.0
 */
public enum DependencyKind {
    /** A field belongs to its parent collection. */
    FIELD_OF_COLLECTION,
    /** A LOOKUP field references a target collection. */
    LOOKUP,
    /** A MASTER_DETAIL field references its parent collection. */
    MASTER_DETAIL,
    /** A flow action or trigger references a collection. */
    FLOW_REFERENCES_COLLECTION,
    /** A flow invokes another flow as a sub-flow (InvokeFlow). */
    FLOW_INVOKES_FLOW,
    /** A page layout belongs to a collection. */
    LAYOUT_OF_COLLECTION,
    /** A page layout places a field. */
    LAYOUT_USES_FIELD,
    /** A page layout shows a related list backed by another collection. */
    LAYOUT_RELATED_COLLECTION,
    /** A validation rule belongs to a collection. */
    VALIDATION_RULE_OF_COLLECTION,
    /** A record type belongs to a collection. */
    RECORD_TYPE_OF_COLLECTION,
    /** A list view belongs to a collection. */
    LIST_VIEW_OF_COLLECTION,
    /** A unique constraint belongs to a collection. */
    UNIQUE_CONSTRAINT_OF_COLLECTION,
    /** A unique constraint covers a field. */
    UNIQUE_CONSTRAINT_USES_FIELD,
    /** A profile grants object permissions on a collection. */
    OBJECT_PERMISSION,
    /** A profile grants field permissions on a field. */
    FIELD_PERMISSION
}
