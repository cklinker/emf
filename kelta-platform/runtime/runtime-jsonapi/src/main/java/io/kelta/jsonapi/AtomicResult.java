package io.kelta.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a single result in a JSON:API Atomic Operations response.
 *
 * <p>For add/update operations, contains the resulting resource.
 * For remove operations, the result is an empty object.
 *
 * @see <a href="https://jsonapi.org/ext/atomic">JSON:API Atomic Operations</a>
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtomicResult(
        @JsonProperty("data") ResourceObject data
) {

    /**
     * Resource object in a result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceObject(
            @JsonProperty("type") String type,
            @JsonProperty("id") String id,
            @JsonProperty("lid") String lid,
            @JsonProperty("attributes") Map<String, Object> attributes
    ) {}

    /**
     * Creates an empty result (for remove operations).
     */
    public static AtomicResult empty() {
        return new AtomicResult(null);
    }

    /**
     * Creates a result with a resource (for add/update operations).
     */
    public static AtomicResult of(String type, String id, String lid, Map<String, Object> attributes) {
        return new AtomicResult(new ResourceObject(type, id, lid, attributes));
    }
}
