package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.query.*;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes reports by reading report configuration and building dynamic queries
 * against the target collection. Supports tabular, summary, and matrix report types
 * with column selection, filtering, grouping, sorting, and aggregation.
 *
 * @since 1.0.0
 */
@Service
public class ReportExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ReportExecutionService.class);
    private static final int MAX_REPORT_PAGE_SIZE = 1000;
    private static final int DEFAULT_REPORT_PAGE_SIZE = 200;
    private static final long QUERY_TIMEOUT_SECONDS = 30;

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final CollectionLifecycleManager lifecycleManager;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReportExecutionService(QueryEngine queryEngine,
                                  CollectionRegistry collectionRegistry,
                                  CollectionLifecycleManager lifecycleManager,
                                  JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.lifecycleManager = lifecycleManager;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes a report and returns paginated results.
     *
     * @param reportConfig the report configuration map (loaded from the reports system collection)
     * @param pageNumber   the page number (1-indexed)
     * @param pageSize     the page size
     * @return the report execution result
     */
    public ReportResult execute(Map<String, Object> reportConfig, int pageNumber, int pageSize) {
        String reportId = (String) reportConfig.get("id");
        String reportName = (String) reportConfig.get("name");
        String primaryCollectionId = (String) reportConfig.get("primaryCollectionId");

        if (primaryCollectionId == null || primaryCollectionId.isBlank()) {
            throw new ReportExecutionException("Report '" + reportName + "' has no primaryCollectionId");
        }

        // Resolve the target collection
        CollectionDefinition targetCollection = resolveCollection(primaryCollectionId);
        if (targetCollection == null) {
            throw new ReportExecutionException(
                "Collection not found for report '" + reportName + "' (collectionId=" + primaryCollectionId + ")");
        }

        // Parse report config
        List<ColumnConfig> columns = parseColumns(reportConfig.get("columns"));
        List<FilterCondition> filters = parseFilters(reportConfig.get("filters"));
        List<SortField> sorting = parseSorting(reportConfig);
        String groupBy = (String) reportConfig.get("groupBy");

        // Build field selection from columns
        List<String> fieldNames = columns.stream()
            .map(ColumnConfig::fieldName)
            .toList();

        // Clamp page size
        int clampedPageSize = Math.min(Math.max(pageSize, 1), MAX_REPORT_PAGE_SIZE);
        int clampedPageNumber = Math.max(pageNumber, 1);
        Pagination pagination = new Pagination(clampedPageNumber, clampedPageSize);

        // Execute query
        QueryRequest request = new QueryRequest(pagination, sorting, fieldNames, filters);
        QueryResult queryResult = queryEngine.executeQuery(targetCollection, request);

        log.info("Report executed: id={}, name='{}', collection='{}', page={}, pageSize={}, totalCount={}",
            reportId, reportName, targetCollection.name(),
            clampedPageNumber, clampedPageSize, queryResult.metadata().totalCount());

        // Build grouped results if needed
        Map<String, List<Map<String, Object>>> groups = null;
        Map<String, Map<String, Object>> groupAggregations = null;
        if (groupBy != null && !groupBy.isBlank()) {
            groups = groupRecords(queryResult.data(), groupBy);
            groupAggregations = computeGroupAggregations(groups, columns);
        }

        return new ReportResult(
            queryResult.data(),
            queryResult.metadata(),
            columns,
            groups,
            groupAggregations
        );
    }

    /**
     * Exports all report data as CSV, writing directly to the provided writer.
     * Fetches all pages to produce a complete export.
     *
     * @param reportConfig the report configuration
     * @param writer       the output writer
     * @throws IOException if writing fails
     */
    public void exportCsv(Map<String, Object> reportConfig, Writer writer) throws IOException {
        String primaryCollectionId = (String) reportConfig.get("primaryCollectionId");
        CollectionDefinition targetCollection = resolveCollection(primaryCollectionId);
        if (targetCollection == null) {
            throw new ReportExecutionException("Collection not found for export");
        }

        List<ColumnConfig> columns = parseColumns(reportConfig.get("columns"));
        List<FilterCondition> filters = parseFilters(reportConfig.get("filters"));
        List<SortField> sorting = parseSorting(reportConfig);

        List<String> fieldNames = columns.stream()
            .map(ColumnConfig::fieldName)
            .toList();

        // Write CSV header
        writer.write(columns.stream()
            .map(c -> escapeCsv(c.label()))
            .collect(Collectors.joining(",")));
        writer.write("\n");

        // Fetch and write all pages
        int page = 1;
        long totalWritten = 0;
        while (true) {
            Pagination pagination = new Pagination(page, MAX_REPORT_PAGE_SIZE);
            QueryRequest request = new QueryRequest(pagination, sorting, fieldNames, filters);
            QueryResult result = queryEngine.executeQuery(targetCollection, request);

            for (Map<String, Object> record : result.data()) {
                writer.write(columns.stream()
                    .map(c -> escapeCsv(formatValue(record.get(c.fieldName()))))
                    .collect(Collectors.joining(",")));
                writer.write("\n");
                totalWritten++;
            }

            if (result.data().isEmpty() || totalWritten >= result.metadata().totalCount()) {
                break;
            }
            page++;
        }

        log.info("CSV export complete: {} rows written", totalWritten);
    }

    // =========================================================================
    // Config parsing
    // =========================================================================

    @SuppressWarnings("unchecked")
    List<ColumnConfig> parseColumns(Object columnsObj) {
        if (columnsObj == null) {
            return List.of();
        }

        List<Map<String, Object>> columnList;
        if (columnsObj instanceof String str) {
            try {
                columnList = objectMapper.readValue(str, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse columns JSON: {}", e.getMessage());
                return List.of();
            }
        } else if (columnsObj instanceof List<?>) {
            columnList = (List<Map<String, Object>>) columnsObj;
        } else {
            return List.of();
        }

        return columnList.stream()
            .map(m -> new ColumnConfig(
                (String) m.get("fieldName"),
                (String) m.getOrDefault("label", m.get("fieldName")),
                (String) m.getOrDefault("type", "string")
            ))
            .filter(c -> c.fieldName() != null && !c.fieldName().isBlank())
            .toList();
    }

    @SuppressWarnings("unchecked")
    List<FilterCondition> parseFilters(Object filtersObj) {
        if (filtersObj == null) {
            return List.of();
        }

        List<Map<String, Object>> filterList;
        if (filtersObj instanceof String str) {
            if (str.isBlank()) return List.of();
            try {
                filterList = objectMapper.readValue(str, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse filters JSON: {}", e.getMessage());
                return List.of();
            }
        } else if (filtersObj instanceof List<?>) {
            filterList = (List<Map<String, Object>>) filtersObj;
        } else {
            return List.of();
        }

        List<FilterCondition> filters = new ArrayList<>();
        for (Map<String, Object> f : filterList) {
            String field = (String) f.get("field");
            String operator = (String) f.get("operator");
            Object value = f.get("value");

            if (field == null || field.isBlank()) continue;

            FilterOperator op = mapOperator(operator);
            filters.add(new FilterCondition(field, op, value));
        }
        return filters;
    }

    List<SortField> parseSorting(Map<String, Object> reportConfig) {
        // Try simple sortBy/sortDirection first (UI builder format)
        String sortBy = (String) reportConfig.get("sortBy");
        if (sortBy != null && !sortBy.isBlank()) {
            String direction = (String) reportConfig.getOrDefault("sortDirection", "ASC");
            SortDirection dir = "DESC".equalsIgnoreCase(direction)
                ? SortDirection.DESC : SortDirection.ASC;
            return List.of(new SortField(sortBy, dir));
        }

        // Fall back to sortOrder JSON array
        Object sortOrderObj = reportConfig.get("sortOrder");
        if (sortOrderObj == null) {
            return List.of();
        }

        return parseSortOrder(sortOrderObj);
    }

    @SuppressWarnings("unchecked")
    private List<SortField> parseSortOrder(Object sortOrderObj) {
        List<Map<String, Object>> sortList;
        if (sortOrderObj instanceof String str) {
            if (str.isBlank()) return List.of();
            try {
                sortList = objectMapper.readValue(str, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse sortOrder JSON: {}", e.getMessage());
                return List.of();
            }
        } else if (sortOrderObj instanceof List<?>) {
            sortList = (List<Map<String, Object>>) sortOrderObj;
        } else {
            return List.of();
        }

        List<SortField> sorting = new ArrayList<>();
        for (Map<String, Object> s : sortList) {
            String field = (String) s.get("field");
            String direction = (String) s.getOrDefault("direction", "ASC");
            if (field == null || field.isBlank()) continue;
            SortDirection dir = "DESC".equalsIgnoreCase(direction)
                ? SortDirection.DESC : SortDirection.ASC;
            sorting.add(new SortField(field, dir));
        }
        return sorting;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CollectionDefinition resolveCollection(String collectionId) {
        // Check activeCollections map in lifecycle manager first
        // Then query the collection table to get the name
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT name FROM collection WHERE id = ? AND active = true LIMIT 1",
                collectionId);
            if (rows.isEmpty()) {
                return null;
            }
            String collectionName = (String) rows.get(0).get("name");

            // Ensure the collection is loaded in the registry
            CollectionDefinition definition = collectionRegistry.get(collectionName);
            if (definition == null) {
                // Try to load it on-demand
                definition = lifecycleManager.loadCollectionByName(collectionName, null);
            }
            return definition;
        } catch (Exception e) {
            log.error("Failed to resolve collection: {}", collectionId, e);
            return null;
        }
    }

    private FilterOperator mapOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return FilterOperator.EQ;
        }
        return switch (operator.toLowerCase()) {
            case "equals", "eq" -> FilterOperator.EQ;
            case "not_equals", "neq" -> FilterOperator.NEQ;
            case "contains", "icontains" -> FilterOperator.ICONTAINS;
            case "greater_than", "gt" -> FilterOperator.GT;
            case "less_than", "lt" -> FilterOperator.LT;
            case "gte" -> FilterOperator.GTE;
            case "lte" -> FilterOperator.LTE;
            case "starts" -> FilterOperator.STARTS;
            case "ends" -> FilterOperator.ENDS;
            case "isnull" -> FilterOperator.ISNULL;
            default -> FilterOperator.EQ;
        };
    }

    private Map<String, List<Map<String, Object>>> groupRecords(
            List<Map<String, Object>> records, String groupBy) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String key = formatValue(record.get(groupBy));
            if (key.isEmpty()) key = "(empty)";
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }
        return groups;
    }

    private Map<String, Map<String, Object>> computeGroupAggregations(
            Map<String, List<Map<String, Object>>> groups,
            List<ColumnConfig> columns) {

        // Identify numeric columns for aggregation
        Set<String> numericTypes = Set.of(
            "number", "integer", "long", "double", "currency", "percent",
            "NUMBER", "INTEGER", "LONG", "DOUBLE", "CURRENCY", "PERCENT"
        );

        List<ColumnConfig> numericColumns = columns.stream()
            .filter(c -> numericTypes.contains(c.type()))
            .toList();

        if (numericColumns.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> aggs = new LinkedHashMap<>();
            for (ColumnConfig col : numericColumns) {
                double sum = 0;
                long count = 0;
                for (Map<String, Object> record : entry.getValue()) {
                    Object val = record.get(col.fieldName());
                    if (val instanceof Number num) {
                        sum += num.doubleValue();
                        count++;
                    }
                }
                aggs.put(col.fieldName() + "_sum", sum);
                aggs.put(col.fieldName() + "_count", count);
                aggs.put(col.fieldName() + "_avg", count > 0 ? sum / count : 0.0);
            }
            aggs.put("recordCount", entry.getValue().size());
            result.put(entry.getKey(), aggs);
        }
        return result;
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return value.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // =========================================================================
    // Result types
    // =========================================================================

    public record ColumnConfig(String fieldName, String label, String type) {}

    public record ReportResult(
        List<Map<String, Object>> data,
        PaginationMetadata metadata,
        List<ColumnConfig> columns,
        Map<String, List<Map<String, Object>>> groups,
        Map<String, Map<String, Object>> groupAggregations
    ) {}

    public static class ReportExecutionException extends RuntimeException {
        public ReportExecutionException(String message) {
            super(message);
        }
        public ReportExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
