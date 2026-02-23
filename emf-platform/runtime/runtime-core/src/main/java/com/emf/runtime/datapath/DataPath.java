package com.emf.runtime.datapath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A complete path from a root collection to a terminal field value.
 *
 * <p>Data paths enable cross-collection field resolution by expressing a series
 * of relationship traversals followed by a terminal field read. For example,
 * the expression {@code "order_id.customer_id.email"} means:
 * <ol>
 *   <li>Follow the {@code order_id} LOOKUP to the orders collection</li>
 *   <li>Follow the {@code customer_id} LOOKUP to the customers collection</li>
 *   <li>Read the {@code email} field value</li>
 * </ol>
 *
 * <p>A simple field reference like {@code "name"} is a single-segment path with
 * no traversal — it reads the field directly from the source record.
 *
 * <p>Stored as a dot-separated string in JSONB config columns. Compact,
 * human-readable, and easy to serialize/deserialize.
 *
 * @param rootCollectionName the starting collection name
 * @param segments           ordered list of segments (relationship hops + terminal field)
 * @param expression         the original dot-notation string (e.g., "order_id.customer_id.email")
 * @since 1.0.0
 */
public record DataPath(
    String rootCollectionName,
    List<DataPathSegment> segments,
    String expression
) {

    /** Maximum allowed traversal depth to prevent runaway paths. */
    public static final int MAX_DEPTH = 10;

    /**
     * Constructs a DataPath with validation.
     */
    public DataPath {
        Objects.requireNonNull(rootCollectionName, "rootCollectionName cannot be null");
        if (rootCollectionName.isBlank()) {
            throw new IllegalArgumentException("rootCollectionName cannot be blank");
        }
        Objects.requireNonNull(segments, "segments cannot be null");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("segments cannot be empty");
        }
        segments = List.copyOf(segments); // defensive immutable copy
        Objects.requireNonNull(expression, "expression cannot be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression cannot be blank");
        }
    }

    /**
     * Parses a dot-notation expression into a DataPath.
     *
     * <p>All segments except the last are treated as RELATIONSHIP segments;
     * the last segment is always a FIELD (terminal) segment.
     *
     * @param expression         the dot-notation expression (e.g., "order_id.customer_id.email")
     * @param rootCollectionName the starting collection name
     * @return the parsed DataPath
     * @throws InvalidDataPathException if the expression is invalid
     */
    public static DataPath parse(String expression, String rootCollectionName) {
        Objects.requireNonNull(expression, "expression cannot be null");
        Objects.requireNonNull(rootCollectionName, "rootCollectionName cannot be null");

        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidDataPathException(expression,
                "Data path expression cannot be empty");
        }

        String[] parts = trimmed.split("\\.");
        if (parts.length > MAX_DEPTH) {
            throw new InvalidDataPathException(expression,
                "Data path exceeds maximum depth of " + MAX_DEPTH +
                " (has " + parts.length + " segments)");
        }

        List<DataPathSegment> segments = new ArrayList<>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                throw new InvalidDataPathException(expression,
                    "Data path contains empty segment at position " + i);
            }

            if (i < parts.length - 1) {
                // Intermediate segment — must be a relationship
                segments.add(DataPathSegment.relationship(part));
            } else {
                // Last segment — terminal field
                segments.add(DataPathSegment.field(part));
            }
        }

        return new DataPath(rootCollectionName, segments, trimmed);
    }

    /**
     * Creates a single-field DataPath (no relationship traversal).
     *
     * @param fieldName          the field name to read
     * @param rootCollectionName the collection name
     * @return a single-segment DataPath
     */
    public static DataPath simple(String fieldName, String rootCollectionName) {
        return new DataPath(
            rootCollectionName,
            List.of(DataPathSegment.field(fieldName)),
            fieldName
        );
    }

    /**
     * Returns the terminal (last) segment — the field whose value is resolved.
     */
    public DataPathSegment terminal() {
        return segments.get(segments.size() - 1);
    }

    /**
     * Returns the relationship segments (all except the last).
     * Empty for single-field paths.
     */
    public List<DataPathSegment> relationships() {
        if (segments.size() <= 1) {
            return Collections.emptyList();
        }
        return segments.subList(0, segments.size() - 1);
    }

    /**
     * Returns the number of relationship hops (depth).
     * Zero for simple field references.
     */
    public int depth() {
        return segments.size() - 1;
    }

    /**
     * Returns whether this is a simple (single-field, no traversal) path.
     */
    public boolean isSimple() {
        return segments.size() == 1;
    }

    /**
     * Serializes back to dot-notation string for DB storage.
     */
    public String toExpression() {
        return expression;
    }
}
