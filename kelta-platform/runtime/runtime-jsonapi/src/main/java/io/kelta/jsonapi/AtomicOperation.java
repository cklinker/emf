package io.kelta.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a single operation in a JSON:API Atomic Operations request.
 *
 * <p>Supports three operation types:
 * <ul>
 *   <li>{@code add} — Create a new resource (requires data)</li>
 *   <li>{@code update} — Update an existing resource (requires ref + data)</li>
 *   <li>{@code remove} — Delete an existing resource (requires ref)</li>
 * </ul>
 *
 * @see <a href="https://jsonapi.org/ext/atomic">JSON:API Atomic Operations</a>
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtomicOperation(
        @JsonProperty("op") String op,
        @JsonProperty("ref") ResourceRef ref,
        @JsonProperty("data") ResourceData data
) {

    /**
     * Reference to an existing resource (for update/remove operations).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceRef(
            @JsonProperty("type") String type,
            @JsonProperty("id") String id,
            @JsonProperty("lid") String lid
    ) {}

    /**
     * Resource data for add/update operations.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceData(
            @JsonProperty("type") String type,
            @JsonProperty("id") String id,
            @JsonProperty("lid") String lid,
            @JsonProperty("attributes") Map<String, Object> attributes,
            @JsonProperty("relationships") Map<String, Object> relationships
    ) {}

    public void validate(int index) {
        if (op == null || op.isBlank()) {
            throw new IllegalArgumentException("Operation at index " + index + " is missing 'op' field");
        }
        switch (op) {
            case "add" -> {
                if (data == null || data.type() == null) {
                    throw new IllegalArgumentException("Operation at index " + index + ": 'add' requires 'data' with 'type'");
                }
            }
            case "update" -> {
                if (ref == null || ref.type() == null) {
                    throw new IllegalArgumentException("Operation at index " + index + ": 'update' requires 'ref' with 'type'");
                }
                if (ref.id() == null && ref.lid() == null) {
                    throw new IllegalArgumentException("Operation at index " + index + ": 'update' requires 'ref' with 'id' or 'lid'");
                }
            }
            case "remove" -> {
                if (ref == null || ref.type() == null) {
                    throw new IllegalArgumentException("Operation at index " + index + ": 'remove' requires 'ref' with 'type'");
                }
                if (ref.id() == null && ref.lid() == null) {
                    throw new IllegalArgumentException("Operation at index " + index + ": 'remove' requires 'ref' with 'id' or 'lid'");
                }
            }
            default -> throw new IllegalArgumentException("Operation at index " + index + ": unknown op '" + op + "'");
        }
    }
}
