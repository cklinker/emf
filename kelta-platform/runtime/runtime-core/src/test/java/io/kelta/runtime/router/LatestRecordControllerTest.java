package io.kelta.runtime.router;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.SortDirection;
import io.kelta.runtime.registry.CollectionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Generic collection ("equipment-checks") and generic field names
 *  ("status") throughout, deliberately -- same reasoning as
 *  CollectionAggregateControllerTest. */
@SuppressWarnings("unchecked")
class LatestRecordControllerTest {

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CollectionDefinition definition;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        LatestRecordController controller = new LatestRecordController(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();

        definition = new CollectionDefinitionBuilder()
                .name("equipment-checks")
                .displayName("Equipment Checks")
                .addField(FieldDefinition.requiredString("status", 50))
                .systemCollection(false)
                .tenantScoped(false)
                .readOnly(false)
                .build();
        when(registry.get("equipment-checks")).thenReturn(definition);
    }

    private Map<String, Object> row(String status, String createdAt) {
        Map<String, Object> r = new HashMap<>();
        r.put("status", status);
        r.put("createdAt", createdAt);
        return r;
    }

    private QueryResult resultOf(List<Map<String, Object>> rows) {
        return new QueryResult(rows, new PaginationMetadata(rows.size(), 1, 1, 1));
    }

    @Test
    void missingFields_returns400() throws Exception {
        mockMvc.perform(get("/api/equipment-checks/latest")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownCollection_returns404() throws Exception {
        mockMvc.perform(get("/api/nonexistent/latest").param("fields", "status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noMatchingRows_returnsNullRecord() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of()));

        MvcResult result = mockMvc.perform(get("/api/equipment-checks/latest").param("fields", "status"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertNull(body.get("record"));
    }

    @Test
    void returnsTheRequestedFieldsPlusRecordedAt() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class)))
                .thenReturn(resultOf(List.of(row("Operational", "2026-08-20T00:00:00Z"))));

        MvcResult result = mockMvc.perform(get("/api/equipment-checks/latest").param("fields", "status"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> record = (Map<String, Object>) body.get("record");
        assertEquals("Operational", record.get("status"));
        assertEquals("2026-08-20T00:00:00Z", record.get("recordedAt"));
        assertFalse(record.containsKey("createdAt")); // renamed to recordedAt, not duplicated
    }

    @Test
    void sortsByCreatedAtDescendingAndLimitsToOne() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of()));

        mockMvc.perform(get("/api/equipment-checks/latest")
                        .param("fields", "status")
                        .param("filter[facilityId][eq]", "f1"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<QueryRequest> captor = org.mockito.ArgumentCaptor.forClass(QueryRequest.class);
        org.mockito.Mockito.verify(queryEngine).executeQuery(eq(definition), captor.capture());
        QueryRequest request = captor.getValue();
        assertEquals(1, request.pagination().pageSize());
        assertEquals(1, request.sorting().size());
        assertEquals("createdAt", request.sorting().get(0).fieldName());
        assertEquals(SortDirection.DESC, request.sorting().get(0).direction());
        assertTrue(request.fields().contains("status"));
        assertTrue(request.fields().contains("createdAt"));
        assertEquals(1, request.filters().size());
        assertEquals("facilityId", request.filters().get(0).fieldName());
    }

    @Test
    void doesNotDuplicateCreatedAtWhenExplicitlyRequested() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of()));

        mockMvc.perform(get("/api/equipment-checks/latest").param("fields", "status,createdAt"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<QueryRequest> captor = org.mockito.ArgumentCaptor.forClass(QueryRequest.class);
        org.mockito.Mockito.verify(queryEngine).executeQuery(eq(definition), captor.capture());
        assertEquals(List.of("status", "createdAt"), captor.getValue().fields());
    }
}
