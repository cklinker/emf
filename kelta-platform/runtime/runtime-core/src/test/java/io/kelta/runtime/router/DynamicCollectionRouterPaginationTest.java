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
