package io.kelta.worker.dependency;

/**
 * The kinds of configuration metadata that participate in the dependency graph.
 *
 * @since 1.0.0
 */
public enum MetadataType {
    COLLECTION,
    FIELD,
    FLOW,
    LAYOUT,
    VALIDATION_RULE,
    RECORD_TYPE,
    LIST_VIEW,
    UNIQUE_CONSTRAINT,
    PROFILE
}
