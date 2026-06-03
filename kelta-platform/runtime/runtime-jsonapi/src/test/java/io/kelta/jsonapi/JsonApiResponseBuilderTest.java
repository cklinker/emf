package io.kelta.jsonapi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class JsonApiResponseBuilderTest {

    @Test
    void single_wrapsAttributesWithTypeAndId() {
        Map<String, Object> attrs = Map.of("name", "Test", "value", 42);
        Map<String, Object> doc = JsonApiResponseBuilder.single("widgets", "w1", attrs);

        Map<String, Object> data = (Map<String, Object>) doc.get("data");
        assertEquals("widgets", data.get("type"));
        assertEquals("w1", data.get("id"));
        assertEquals(attrs, data.get("attributes"));
        assertNull(doc.get("meta"));
    }

    @Test
    void single_withMeta_includesTopLevelMeta() {
        Map<String, Object> attrs = Map.of("x", 1);
        Map<String, Object> meta = Map.of("version", "2.0");
        Map<String, Object> doc = JsonApiResponseBuilder.single("items", "i1", attrs, meta);

        assertNotNull(doc.get("meta"));
        assertEquals("2.0", ((Map<String, Object>) doc.get("meta")).get("version"));
    }

    @Test
    void single_withNullMeta_omitsMeta() {
        Map<String, Object> doc = JsonApiResponseBuilder.single("items", "i1", Map.of(), null);
        assertFalse(doc.containsKey("meta"));
    }

    @Test
    void collection_wrapsRecordsAsArray() {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("id", "1");
        r1.put("name", "One");
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("id", "2");
        r2.put("name", "Two");

        Map<String, Object> doc = JsonApiResponseBuilder.collection("things", List.of(r1, r2));

        List<Map<String, Object>> data = (List<Map<String, Object>>) doc.get("data");
        assertEquals(2, data.size());
        assertEquals("things", data.get(0).get("type"));
        assertEquals("1", data.get(0).get("id"));
        Map<String, Object> attrs0 = (Map<String, Object>) data.get(0).get("attributes");
        assertEquals("One", attrs0.get("name"));
        assertFalse(attrs0.containsKey("id")); // id should be extracted, not in attributes
    }

    @Test
    void collection_withMeta_includesMeta() {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("id", "1");
        Map<String, Object> meta = Map.of("total", 100);
        Map<String, Object> doc = JsonApiResponseBuilder.collection("items", List.of(r1), meta);

        assertEquals(100, ((Map<String, Object>) doc.get("meta")).get("total"));
    }

    @Test
    void error_buildsErrorsArray() {
        Map<String, Object> doc = JsonApiResponseBuilder.error("400", "Bad Request", "Missing field");

        List<Map<String, Object>> errors = (List<Map<String, Object>>) doc.get("errors");
        assertEquals(1, errors.size());
        assertEquals("400", errors.get(0).get("status"));
        assertEquals("Bad Request", errors.get(0).get("title"));
        assertEquals("Missing field", errors.get(0).get("detail"));
        // Legacy 3-arg overload now derives an UPPER_SNAKE code from the title so
        // every error object still satisfies the platform "status+code+detail" rule.
        assertEquals("BAD_REQUEST", errors.get(0).get("code"));
        assertFalse(doc.containsKey("data"));
    }

    @Test
    void error_withExplicitCode_populatesAllFields() {
        Map<String, Object> doc = JsonApiResponseBuilder.error(
                "404", "NOT_FOUND", "Not Found", "Collection 'widgets' does not exist");

        List<Map<String, Object>> errors = (List<Map<String, Object>>) doc.get("errors");
        Map<String, Object> err = errors.get(0);
        assertEquals("404", err.get("status"));
        assertEquals("NOT_FOUND", err.get("code"));
        assertEquals("Not Found", err.get("title"));
        assertEquals("Collection 'widgets' does not exist", err.get("detail"));
        assertFalse(err.containsKey("source"));
    }

    @Test
    void error_withPointer_includesSourcePointer() {
        Map<String, Object> doc = JsonApiResponseBuilder.error(
                "400", "VALIDATION_FAILED", "Validation Error",
                "email is required", "/data/attributes/email");

        List<Map<String, Object>> errors = (List<Map<String, Object>>) doc.get("errors");
        Map<String, Object> err = errors.get(0);
        assertEquals("VALIDATION_FAILED", err.get("code"));
        Map<String, Object> source = (Map<String, Object>) err.get("source");
        assertEquals("/data/attributes/email", source.get("pointer"));
    }

    @Test
    void errorObject_blankPointer_omitsSource() {
        Map<String, Object> err = JsonApiResponseBuilder.errorObject(
                "400", "BAD_REQUEST", "Bad Request", "Bad input", "  ");
        assertFalse(err.containsKey("source"));
    }

    @Test
    void errors_wrapsMultipleErrorObjectsIntoOneDocument() {
        Map<String, Object> e1 = JsonApiResponseBuilder.errorObject(
                "400", "VALIDATION_FAILED", "Validation Error",
                "name is required", "/data/attributes/name");
        Map<String, Object> e2 = JsonApiResponseBuilder.errorObject(
                "400", "VALIDATION_FAILED", "Validation Error",
                "email is invalid", "/data/attributes/email");

        Map<String, Object> doc = JsonApiResponseBuilder.errors(List.of(e1, e2));

        List<Map<String, Object>> errors = (List<Map<String, Object>>) doc.get("errors");
        assertEquals(2, errors.size());
        // Every error object must carry the required fields; no empty {} objects.
        for (Map<String, Object> err : errors) {
            assertNotNull(err.get("status"));
            assertNotNull(err.get("code"));
            assertNotNull(err.get("detail"));
        }
    }
}
