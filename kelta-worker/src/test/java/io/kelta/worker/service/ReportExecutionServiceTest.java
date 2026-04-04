package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.query.*;
import io.kelta.runtime.registry.CollectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportExecutionServiceTest {

    private QueryEngine queryEngine;
    private CollectionRegistry collectionRegistry;
    private CollectionLifecycleManager lifecycleManager;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private ReportExecutionService service;

    @BeforeEach
    void setUp() {
        queryEngine = mock(QueryEngine.class);
        collectionRegistry = mock(CollectionRegistry.class);
        lifecycleManager = mock(CollectionLifecycleManager.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        service = new ReportExecutionService(
            queryEngine, collectionRegistry, lifecycleManager, jdbcTemplate, objectMapper);
    }

    // =========================================================================
    // Column parsing
    // =========================================================================

    @Test
    void shouldParseColumnsFromJsonString() {
        String json = "[{\"fieldName\":\"name\",\"label\":\"Name\",\"type\":\"string\"},"
            + "{\"fieldName\":\"amount\",\"label\":\"Amount\",\"type\":\"number\"}]";

        var columns = service.parseColumns(json);

        assertEquals(2, columns.size());
        assertEquals("name", columns.get(0).fieldName());
        assertEquals("Name", columns.get(0).label());
        assertEquals("string", columns.get(0).type());
        assertEquals("amount", columns.get(1).fieldName());
    }

    @Test
    void shouldParseColumnsFromList() {
        List<Map<String, Object>> list = List.of(
            Map.of("fieldName", "status", "label", "Status", "type", "string")
        );

        var columns = service.parseColumns(list);

        assertEquals(1, columns.size());
        assertEquals("status", columns.get(0).fieldName());
    }

    @Test
    void shouldReturnEmptyForNullColumns() {
        assertEquals(List.of(), service.parseColumns(null));
    }

    @Test
    void shouldSkipColumnsWithBlankFieldName() {
        String json = "[{\"fieldName\":\"\",\"label\":\"Empty\"},{\"fieldName\":\"valid\",\"label\":\"Valid\"}]";
        var columns = service.parseColumns(json);
        assertEquals(1, columns.size());
        assertEquals("valid", columns.get(0).fieldName());
    }

    // =========================================================================
    // Filter parsing
    // =========================================================================

    @Test
    void shouldParseFiltersFromJsonString() {
        String json = "[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"active\"}]";

        var filters = service.parseFilters(json);

        assertEquals(1, filters.size());
        assertEquals("status", filters.get(0).fieldName());
        assertEquals(FilterOperator.EQ, filters.get(0).operator());
        assertEquals("active", filters.get(0).value());
    }

    @Test
    void shouldMapContainsOperatorToIcontains() {
        String json = "[{\"field\":\"name\",\"operator\":\"contains\",\"value\":\"test\"}]";

        var filters = service.parseFilters(json);

        assertEquals(FilterOperator.ICONTAINS, filters.get(0).operator());
    }

    @Test
    void shouldMapNotEqualsOperator() {
        String json = "[{\"field\":\"status\",\"operator\":\"not_equals\",\"value\":\"closed\"}]";

        var filters = service.parseFilters(json);

        assertEquals(FilterOperator.NEQ, filters.get(0).operator());
    }

    @Test
    void shouldMapGreaterThanAndLessThanOperators() {
        String json = "[{\"field\":\"amount\",\"operator\":\"greater_than\",\"value\":\"100\"},"
            + "{\"field\":\"count\",\"operator\":\"less_than\",\"value\":\"50\"}]";

        var filters = service.parseFilters(json);

        assertEquals(2, filters.size());
        assertEquals(FilterOperator.GT, filters.get(0).operator());
        assertEquals(FilterOperator.LT, filters.get(1).operator());
    }

    @Test
    void shouldReturnEmptyForNullFilters() {
        assertEquals(List.of(), service.parseFilters(null));
    }

    @Test
    void shouldReturnEmptyForBlankFilterString() {
        assertEquals(List.of(), service.parseFilters(""));
    }

    // =========================================================================
    // Sort parsing
    // =========================================================================

    @Test
    void shouldParseSortByFromSimpleFields() {
        Map<String, Object> config = Map.of("sortBy", "name", "sortDirection", "DESC");

        var sorting = service.parseSorting(config);

        assertEquals(1, sorting.size());
        assertEquals("name", sorting.get(0).fieldName());
        assertEquals(SortDirection.DESC, sorting.get(0).direction());
    }

    @Test
    void shouldDefaultToAscWhenNoDirection() {
        Map<String, Object> config = Map.of("sortBy", "name");

        var sorting = service.parseSorting(config);

        assertEquals(SortDirection.ASC, sorting.get(0).direction());
    }

    @Test
    void shouldReturnEmptyForNoSortConfig() {
        Map<String, Object> config = Map.of();

        var sorting = service.parseSorting(config);

        assertTrue(sorting.isEmpty());
    }

    // =========================================================================
    // Report execution
    // =========================================================================

    @Test
    void shouldExecuteReportWithColumnsAndFilters() {
        // Set up collection resolution
        when(jdbcTemplate.queryForList(contains("SELECT name FROM collection"), eq("col-1")))
            .thenReturn(List.of(Map.of("name", "accounts")));

        CollectionDefinition accountsDef = buildCollectionDef("accounts");
        when(collectionRegistry.get("accounts")).thenReturn(accountsDef);

        // Set up query result
        List<Map<String, Object>> records = List.of(
            Map.of("id", "r1", "name", "Acme", "amount", 100.0),
            Map.of("id", "r2", "name", "Beta", "amount", 200.0)
        );
        PaginationMetadata meta = new PaginationMetadata(2, 1, 200, 1);
        when(queryEngine.executeQuery(eq(accountsDef), any(QueryRequest.class)))
            .thenReturn(new QueryResult(records, meta));

        // Build report config
        Map<String, Object> reportConfig = new HashMap<>();
        reportConfig.put("id", "report-1");
        reportConfig.put("name", "Test Report");
        reportConfig.put("primaryCollectionId", "col-1");
        reportConfig.put("columns", "[{\"fieldName\":\"name\",\"label\":\"Name\",\"type\":\"string\"},"
            + "{\"fieldName\":\"amount\",\"label\":\"Amount\",\"type\":\"number\"}]");
        reportConfig.put("filters", "[{\"field\":\"amount\",\"operator\":\"greater_than\",\"value\":\"50\"}]");
        reportConfig.put("sortBy", "name");
        reportConfig.put("sortDirection", "ASC");

        // Execute
        ReportExecutionService.ReportResult result = service.execute(reportConfig, 1, 200);

        assertEquals(2, result.data().size());
        assertEquals(2, result.columns().size());
        assertEquals(2, result.metadata().totalCount());
        assertNull(result.groups()); // no groupBy

        // Verify query was constructed correctly
        verify(queryEngine).executeQuery(eq(accountsDef), argThat(req -> {
            assertEquals(1, req.filters().size());
            assertEquals("amount", req.filters().get(0).fieldName());
            assertEquals(FilterOperator.GT, req.filters().get(0).operator());
            assertEquals(1, req.sorting().size());
            assertEquals("name", req.sorting().get(0).fieldName());
            return true;
        }));
    }

    @Test
    void shouldGroupRecordsWhenGroupByIsSet() {
        when(jdbcTemplate.queryForList(contains("SELECT name FROM collection"), eq("col-1")))
            .thenReturn(List.of(Map.of("name", "orders")));

        CollectionDefinition ordersDef = buildCollectionDef("orders");
        when(collectionRegistry.get("orders")).thenReturn(ordersDef);

        List<Map<String, Object>> records = List.of(
            new HashMap<>(Map.of("id", "r1", "status", "open", "amount", 100.0)),
            new HashMap<>(Map.of("id", "r2", "status", "open", "amount", 200.0)),
            new HashMap<>(Map.of("id", "r3", "status", "closed", "amount", 50.0))
        );
        PaginationMetadata meta = new PaginationMetadata(3, 1, 200, 1);
        when(queryEngine.executeQuery(eq(ordersDef), any(QueryRequest.class)))
            .thenReturn(new QueryResult(records, meta));

        Map<String, Object> reportConfig = new HashMap<>();
        reportConfig.put("id", "report-2");
        reportConfig.put("name", "Grouped Report");
        reportConfig.put("primaryCollectionId", "col-1");
        reportConfig.put("columns", "[{\"fieldName\":\"status\",\"label\":\"Status\",\"type\":\"string\"},"
            + "{\"fieldName\":\"amount\",\"label\":\"Amount\",\"type\":\"number\"}]");
        reportConfig.put("groupBy", "status");

        ReportExecutionService.ReportResult result = service.execute(reportConfig, 1, 200);

        assertNotNull(result.groups());
        assertEquals(2, result.groups().size());
        assertEquals(2, result.groups().get("open").size());
        assertEquals(1, result.groups().get("closed").size());

        // Check aggregations
        assertNotNull(result.groupAggregations());
        Map<String, Object> openAggs = result.groupAggregations().get("open");
        assertEquals(300.0, (double) openAggs.get("amount_sum"), 0.01);
        assertEquals(2L, openAggs.get("amount_count"));
        assertEquals(150.0, (double) openAggs.get("amount_avg"), 0.01);
    }

    @Test
    void shouldThrowWhenCollectionNotFound() {
        when(jdbcTemplate.queryForList(contains("SELECT name FROM collection"), eq("bad-id")))
            .thenReturn(List.of());

        Map<String, Object> config = new HashMap<>();
        config.put("id", "report-x");
        config.put("name", "Bad Report");
        config.put("primaryCollectionId", "bad-id");
        config.put("columns", "[]");

        assertThrows(ReportExecutionService.ReportExecutionException.class,
            () -> service.execute(config, 1, 200));
    }

    @Test
    void shouldThrowWhenNoPrimaryCollectionId() {
        Map<String, Object> config = new HashMap<>();
        config.put("id", "report-y");
        config.put("name", "No Collection");
        config.put("columns", "[]");

        assertThrows(ReportExecutionService.ReportExecutionException.class,
            () -> service.execute(config, 1, 200));
    }

    // =========================================================================
    // CSV export
    // =========================================================================

    @Test
    void shouldExportCsv() throws Exception {
        when(jdbcTemplate.queryForList(contains("SELECT name FROM collection"), eq("col-1")))
            .thenReturn(List.of(Map.of("name", "contacts")));

        CollectionDefinition contactsDef = buildCollectionDef("contacts");
        when(collectionRegistry.get("contacts")).thenReturn(contactsDef);

        List<Map<String, Object>> records = List.of(
            Map.of("id", "r1", "name", "Alice", "email", "alice@example.com"),
            Map.of("id", "r2", "name", "Bob", "email", "bob@example.com")
        );
        PaginationMetadata meta = new PaginationMetadata(2, 1, 1000, 1);
        when(queryEngine.executeQuery(eq(contactsDef), any(QueryRequest.class)))
            .thenReturn(new QueryResult(records, meta));

        Map<String, Object> reportConfig = new HashMap<>();
        reportConfig.put("id", "report-csv");
        reportConfig.put("name", "CSV Export");
        reportConfig.put("primaryCollectionId", "col-1");
        reportConfig.put("columns", "[{\"fieldName\":\"name\",\"label\":\"Name\",\"type\":\"string\"},"
            + "{\"fieldName\":\"email\",\"label\":\"Email\",\"type\":\"string\"}]");

        StringWriter writer = new StringWriter();
        service.exportCsv(reportConfig, writer);

        String csv = writer.toString();
        String[] lines = csv.split("\n");
        assertEquals(3, lines.length); // header + 2 data rows
        assertEquals("Name,Email", lines[0]);
        assertEquals("Alice,alice@example.com", lines[1]);
        assertEquals("Bob,bob@example.com", lines[2]);
    }

    @Test
    void shouldEscapeCsvValues() throws Exception {
        when(jdbcTemplate.queryForList(contains("SELECT name FROM collection"), eq("col-1")))
            .thenReturn(List.of(Map.of("name", "notes")));

        CollectionDefinition notesDef = buildCollectionDef("notes");
        when(collectionRegistry.get("notes")).thenReturn(notesDef);

        List<Map<String, Object>> records = List.of(
            Map.of("id", "r1", "name", "Has, comma", "description", "Has \"quotes\"")
        );
        PaginationMetadata meta = new PaginationMetadata(1, 1, 2000, 1);
        when(queryEngine.executeQuery(eq(notesDef), any(QueryRequest.class)))
            .thenReturn(new QueryResult(records, meta));

        Map<String, Object> reportConfig = new HashMap<>();
        reportConfig.put("id", "report-esc");
        reportConfig.put("name", "Escape Test");
        reportConfig.put("primaryCollectionId", "col-1");
        reportConfig.put("columns", "[{\"fieldName\":\"name\",\"label\":\"Name\",\"type\":\"string\"},"
            + "{\"fieldName\":\"description\",\"label\":\"Description\",\"type\":\"string\"}]");

        StringWriter writer = new StringWriter();
        service.exportCsv(reportConfig, writer);

        String csv = writer.toString();
        assertTrue(csv.contains("\"Has, comma\""));
        assertTrue(csv.contains("\"Has \"\"quotes\"\"\""));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CollectionDefinition buildCollectionDef(String name) {
        return new CollectionDefinition(
            name, name, null,
            List.of(
                new FieldDefinition("name", FieldType.STRING, true, false, false, null, null, null, null, null, null),
                new FieldDefinition("amount", FieldType.DOUBLE, true, false, false, null, null, null, null, null, null),
                new FieldDefinition("status", FieldType.STRING, true, false, false, null, null, null, null, null, null),
                new FieldDefinition("email", FieldType.STRING, true, false, false, null, null, null, null, null, null),
                new FieldDefinition("description", FieldType.STRING, true, false, false, null, null, null, null, null, null)
            ),
            null, null, null, 1, null, null,
            false, true, false, Set.of(), Map.of(), null, null
        );
    }
}
