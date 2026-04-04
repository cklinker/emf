package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.system.SystemCollectionDefinitions;
import io.kelta.runtime.query.*;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.cache.WorkerCacheManager;
import io.kelta.worker.service.DashboardDataService.WidgetExecutionException;
import io.kelta.worker.service.DashboardDataService.WidgetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardDataServiceTest {

    private QueryEngine queryEngine;
    private CollectionRegistry collectionRegistry;
    private CollectionLifecycleManager lifecycleManager;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private WorkerCacheManager cacheManager;
    private DashboardDataService service;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionRegistry = mock(CollectionRegistry.class);
        lifecycleManager = mock(CollectionLifecycleManager.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        cacheManager = mock(WorkerCacheManager.class);

        // Default: no cache hits
        when(cacheManager.getDashboardWidgetData(anyString())).thenReturn(Optional.empty());

        service = new DashboardDataService(queryEngine, collectionRegistry,
            lifecycleManager, jdbcTemplate, objectMapper, cacheManager);
    }

    // =========================================================================
    // Metric widget tests
    // =========================================================================

    @Test
    void shouldExecuteMetricWidgetWithCount() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        QueryResult queryResult = QueryResult.of(
            List.of(Map.of("name", "A"), Map.of("name", "B"), Map.of("name", "C")),
            42L, new Pagination(1, 1000));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-1", "metric",
            Map.of("collectionName", "accounts", "aggregateFunction", "COUNT"));

        WidgetResult result = service.executeWidget(component, Map.of());

        assertEquals("metric", result.type());
        assertNotNull(result.data());
        assertEquals(42L, result.data().get("value"));
        assertEquals("COUNT", result.data().get("aggregateFunction"));
    }

    @Test
    void shouldExecuteMetricWidgetWithSum() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("opportunities")).thenReturn(collDef);

        List<Map<String, Object>> data = List.of(
            Map.of("amount", 100.0), Map.of("amount", 250.5), Map.of("amount", 49.5));
        QueryResult queryResult = QueryResult.of(data, 3L, new Pagination(1, 1000));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-2", "metric",
            Map.of("collectionName", "opportunities",
                   "aggregateFunction", "SUM",
                   "aggregateField", "amount"));

        WidgetResult result = service.executeWidget(component, Map.of());

        assertEquals("metric", result.type());
        assertEquals(400.0, result.data().get("value"));
    }

    @Test
    void shouldThrowWhenAggregateFieldMissingForNonCount() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        Map<String, Object> component = buildComponent("comp-3", "metric",
            Map.of("collectionName", "accounts", "aggregateFunction", "SUM"));

        assertThrows(WidgetExecutionException.class,
            () -> service.executeWidget(component, Map.of()));
    }

    // =========================================================================
    // Chart widget tests
    // =========================================================================

    @Test
    void shouldExecuteChartWidgetWithGrouping() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("cases")).thenReturn(collDef);

        List<Map<String, Object>> data = List.of(
            Map.of("status", "open"), Map.of("status", "open"),
            Map.of("status", "closed"), Map.of("status", "pending"));
        QueryResult queryResult = QueryResult.of(data, 4L, new Pagination(1, 1000));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-4", "chart",
            Map.of("collectionName", "cases",
                   "groupByField", "status",
                   "aggregateFunction", "COUNT"));

        WidgetResult result = service.executeWidget(component, Map.of());

        assertEquals("chart", result.type());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series =
            (List<Map<String, Object>>) result.data().get("series");
        assertNotNull(series);
        assertEquals(3, series.size()); // open, closed, pending

        // Find the "open" group and verify count
        Map<String, Object> openGroup = series.stream()
            .filter(s -> "open".equals(s.get("label")))
            .findFirst().orElse(null);
        assertNotNull(openGroup);
        assertEquals(2, openGroup.get("count"));
        assertEquals(2, openGroup.get("value"));
    }

    @Test
    void shouldThrowWhenGroupByFieldMissing() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("cases")).thenReturn(collDef);

        Map<String, Object> component = buildComponent("comp-5", "chart",
            Map.of("collectionName", "cases"));

        assertThrows(WidgetExecutionException.class,
            () -> service.executeWidget(component, Map.of()));
    }

    @Test
    void shouldLimitChartGroupsToMaxGroups() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("items")).thenReturn(collDef);

        // Generate 30 distinct groups
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            data.add(Map.of("category", "cat-" + i));
        }
        QueryResult queryResult = QueryResult.of(data, 30L, new Pagination(1, 1000));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-6", "chart",
            Map.of("collectionName", "items",
                   "groupByField", "category",
                   "maxGroups", 5));

        WidgetResult result = service.executeWidget(component, Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series =
            (List<Map<String, Object>>) result.data().get("series");
        assertEquals(5, series.size());
    }

    // =========================================================================
    // Table widget tests
    // =========================================================================

    @Test
    void shouldExecuteTableWidgetWithPagination() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("contacts")).thenReturn(collDef);

        List<Map<String, Object>> data = List.of(
            Map.of("name", "Alice", "email", "alice@example.com"),
            Map.of("name", "Bob", "email", "bob@example.com"));
        QueryResult queryResult = QueryResult.of(data, 50L, new Pagination(1, 25));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-7", "table",
            Map.of("collectionName", "contacts",
                   "fields", List.of("name", "email")));

        WidgetResult result = service.executeWidget(component, Map.of("page", "1", "pageSize", "25"));

        assertEquals("table", result.type());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
            (List<Map<String, Object>>) result.data().get("records");
        assertEquals(2, records.size());
        assertNotNull(result.pagination());
        assertEquals(50L, result.pagination().get("totalCount"));
    }

    // =========================================================================
    // Recent records widget tests
    // =========================================================================

    @Test
    void shouldExecuteRecentWidgetSortedByCreatedAtDesc() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("activities")).thenReturn(collDef);

        List<Map<String, Object>> data = List.of(
            Map.of("subject", "Latest"), Map.of("subject", "Older"));
        QueryResult queryResult = QueryResult.of(data, 100L, new Pagination(1, 10));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-8", "recent",
            Map.of("collectionName", "activities", "limit", 10));

        WidgetResult result = service.executeWidget(component, Map.of());

        assertEquals("recent", result.type());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
            (List<Map<String, Object>>) result.data().get("records");
        assertEquals(2, records.size());
        assertEquals(100L, result.data().get("totalCount"));
    }

    @Test
    void shouldClampRecentRecordsLimit() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("activities")).thenReturn(collDef);

        QueryResult queryResult = QueryResult.of(List.of(), 0L, new Pagination(1, 100));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        // Request 200 but max is 100
        Map<String, Object> component = buildComponent("comp-9", "recent",
            Map.of("collectionName", "activities", "limit", 200));

        service.executeWidget(component, Map.of());

        // Verify the pagination used is clamped to 100
        verify(queryEngine).executeQuery(eq(collDef), argThat(req ->
            req.pagination().pageSize() <= 100));
    }

    // =========================================================================
    // Time range filter tests
    // =========================================================================

    @Test
    void shouldBuildTimeFiltersFromRuntimeParams() {
        List<FilterCondition> filters = service.buildTimeFilters(
            Map.of("timeRange", "7D"), Map.of());

        assertEquals(1, filters.size());
        assertEquals("createdAt", filters.get(0).fieldName());
        assertEquals(FilterOperator.GTE, filters.get(0).operator());
    }

    @Test
    void shouldBuildTimeFiltersWithCustomTimeField() {
        List<FilterCondition> filters = service.buildTimeFilters(
            Map.of("timeRange", "30D"),
            Map.of("timeField", "closedAt"));

        assertEquals(1, filters.size());
        assertEquals("closedAt", filters.get(0).fieldName());
    }

    @Test
    void shouldBuildTimeFiltersFromExplicitDates() {
        List<FilterCondition> filters = service.buildTimeFilters(
            Map.of("startDate", "2026-01-01", "endDate", "2026-03-31"),
            Map.of());

        assertEquals(2, filters.size());
    }

    // =========================================================================
    // Dashboard execution tests
    // =========================================================================

    @Test
    void shouldExecuteMultipleWidgets() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        QueryResult queryResult = QueryResult.of(
            List.of(Map.of("name", "A")), 10L, new Pagination(1, 1000));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        List<Map<String, Object>> components = List.of(
            buildComponent("comp-a", "metric",
                Map.of("collectionName", "accounts", "aggregateFunction", "COUNT")),
            buildComponent("comp-b", "recent",
                Map.of("collectionName", "accounts", "limit", 5))
        );

        Map<String, WidgetResult> results = service.executeDashboard(
            "dash-1", components, Map.of());

        assertEquals(2, results.size());
        assertNotNull(results.get("comp-a"));
        assertNotNull(results.get("comp-b"));
        assertNull(results.get("comp-a").error());
        assertNull(results.get("comp-b").error());
    }

    @Test
    void shouldReturnErrorForFailedWidget() {
        // No collection set up → widget should fail gracefully
        when(collectionRegistry.get(anyString())).thenReturn(null);

        List<Map<String, Object>> components = List.of(
            buildComponent("comp-fail", "metric",
                Map.of("collectionName", "nonexistent", "aggregateFunction", "COUNT")));

        Map<String, WidgetResult> results = service.executeDashboard(
            "dash-2", components, Map.of());

        assertEquals(1, results.size());
        assertNotNull(results.get("comp-fail").error());
    }

    // =========================================================================
    // Caching tests
    // =========================================================================

    @Test
    void shouldReturnCachedResultWhenPresent() {
        Map<String, Object> cachedData = Map.of(
            "type", "metric",
            "data", Map.of("value", 42L));
        when(cacheManager.getDashboardWidgetData(anyString()))
            .thenReturn(Optional.of(cachedData));

        Map<String, Object> component = buildComponent("comp-cached", "metric",
            Map.of("collectionName", "accounts", "aggregateFunction", "COUNT"));

        WidgetResult result = service.executeWidget(component, Map.of());

        assertEquals("metric", result.type());
        // Query engine should NOT be called
        verifyNoInteractions(queryEngine);
    }

    @Test
    void shouldCacheResultAfterExecution() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        QueryResult queryResult = QueryResult.of(
            List.of(), 5L, new Pagination(1, 1000));
        when(queryEngine.executeQuery(eq(collDef), any())).thenReturn(queryResult);

        Map<String, Object> component = buildComponent("comp-new", "metric",
            Map.of("collectionName", "accounts", "aggregateFunction", "COUNT"));

        service.executeWidget(component, Map.of());

        verify(cacheManager).putDashboardWidgetData(anyString(), any());
    }

    // =========================================================================
    // Unsupported type test
    // =========================================================================

    @Test
    void shouldThrowForUnsupportedWidgetType() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        Map<String, Object> component = buildComponent("comp-bad", "unknown_type",
            Map.of("collectionName", "accounts"));

        assertThrows(WidgetExecutionException.class,
            () -> service.executeWidget(component, Map.of()));
    }

    @Test
    void shouldThrowWhenComponentTypeIsNull() {
        Map<String, Object> component = new HashMap<>();
        component.put("id", "comp-null");
        component.put("componentType", null);
        component.put("config", Map.of("collectionName", "accounts"));

        assertThrows(WidgetExecutionException.class,
            () -> service.executeWidget(component, Map.of()));
    }

    // =========================================================================
    // Aggregate computation tests
    // =========================================================================

    @Test
    void shouldComputeAverageAggregate() {
        List<Map<String, Object>> records = List.of(
            Map.of("score", 10.0), Map.of("score", 20.0), Map.of("score", 30.0));

        Object avg = service.computeAggregate(records, "score", "AVG");
        assertEquals(20.0, (double) avg, 0.001);
    }

    @Test
    void shouldComputeMinAggregate() {
        List<Map<String, Object>> records = List.of(
            Map.of("price", 50.0), Map.of("price", 10.0), Map.of("price", 30.0));

        Object min = service.computeAggregate(records, "price", "MIN");
        assertEquals(10.0, (double) min, 0.001);
    }

    @Test
    void shouldComputeMaxAggregate() {
        List<Map<String, Object>> records = List.of(
            Map.of("price", 50.0), Map.of("price", 10.0), Map.of("price", 30.0));

        Object max = service.computeAggregate(records, "price", "MAX");
        assertEquals(50.0, (double) max, 0.001);
    }

    @Test
    void shouldReturnZeroForEmptyRecords() {
        Object result = service.computeAggregate(List.of(), "amount", "SUM");
        assertEquals(0, result);
    }

    // =========================================================================
    // Config parsing tests
    // =========================================================================

    @Test
    void shouldParseFieldListFromJsonString() {
        List<String> fields = service.parseFieldList("[\"name\",\"email\",\"phone\"]");
        assertEquals(3, fields.size());
        assertEquals("name", fields.get(0));
    }

    @Test
    void shouldParseFieldListFromCommaSeparatedString() {
        List<String> fields = service.parseFieldList("name, email, phone");
        assertEquals(3, fields.size());
        assertEquals("name", fields.get(0));
        assertEquals("email", fields.get(1));
    }

    @Test
    void shouldParseFieldListFromList() {
        List<String> fields = service.parseFieldList(List.of("name", "email"));
        assertEquals(2, fields.size());
    }

    @Test
    void shouldReturnEmptyForNullFields() {
        List<String> fields = service.parseFieldList(null);
        assertTrue(fields.isEmpty());
    }

    @Test
    void shouldParseFiltersFromList() {
        List<Map<String, Object>> filtersList = List.of(
            Map.of("field", "status", "operator", "eq", "value", "open"),
            Map.of("field", "priority", "operator", "eq", "value", "high"));

        List<FilterCondition> filters = service.parseFilters(filtersList);
        assertEquals(2, filters.size());
        assertEquals("status", filters.get(0).fieldName());
        assertEquals(FilterOperator.EQ, filters.get(0).operator());
    }

    // =========================================================================
    // Collection resolution tests
    // =========================================================================

    @Test
    void shouldResolveCollectionByName() {
        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        CollectionDefinition resolved = service.resolveTargetCollection(null,
            Map.of("collectionName", "accounts"));
        assertNotNull(resolved);
    }

    @Test
    void shouldResolveCollectionFromReport() {
        CollectionDefinition reportsDef = SystemCollectionDefinitions.reports();
        when(collectionRegistry.get("reports")).thenReturn(reportsDef);

        Map<String, Object> report = new HashMap<>();
        report.put("primaryCollectionId", "col-123");
        when(queryEngine.getById(eq(reportsDef), eq("rpt-1")))
            .thenReturn(Optional.of(report));

        CollectionDefinition collDef = SystemCollectionDefinitions.dashboards();
        when(jdbcTemplate.queryForList(anyString(), eq("col-123")))
            .thenReturn(List.of(Map.of("name", "accounts")));
        when(collectionRegistry.get("accounts")).thenReturn(collDef);

        CollectionDefinition resolved = service.resolveTargetCollection("rpt-1", Map.of());
        assertNotNull(resolved);
    }

    @Test
    void shouldReturnNullWhenCollectionNotFound() {
        when(collectionRegistry.get(anyString())).thenReturn(null);
        when(lifecycleManager.loadCollectionByName(anyString(), any())).thenReturn(null);

        CollectionDefinition resolved = service.resolveTargetCollection(null,
            Map.of("collectionName", "nonexistent"));
        assertNull(resolved);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Map<String, Object> buildComponent(String id, String type,
                                               Map<String, Object> config) {
        Map<String, Object> component = new HashMap<>();
        component.put("id", id);
        component.put("componentType", type);
        component.put("reportId", null);
        component.put("config", new HashMap<>(config));
        return component;
    }
}
