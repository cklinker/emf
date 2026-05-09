package io.kelta.runtime.module.core.handlers;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.*;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionHandler;
import io.kelta.runtime.workflow.ActionResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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

    public QueryRecordsActionHandler(ObjectMapper objectMapper,
                                     CollectionRegistry collectionRegistry,
                                     QueryEngine queryEngine) {
        this.objectMapper = objectMapper;
        this.collectionRegistry = collectionRegistry;
        this.queryEngine = queryEngine;
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

            String targetCollectionName = (String) config.get("targetCollectionName");
            if (targetCollectionName == null || targetCollectionName.isBlank()) {
                return ActionResult.failure("targetCollectionName is required");
            }

            CollectionDefinition targetCollection = collectionRegistry.get(targetCollectionName);
            if (targetCollection == null) {
                return ActionResult.failure("Collection not found: " + targetCollectionName);
            }

            List<FilterCondition> filters = buildFilters(config);
            List<SortField> sorting = buildSorting(config);

            int pageSize = config.containsKey("pageSize")
                ? ((Number) config.get("pageSize")).intValue()
                : 200;
            Pagination pagination = new Pagination(1, pageSize);

            QueryRequest request = new QueryRequest(pagination, sorting, List.of(), filters);
            QueryResult queryResult = queryEngine.executeQuery(targetCollection, request);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("records", queryResult.data());
            output.put("totalCount", queryResult.metadata().totalCount());

            List<AggregationSpec> aggregationSpecs = buildAggregationSpecs(config);
            if (!aggregationSpecs.isEmpty()) {
                Map<String, Object> aggResults = queryEngine.aggregate(
                    targetCollection, filters, aggregationSpecs);
                output.put("aggregations", aggResults);
            }

            log.info("Query records: collection={}, filters={}, records={}, aggregations={}",
                targetCollectionName, filters.size(), queryResult.data().size(),
                aggregationSpecs.size());

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
            buildAggregationSpecs(config);
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

    @SuppressWarnings("unchecked")
    private List<AggregationSpec> buildAggregationSpecs(Map<String, Object> config) {
        List<Map<String, Object>> raw =
            (List<Map<String, Object>>) config.get("aggregations");
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<AggregationSpec> specs = new ArrayList<>(raw.size());
        for (Map<String, Object> agg : raw) {
            String function = (String) agg.get("function");
            String field = (String) agg.get("field");
            String alias = (String) agg.get("alias");
            specs.add(new AggregationSpec(function, field, alias));
        }
        return specs;
    }
}
