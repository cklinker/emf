package io.kelta.jsonapi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonApiError#toMap()} is what response bodies are built from —
 * plain maps serialize identically under every mapper configuration, unlike
 * the bean itself (observed as {@code {"errors":[{}]}} on the deployed worker).
 */
class JsonApiErrorToMapTest {

    @Test
    void toMapCarriesAllSetMembersAndOmitsNulls() {
        JsonApiError error = new JsonApiError("422", "VALIDATION_RULE_FAILED",
                "Validation Error", "amountMax must be >= amountMin");
        error.setSource(Map.of("pointer", "/data/attributes/amountMax"));
        error.setMeta(Map.of("requestId", "abc12345"));

        Map<String, Object> map = error.toMap();

        assertEquals("422", map.get("status"));
        assertEquals("VALIDATION_RULE_FAILED", map.get("code"));
        assertEquals("Validation Error", map.get("title"));
        assertEquals("amountMax must be >= amountMin", map.get("detail"));
        assertEquals(Map.of("pointer", "/data/attributes/amountMax"), map.get("source"));
        assertEquals(Map.of("requestId", "abc12345"), map.get("meta"));
        assertFalse(map.containsKey("id"), "unset members must be omitted");
    }

    @Test
    void envelopeOfMapsSerializesWithMembers() {
        JsonApiError error = new JsonApiError("400", "VALIDATION_FAILED", "Validation Error", "name is required");

        String json = new tools.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("errors", List.of(error.toMap())));

        assertTrue(json.contains("\"detail\":\"name is required\""), json);
        assertTrue(json.contains("\"code\":\"VALIDATION_FAILED\""), json);
        assertFalse(json.contains("{}"), json);
    }
}
