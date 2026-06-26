package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests that DynamicCollectionRouter emits a JSON:API {@code links} block with
 * {@code self}, {@code prev}, {@code next} URLs alongside the existing
 * {@code metadata} object, and that the bracket-syntax {@code page[size]} cap
 * is enforced.
 */
@SuppressWarnings("unchecked")
class DynamicCollectionRouterPaginationTest {

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        DynamicCollectionRouter router = new DynamicCollectionRouter(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(router).build();
        objectMapper = new ObjectMapper();
    }

    private CollectionDefinition buildCustomersCollection() {
        return new CollectionDefinitionBuilder()
                .name("customers")
                .displayName("Customers")
                .addField(FieldDefinition.requiredString("name", 100))
                .systemCollection(false)
                .tenantScoped(false)
                .readOnly(false)
                .build();
    }

    private Map<String, Object> record(String id, String name) {
        Map<String, Object> rec = new HashMap<>();
        rec.put("id", id);
        rec.put("name", name);
        return rec;
    }

    @Test
    void list_middlePage_emitsLinksWithSelfPrevNext() throws Exception {
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(100, 2, 20, 5));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[number]", "2")
                        .param("page[size]", "20"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> links = (Map<String, Object>) response.get("links");
        assertNotNull(links, "response must include a 'links' block");
        assertEquals("/api/customers?page[number]=2&page[size]=20", links.get("self"));
        assertEquals("/api/customers?page[number]=1&page[size]=20", links.get("prev"));
        assertEquals("/api/customers?page[number]=3&page[size]=20", links.get("next"));
    }

    @Test
    void list_firstPage_emitsNullPrevAndNonNullNext() throws Exception {
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(100, 1, 20, 5));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> links = (Map<String, Object>) response.get("links");
        assertNotNull(links);
        assertNull(links.get("prev"), "prev should be null on page 1");
        assertNotNull(links.get("next"), "next should be set when more pages exist");
    }

    @Test
    void list_lastPage_emitsNullNextAndNonNullPrev() throws Exception {
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(100, 5, 20, 5));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[number]", "5")
                        .param("page[size]", "20"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> links = (Map<String, Object>) response.get("links");
        assertNotNull(links.get("prev"));
        assertNull(links.get("next"), "next should be null on the last page");
    }

    @Test
    void list_pageSizeAboveHttpCap_isClampedTo200() throws Exception {
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(1, 1, 200, 1));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        mockMvc.perform(get("/api/customers")
                        .param("page[size]", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(queryEngine).executeQuery(eq(def), captor.capture());
        assertEquals(200, captor.getValue().pagination().pageSize(),
                "page[size]=500 must be clamped down to MAX_HTTP_PAGE_SIZE (200)");
    }

    @Test
    void list_pageSizeAboveHttpCap_echoesClampedFlagInMeta() throws Exception {
        // Regression: page[size]=500 used to look like a silent-empty-data
        // response (positive totalCount, empty data). The router now clamps to
        // 200 AND surfaces the clamp in `metadata.pageSizeClamped` +
        // `requestedPageSize` so the caller can detect their request was
        // modified.
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme"), record("c2", "Beta")),
                new PaginationMetadata(9999, 1, 200, 50));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[size]", "500"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        assertFalse(data.isEmpty(),
                "data must not be empty when totalCount is positive — that was the bug");

        Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
        assertEquals(9999, ((Number) metadata.get("totalCount")).intValue());
        assertEquals(200, ((Number) metadata.get("pageSize")).intValue(),
                "effective pageSize must be the clamped value (200)");
        assertEquals(500, ((Number) metadata.get("requestedPageSize")).intValue(),
                "requestedPageSize must echo what the caller asked for");
        assertEquals(Boolean.TRUE, metadata.get("pageSizeClamped"),
                "pageSizeClamped must be true when the request was clamped");
    }

    @Test
    void list_pageSizeAtCap_doesNotEmitClampedFlag() throws Exception {
        // Boundary: page[size]=200 equals the cap, so the clamp indicators must
        // be absent from metadata.
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(50, 1, 200, 1));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[size]", "200"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
        assertEquals(200, ((Number) metadata.get("pageSize")).intValue());
        assertFalse(metadata.containsKey("pageSizeClamped"),
                "pageSizeClamped must be absent when page[size] equals the cap");
        assertFalse(metadata.containsKey("requestedPageSize"),
                "requestedPageSize must be absent when no clamping occurred");
    }

    @Test
    void list_pageSizeBelowCap_doesNotEmitClampedFlag() throws Exception {
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(50, 1, 25, 2));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[size]", "25"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
        assertFalse(metadata.containsKey("pageSizeClamped"));
        assertFalse(metadata.containsKey("requestedPageSize"));
    }

    @Test
    void list_emitsBothMetaAndMetadataWithIdenticalContent() throws Exception {
        // Regression: JSON:API spec uses `meta`; we historically only emitted
        // `metadata`, which broke spec-compliant clients. Both keys must now
        // be present and carry identical pagination info.
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(100, 2, 20, 5));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[number]", "2")
                        .param("page[size]", "20"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);

        Map<String, Object> meta = (Map<String, Object>) response.get("meta");
        Map<String, Object> metadata = (Map<String, Object>) response.get("metadata");
        assertNotNull(meta, "JSON:API standard `meta` key must be present");
        assertNotNull(metadata, "legacy `metadata` alias must still be present");
        assertEquals(metadata, meta, "`meta` and `metadata` must carry identical content");
        assertEquals(100, ((Number) meta.get("totalCount")).intValue());
        assertEquals(2, ((Number) meta.get("currentPage")).intValue());
        assertEquals(20, ((Number) meta.get("pageSize")).intValue());
        assertEquals(5, ((Number) meta.get("totalPages")).intValue());
    }

    @Test
    void list_linksPreserveNonPaginationQueryParams() throws Exception {
        CollectionDefinition def = buildCustomersCollection();
        when(registry.get("customers")).thenReturn(def);

        QueryResult result = new QueryResult(
                List.of(record("c1", "Acme")),
                new PaginationMetadata(100, 2, 20, 5));
        when(queryEngine.executeQuery(eq(def), any(QueryRequest.class))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/customers")
                        .param("page[number]", "2")
                        .param("page[size]", "20")
                        .param("sort", "-createdAt"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> links = (Map<String, Object>) response.get("links");
        String next = (String) links.get("next");
        assertTrue(next.contains("sort=-createdAt"),
                "next link must preserve non-pagination query params, got: " + next);
        assertTrue(next.contains("page[number]=3"), "next link must advance page, got: " + next);
    }
}
