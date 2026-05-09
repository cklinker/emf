package io.kelta.runtime.query;

import java.util.Objects;
import java.util.Set;

/**
 * Specification for a single aggregation to compute over a query result set.
 *
 * <p>Used by {@link QueryEngine#aggregate(io.kelta.runtime.model.CollectionDefinition, java.util.List, java.util.List)}
 * to describe SQL aggregate functions (e.g. {@code SUM(total_amount) AS total_spent}).
 *
 * @param function aggregate function: COUNT, SUM, MIN, MAX, AVG (uppercase)
 * @param field    column to aggregate; required for SUM/MIN/MAX/AVG, ignored for COUNT
 * @param alias    output key in the result map; must be a valid SQL identifier
 */
public record AggregationSpec(String function, String field, String alias) {

    private static final Set<String> VALID_FUNCTIONS = Set.of("COUNT", "SUM", "MIN", "MAX", "AVG");

    public AggregationSpec {
        Objects.requireNonNull(function, "function cannot be null");
        Objects.requireNonNull(alias, "alias cannot be null");
        function = function.toUpperCase();
        if (!VALID_FUNCTIONS.contains(function)) {
            throw new IllegalArgumentException(
                "Invalid aggregate function '" + function + "'; must be one of " + VALID_FUNCTIONS);
        }
        if (alias.isBlank()) {
            throw new IllegalArgumentException("alias cannot be blank");
        }
        if (!"COUNT".equals(function)) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException(
                    "field is required for aggregate function " + function);
            }
        }
    }
}
