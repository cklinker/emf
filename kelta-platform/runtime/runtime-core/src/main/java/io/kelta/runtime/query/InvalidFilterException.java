package io.kelta.runtime.query;

/**
 * Thrown when a {@code filter[...]} query parameter cannot be parsed: unknown
 * operator, blank value, or an {@code IN}/{@code ANY} list that exceeds
 * {@link FilterCondition#MAX_IN_LIST_SIZE}. Extends {@link InvalidQueryException}
 * so the gateway error handler maps it to HTTP 400 with no extra wiring.
 */
public class InvalidFilterException extends InvalidQueryException {

    public InvalidFilterException(String message) {
        super(message);
    }

    public InvalidFilterException(String fieldName, String reason) {
        super(fieldName, reason);
    }
}
