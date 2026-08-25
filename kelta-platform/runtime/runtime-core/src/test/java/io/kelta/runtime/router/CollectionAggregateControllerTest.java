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

/**
 * Generic collection name ("support-tickets") and generic field names
 * ("priority", "resolutionHours") throughout, deliberately -- this is a
 * platform-wide capability, not something scoped to any one tenant's data
 * shape.
 */
@SuppressWarnings("unchecked")
class CollectionAggregateControllerTest {

    private CollectionRegistry registry;
    private QueryEngine queryEngine;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CollectionDefinition definition;

    @BeforeEach
    void setUp() {
        registry = mock(CollectionRegistry.class);
        queryEngine = mock(QueryEngine.class);
        CollectionAggregateController controller = new CollectionAggregateController(registry, queryEngine);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();

        definition = new CollectionDefinitionBuilder()
                .name("support-tickets")
                .displayName("Support Tickets")
                .addField(FieldDefinition.requiredString("priority", 50))
                .systemCollection(false)
                .tenantScoped(false)
                .readOnly(false)
                .build();
        when(registry.get("support-tickets")).thenReturn(definition);
    }

    private Map<String, Object> row(Object priority, Object resolutionHours) {
        Map<String, Object> r = new HashMap<>();
        if (priority != null) r.put("priority", priority);
        if (resolutionHours != null) r.put("resolutionHours", resolutionHours);
        return r;
    }

    private QueryResult resultOf(List<Map<String, Object>> rows) {
        return new QueryResult(rows, new PaginationMetadata(rows.size(), 1, 1000, 1));
    }

    @Test
    void missingGroupBy_returns400() throws Exception {
        mockMvc.perform(get("/api/support-tickets/aggregate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCollection_returns404() throws Exception {
        mockMvc.perform(get("/api/nonexistent/aggregate").param("groupBy", "priority"))
                .andExpect(status().isNotFound());
    }

    @Test
    void groupsByFieldWithCounts() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of(
                row("high", null),
                row("high", null),
                row("low", null)
        )));

        MvcResult result = mockMvc.perform(get("/api/support-tickets/aggregate").param("groupBy", "priority"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertEquals(3, body.get("totalCount"));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
        assertEquals(2, groups.size());
        assertEquals("high", groups.get(0).get("key")); // most-reported group first
        assertEquals(2, groups.get(0).get("count"));
        assertNull(groups.get(0).get("avg")); // no `avg` param requested
    }

    @Test
    void computesAveragePerGroupWhenAvgRequested() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of(
                row("high", 4),
                row("high", 2),
                row("low", 10)
        )));

        MvcResult result = mockMvc.perform(get("/api/support-tickets/aggregate")
                        .param("groupBy", "priority")
                        .param("avg", "resolutionHours"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
        Map<String, Object> high = groups.stream().filter(g -> "high".equals(g.get("key"))).findFirst().get();
        assertEquals(3.0, high.get("avg"));
        Map<String, Object> low = groups.stream().filter(g -> "low".equals(g.get("key"))).findFirst().get();
        assertEquals(10.0, low.get("avg"));
    }

    @Test
    void skipsRowsMissingTheGroupByField() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of(
                row("high", null),
                row(null, null)
        )));

        MvcResult result = mockMvc.perform(get("/api/support-tickets/aggregate").param("groupBy", "priority"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertEquals(2, body.get("totalCount")); // raw row count, unfiltered
        List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
        assertEquals(1, groups.size());
    }

    @Test
    void doesNotFoldDifferentCasingTogether() throws Exception {
        // No case-normalization at this layer, by design -- see the class javadoc.
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of(
                row("High", null),
                row("high", null)
        )));

        MvcResult result = mockMvc.perform(get("/api/support-tickets/aggregate").param("groupBy", "priority"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
        assertEquals(2, groups.size());
    }

    @Test
    void selectsOnlyGroupByAndAvgFields() throws Exception {
        when(queryEngine.executeQuery(eq(definition), any(QueryRequest.class))).thenReturn(resultOf(List.of()));

        mockMvc.perform(get("/api/support-tickets/aggregate")
                        .param("groupBy", "priority")
                        .param("avg", "resolutionHours")
                        .param("filter[status][eq]", "open"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<QueryRequest> captor = org.mockito.ArgumentCaptor.forClass(QueryRequest.class);
        org.mockito.Mockito.verify(queryEngine).executeQuery(eq(definition), captor.capture());
        QueryRequest request = captor.getValue();
        assertEquals(List.of("priority", "resolutionHours"), request.fields());
        assertEquals(1, request.filters().size());
        assertEquals("status", request.filters().get(0).fieldName());
    }
}
