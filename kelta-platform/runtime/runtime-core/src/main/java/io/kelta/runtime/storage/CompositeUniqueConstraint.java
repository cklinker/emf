package io.kelta.runtime.storage;

import java.util.List;
import java.util.Objects;

/**
 * Metadata describing a composite unique constraint on a collection's
 * physical table.
 *
 * <p>The {@link #name} is the underlying database constraint identifier
 * (subject to PostgreSQL's 63-char limit, generated deterministically
 * from the table and ordered field names when not supplied by the caller).
 *
 * <p>The {@link #fieldNames} are the <em>API</em> field names of the
 * collection — not the physical column names — so callers can match them
 * back against {@link io.kelta.runtime.model.CollectionDefinition#fields()}.
 *
 * @param name       the database-level constraint name (unique within a table)
 * @param fieldNames the ordered list of API field names participating in the constraint
 */
public record CompositeUniqueConstraint(String name, List<String> fieldNames) {
    public CompositeUniqueConstraint {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(fieldNames, "fieldNames cannot be null");
        if (fieldNames.size() < 2) {
            throw new IllegalArgumentException(
                "A composite unique constraint must include at least 2 fields (got "
                    + fieldNames.size() + ")");
        }
        fieldNames = List.copyOf(fieldNames);
    }
}
