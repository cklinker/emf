package com.emf.runtime.datapath;

import java.util.Objects;

/**
 * A single segment in a data path traversal.
 *
 * <p>Each segment represents one step in navigating through collection
 * relationships to reach a terminal field value. Segments can be:
 * <ul>
 *   <li>{@code FIELD} — a terminal field reference (the final value to resolve)</li>
 *   <li>{@code RELATIONSHIP} — a traversal hop through a LOOKUP or MASTER_DETAIL field</li>
 * </ul>
 *
 * @param fieldName the field name in the current collection (e.g., "order_id", "email")
 * @param type      the segment type determining how to resolve this step
 * @since 1.0.0
 */
public record DataPathSegment(
    String fieldName,
    DataPathSegmentType type
) {

    /**
     * Constructs a DataPathSegment with validation.
     */
    public DataPathSegment {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName cannot be blank");
        }
        Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Creates a FIELD segment (terminal value).
     *
     * @param fieldName the field name
     * @return a new terminal segment
     */
    public static DataPathSegment field(String fieldName) {
        return new DataPathSegment(fieldName, DataPathSegmentType.FIELD);
    }

    /**
     * Creates a RELATIONSHIP segment (traversal hop).
     *
     * @param fieldName the relationship field name (LOOKUP or MASTER_DETAIL)
     * @return a new relationship segment
     */
    public static DataPathSegment relationship(String fieldName) {
        return new DataPathSegment(fieldName, DataPathSegmentType.RELATIONSHIP);
    }

    /**
     * Enum representing the type of a data path segment.
     */
    public enum DataPathSegmentType {
        /**
         * Terminal field — the final value to read from the record.
         */
        FIELD,

        /**
         * Relationship traversal — follow a LOOKUP or MASTER_DETAIL field
         * to reach the target collection, then continue resolution.
         */
        RELATIONSHIP
    }
}
