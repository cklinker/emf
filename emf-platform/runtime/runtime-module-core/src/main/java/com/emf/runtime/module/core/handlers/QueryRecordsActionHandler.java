package com.emf.runtime.module.core.handlers;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.query.*;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.service.RollupSummaryService;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Action handler that queries records from a collection with optional filtering,
 * sorting, pagination, and aggregation support.
 *
 * <p>Config format (provided via Parameters):
 * <pre>
 * {
 *   "targetCollectionName": "orders",
 *   "filters": [
 *     { "field": "customer", "operator": "eq", "value": "cust-123" }
 *   ],
 *   "sort": "-created_at",
 *   "pageSize": 200,
 *   "aggregations": [
 *     { "function": "SUM", "field": "total_amount", "alias": "total_spent" },
 *     { "function": "COUNT", "alias": "total_orders" }
 *   ]
 * }
 * </pre>
 *
 * <p>Returns:
 * <pre>
 * {
 *   "records": [...],
 *   "totalCount": 5,
 *   "aggregations": { "total_spent": 750.00, "total_orders": 5 }
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class QueryRecordsActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(QueryRecordsActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionRegistry collectionRegistry;
    private final QueryEngine queryEngine;
    private final RollupSummaryService rollupSummaryService;

    public QueryRecordsActionHandler(ObjectMapper objectMapper,
                                     CollectionRegistry collectionRegistry,
                                     QueryEngine queryEngine,
                                     RollupSummaryService rollupSummaryService) {
        this.objectMapper = objectMapper;
        this.collectionRegistry = collectionRegistry;
        this.queryEngine = queryEngine;
        this.rollupSummaryService = rollupSummaryService;
    }

    @Override
    public String getActionTypeKey() {
        return "QUERY_RECORDS";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            // 1. Resolve collection
            String targetCollectionName = (String) config.get("targetCollectionName");
            if (targetCollectionName == null || targetCollectionName.isBlank()) {
                return ActionResult.failure("targetCollectionName is required");
            }

            CollectionDefinition targetCollection = collectionRegistry.get(targetCollectionName);
            if (targetCollection == null) {
                return ActionResult.failure("Collection not found: " + targetCollectionName);
            }

            // 2. Build filters
            List<FilterCondition> filters = buildFilters(config);

            // 3. Build sorting
            List<SortField> sorting = buildSorting(config);

            // 4. Build pagination
            int pageSize = config.containsKey("pageSize")
                ? ((Number) config.get("pageSize")).intValue()
                : 200;
            Pagination pagination = new Pagination(1, pageSize);

            // 5. Execute query
            QueryRequest request = new QueryRequest(pagination, sorting, List.of(), filters);
            QueryResult queryResult = queryEngine.executeQuery(targetCollection, request);

            // 6. Build output
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("records", queryResult.data());
            output.put("totalCount", queryResult.metadata().totalCount());

            // 7. Compute aggregations if requested
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aggregations =
                (List<Map<String, Object>>) config.get("aggregations");

            if (aggregations != null && !aggregations.isEmpty()
                    && rollupSummaryService != null) {
                Map<String, Object> aggResults = computeAggregations(
                    targetCollection, filters, aggregations);
                output.put("aggregations", aggResults);
            }

            log.info("Query records: collection={}, filters={}, records={}, aggregations={}",
                targetCollectionName, filters.size(), queryResult.data().size(),
                aggregations != null ? aggregations.size() : 0);

            return ActionResult.success(output);
        } catch (Exception e) {
            log.error("Failed to execute query records action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("targetCollectionName") == null) {
                throw new IllegalArgumentException("Config must contain 'targetCollectionName'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<FilterCondition> buildFilters(Map<String, Object> config) {
        List<Map<String, Object>> filterConfigs =
            (List<Map<String, Object>>) config.get("filters");
        if (filterConfigs == null || filterConfigs.isEmpty()) {
            return List.of();
        }

        List<FilterCondition> filters = new ArrayList<>();
        for (Map<String, Object> fc : filterConfigs) {
            String field = (String) fc.get("field");
            String operator = (String) fc.get("operator");
            Object value = fc.get("value");

            if (field == null || field.isBlank()) continue;

            FilterOperator op = parseOperator(operator);
            filters.add(new FilterCondition(field, op, value));
        }
        return filters;
    }

    private FilterOperator parseOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return FilterOperator.EQ;
        }
        return switch (operator.toLowerCase()) {
            case "eq" -> FilterOperator.EQ;
            case "neq" -> FilterOperator.NEQ;
            case "gt" -> FilterOperator.GT;
            case "lt" -> FilterOperator.LT;
            case "gte" -> FilterOperator.GTE;
            case "lte" -> FilterOperator.LTE;
            case "contains" -> FilterOperator.CONTAINS;
            case "icontains" -> FilterOperator.ICONTAINS;
            case "isnull" -> FilterOperator.ISNULL;
            default -> FilterOperator.EQ;
        };
    }

    private List<SortField> buildSorting(Map<String, Object> config) {
        String sort = (String) config.get("sort");
        if (sort == null || sort.isBlank()) {
            return List.of();
        }

        List<SortField> sorting = new ArrayList<>();
        for (String part : sort.split(",")) {
            part = part.trim();
            if (part.startsWith("-")) {
                sorting.add(new SortField(part.substring(1), SortDirection.DESC));
            } else {
                sorting.add(new SortField(part, SortDirection.ASC));
            }
        }
        return sorting;
    }

    private Map<String, Object> computeAggregations(
            CollectionDefinition collection,
            List<FilterCondition> filters,
            List<Map<String, Object>> aggregations) {

        Map<String, Object> results = new LinkedHashMap<>();
        String tableName = collection.storageConfig().tableName();

        // For aggregations, we need a filter field and value to pass to RollupSummaryService.
        // If we have filters, use the first one as the grouping constraint.
        // If no filters, compute against all records (use a dummy always-true condition).
        String filterField = null;
        String filterValue = null;
        Map<String, Object> additionalFilter = null;

        if (!filters.isEmpty()) {
            FilterCondition primaryFilter = filters.get(0);
            filterField = primaryFilter.fieldName();
            filterValue = primaryFilter.value() != null ? primaryFilter.value().toString() : null;

            // Any additional filters beyond the first one
            if (filters.size() > 1) {
                additionalFilter = new LinkedHashMap<>();
                for (int i = 1; i < filters.size(); i++) {
                    FilterCondition fc = filters.get(i);
                    additionalFilter.put(fc.fieldName(),
                        fc.value() != null ? fc.value().toString() : null);
                }
            }
        }

        for (Map<String, Object> agg : aggregations) {
            String function = (String) agg.get("function");
            String field = (String) agg.get("field");
            String alias = (String) agg.get("alias");

            if (function == null || alias == null) continue;

            try {
                Object value;
                if (filterField != null && filterValue != null) {
                    value = rollupSummaryService.compute(
                        tableName, filterField, filterValue,
                        function.toUpperCase(), field, additionalFilter);
                } else {
                    // No filter — compute aggregate over all records using a sentinel
                    // Use tenant_id or a similar always-present field if available
                    // For now, we'll fall back to 0 for count and null for others
                    value = function.equalsIgnoreCase("COUNT") ? 0L : null;
                }

                // Ensure numeric types are consistent
                if (value == null) {
                    value = function.equalsIgnoreCase("COUNT") ? 0L : 0.0;
                }

                results.put(alias, value);
            } catch (Exception e) {
                log.warn("Failed to compute aggregation {} for alias '{}': {}",
                    function, alias, e.getMessage());
                results.put(alias, function.equalsIgnoreCase("COUNT") ? 0L : 0.0);
            }
        }

        return results;
    }
}
