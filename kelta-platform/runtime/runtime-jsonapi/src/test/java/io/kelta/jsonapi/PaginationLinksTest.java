package io.kelta.jsonapi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaginationLinksTest {

    @Test
    void build_middlePage_emitsSelfPrevNext() {
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", Map.of(), 2, 20, 5);

        assertEquals("/api/customers?page[number]=2&page[size]=20", links.get("self"));
        assertEquals("/api/customers?page[number]=1&page[size]=20", links.get("prev"));
        assertEquals("/api/customers?page[number]=3&page[size]=20", links.get("next"));
    }

    @Test
    void build_firstPage_prevIsNull() {
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", Map.of(), 1, 20, 5);

        assertNull(links.get("prev"));
        assertNotNull(links.get("next"));
        assertTrue(links.containsKey("prev"), "prev key must be present (with null value)");
    }

    @Test
    void build_lastPage_nextIsNull() {
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", Map.of(), 5, 20, 5);

        assertNotNull(links.get("prev"));
        assertNull(links.get("next"));
        assertTrue(links.containsKey("next"), "next key must be present (with null value)");
    }

    @Test
    void build_singlePageResult_bothPrevAndNextAreNull() {
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", Map.of(), 1, 20, 1);

        assertNull(links.get("prev"));
        assertNull(links.get("next"));
        assertEquals("/api/customers?page[number]=1&page[size]=20", links.get("self"));
    }

    @Test
    void build_emptyResult_bothPrevAndNextAreNull() {
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", Map.of(), 1, 20, 0);

        assertNull(links.get("prev"));
        assertNull(links.get("next"));
    }

    @Test
    void build_preservesNonPaginationQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("filter[status][EQ]", "ACTIVE");
        params.put("sort", "-createdAt");
        params.put("page[number]", "2");
        params.put("page[size]", "20");

        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", params, 2, 20, 5);

        String next = (String) links.get("next");
        assertTrue(next.contains("filter%5Bstatus%5D%5BEQ%5D=ACTIVE"),
                "next link must preserve filter param, got: " + next);
        assertTrue(next.contains("sort=-createdAt"),
                "next link must preserve sort param, got: " + next);
        assertTrue(next.contains("page[number]=3"),
                "next link must point to next page, got: " + next);
        assertTrue(next.contains("page[size]=20"),
                "next link must preserve page size, got: " + next);
        // Original page[number]/page[size] values are stripped, not duplicated.
        assertEquals(1, next.split("page\\[number\\]=", -1).length - 1,
                "page[number] must appear exactly once");
        assertEquals(1, next.split("page\\[size\\]=", -1).length - 1,
                "page[size] must appear exactly once");
    }

    @Test
    void build_nullParams_doesNotThrow() {
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", null, 1, 20, 3);
        assertEquals("/api/customers?page[number]=1&page[size]=20", links.get("self"));
    }

    @Test
    void build_paramValueWithSpecialChars_isUrlEncoded() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", "hello world & friends");
        Map<String, Object> links = PaginationLinks.build(
                "/api/search", params, 1, 20, 2);

        String self = (String) links.get("self");
        assertTrue(self.contains("hello+world+%26+friends") || self.contains("hello%20world%20%26%20friends"),
                "value must be URL-encoded, got: " + self);
    }

    @Test
    void build_returnsLinkedHashMapPreservingKeyOrder() {
        // self, prev, next ordering matters for predictable response shape.
        Map<String, Object> links = PaginationLinks.build(
                "/api/customers", Map.of(), 2, 20, 5);
        var iter = links.keySet().iterator();
        assertEquals("self", iter.next());
        assertEquals("prev", iter.next());
        assertEquals("next", iter.next());
    }
}
