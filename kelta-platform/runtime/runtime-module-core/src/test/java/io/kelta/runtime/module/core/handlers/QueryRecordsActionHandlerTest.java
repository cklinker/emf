package io.kelta.runtime.module.core.handlers;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.AggregationSpec;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.PaginationMetadata;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QueryRecordsActionHandler")
class QueryRecordsActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollectionRegistry registry = mock(CollectionRegistry.class);
    private final QueryEngine queryEngine = mock(QueryEngine.class);
    private final QueryRecordsActionHandler handler =
        new QueryRecordsActionHandler(objectMapper, registry, queryEngine);

    private static ActionContext context(String configJson) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of())
            .actionConfigJson(configJson).workflowRuleId("wf1").executionLogId("log1")
            .build();
    }

    private static QueryResult queryResultWith(List<Map<String, Object>> rows, long total) {
        return new QueryResult(rows, new PaginationMetadata(total, 1, 200, (int) Math.ceil(total / 200.0)));
    }

    @Test
    @DisplayName("Should have correct action type key")
    void key() {
        assertEquals("QUERY_RECORDS", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Filtered SUM + COUNT returns engine results")
    void filteredAggregation() {
        CollectionDefinition collDef = mock(CollectionDefinition.class);
        when(registry.get("orders")).thenReturn(collDef);
        when(queryEngine.executeQuery(eq(collDef), any(QueryRequest.class)))
            .thenReturn(queryResultWith(List.of(Map.of("id", "o1", "total_amount", 161.98)), 1));
        when(queryEngine.aggregate(eq(collDef), any(), any()))
            .thenReturn(Map.of("total_spent", 161.98, "total_orders", 1L));

        String config = """
            {
              "targetCollectionName": "orders",
              "filters": [{"field": "customer", "operator": "eq", "value": "cust-1"}],
              "aggregations": [
                {"function": "SUM", "field": "total_amount", "alias": "total_spent"},
                {"function": "COUNT", "alias": "total_orders"}
              ]
            }
            """;

        ActionResult result = handler.execute(context(config));
        assertTrue(result.successful());

        @SuppressWarnings("unchecked")
        Map<String, Object> aggs = (Map<String, Object>) result.outputData().get("aggregations");
        assertEquals(161.98, aggs.get("total_spent"));
        assertEquals(1L, aggs.get("total_orders"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FilterCondition>> filterCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AggregationSpec>> specCap = ArgumentCaptor.forClass(List.class);
        verify(queryEngine).aggregate(eq(collDef), filterCap.capture(), specCap.capture());

        assertEquals(1, filterCap.getValue().size());
        assertEquals("customer", filterCap.getValue().get(0).fieldName());
        assertEquals(2, specCap.getValue().size());
        assertEquals("SUM", specCap.getValue().get(0).function());
        assertEquals("COUNT", specCap.getValue().get(1).function());
    }

    @Test
    @DisplayName("Empty filters still aggregate via QueryEngine")
    void emptyFilterAggregation() {
        CollectionDefinition collDef = mock(CollectionDefinition.class);
        when(registry.get("orders")).thenReturn(collDef);
        when(queryEngine.executeQuery(eq(collDef), any(QueryRequest.class)))
            .thenReturn(queryResultWith(List.of(), 5));
        when(queryEngine.aggregate(eq(collDef), eq(List.of()), any()))
            .thenReturn(Map.of("count", 5L));

        String config = """
            {
              "targetCollectionName": "orders",
              "aggregations": [{"function": "COUNT", "alias": "count"}]
            }
            """;

        ActionResult result = handler.execute(context(config));
        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> aggs = (Map<String, Object>) result.outputData().get("aggregations");
        assertEquals(5L, aggs.get("count"));
        verify(queryEngine).aggregate(eq(collDef), eq(List.of()), any());
    }

    @Test
    @DisplayName("Skips aggregations block when none configured")
    void noAggregationsConfigured() {
        CollectionDefinition collDef = mock(CollectionDefinition.class);
        when(registry.get("orders")).thenReturn(collDef);
        when(queryEngine.executeQuery(eq(collDef), any(QueryRequest.class)))
            .thenReturn(queryResultWith(List.of(), 0));

        String config = "{\"targetCollectionName\": \"orders\"}";
        ActionResult result = handler.execute(context(config));
        assertTrue(result.successful());
        assertFalse(result.outputData().containsKey("aggregations"));
    }

    @Test
    @DisplayName("validate rejects SUM without field")
    void validateRejectsSumWithoutField() {
        String config = """
            {"targetCollectionName": "orders",
             "aggregations": [{"function": "SUM", "alias": "x"}]}
            """;
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, () -> handler.validate(config));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("validate rejects unknown aggregate function")
    void validateRejectsUnknownFunction() {
        String config = """
            {"targetCollectionName": "orders",
             "aggregations": [{"function": "MEDIAN", "field": "x", "alias": "m"}]}
            """;
        assertThrows(IllegalArgumentException.class, () -> handler.validate(config));
    }
}
