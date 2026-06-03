package io.kelta.runtime.storage;

import java.util.List;
import java.util.Objects;

/**
 * Metadata describing a composite UNIQUE constraint on a collection's physical
 * table — the database-level constraint name plus the API field names that the
 * constraint covers, in declaration order.
 *
 * <p>Single-column unique constraints declared via {@code field.unique()} are
 * not represented here; this record is only used for the constraints created
 * by {@link PhysicalTableStorageAdapter#addCompositeUniqueConstraint}.
 */
public record CompositeUniqueConstraint(String constraintName, List<String> fieldNames) {
    public CompositeUniqueConstraint {
        Objects.requireNonNull(constraintName, "constraintName");
        Objects.requireNonNull(fieldNames, "fieldNames");
        if (fieldNames.size() < 2) {
            throw new IllegalArgumentException("Composite unique constraint must cover at least 2 fields");
        }
        fieldNames = List.copyOf(fieldNames);
    }
}
