package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.*;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.DashboardDataService;
import io.kelta.worker.service.DashboardDataService.WidgetResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for dashboard widget data endpoints.
 *
 * <p>Provides endpoints to fetch aggregated data for dashboard widgets
 * (metric cards, bar charts, data tables, and recent records).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/dashboards")
public class DashboardDataController {

    private static final Logger log = LoggerFactory.getLogger(DashboardDataController.class);

    private final DashboardDataService dashboardDataService;
    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;

    public DashboardDataController(DashboardDataService dashboardDataService,
                                   QueryEngine queryEngine,
                                   CollectionRegistry collectionRegistry) {
        this.dashboardDataService = dashboardDataService;
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
    }

    /**
     * Executes all widgets on a dashboard and returns their data.
     *
     * @param dashboardId the dashboard ID
     * @param body        optional runtime parameters (timeRange, startDate, endDate, etc.)
     * @return widget data keyed by component ID
     */
    @PostMapping("/{dashboardId}/data")
    public ResponseEntity<Map<String, Object>> executeDashboard(
            @PathVariable String dashboardId,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            // Load dashboard record
            Map<String, Object> dashboard = loadDashboard(dashboardId);
            if (dashboard == null) {
                return ResponseEntity.notFound().build();
            }

            // Load all components for this dashboard
            List<Map<String, Object>> components = loadDashboardComponents(dashboardId);
            if (components.isEmpty()) {
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("dashboardId", dashboardId);
                attributes.put("dashboardName", dashboard.get("name"));
                attributes.put("widgets", Map.of());

                return ResponseEntity.ok(
                    JsonApiResponseBuilder.single("dashboard-data", dashboardId, attributes));
            }

            Map<String, String> runtimeParams = body != null ? body : Map.of();
            Map<String, WidgetResult> results = dashboardDataService.executeDashboard(
                dashboardId, components, runtimeParams);

            // Build response
            Map<String, Object> widgetData = new LinkedHashMap<>();
            for (Map.Entry<String, WidgetResult> entry : results.entrySet()) {
                WidgetResult wr = entry.getValue();
                Map<String, Object> widget = new LinkedHashMap<>();
                if (wr.error() != null) {
                    widget.put("error", wr.error());
                } else {
                    widget.put("type", wr.type());
                    widget.put("data", wr.data());
                    if (wr.pagination() != null) {
                        widget.put("pagination", wr.pagination());
                    }
                }
                widgetData.put(entry.getKey(), widget);
            }

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("dashboardId", dashboardId);
            attributes.put("dashboardName", dashboard.get("name"));
            attributes.put("widgets", widgetData);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("widgetCount", components.size());
            meta.put("errorCount", results.values().stream()
                .filter(r -> r.error() != null).count());

            return ResponseEntity.ok(
                JsonApiResponseBuilder.single("dashboard-data", dashboardId, attributes, meta));

        } catch (Exception e) {
            log.error("Dashboard data execution error: dashboardId={}", dashboardId, e);
            return ResponseEntity.internalServerError().body(
                JsonApiResponseBuilder.error("500", "Internal Server Error",
                    "Failed to execute dashboard"));
        }
    }

    /**
     * Executes a single dashboard component/widget and returns its data.
     *
     * @param dashboardId the dashboard ID
     * @param componentId the component ID
     * @param body        optional runtime parameters
     * @return widget data for the single component
     */
    @PostMapping("/{dashboardId}/components/{componentId}/data")
    public ResponseEntity<Map<String, Object>> executeComponent(
            @PathVariable String dashboardId,
            @PathVariable String componentId,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            // Load the specific component
            Map<String, Object> component = loadComponent(componentId, dashboardId);
            if (component == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, String> runtimeParams = body != null ? body : Map.of();
            WidgetResult result = dashboardDataService.executeWidget(component, runtimeParams);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("componentId", componentId);
            attributes.put("dashboardId", dashboardId);

            if (result.error() != null) {
                attributes.put("error", result.error());
            } else {
                attributes.put("type", result.type());
                attributes.put("data", result.data());
                if (result.pagination() != null) {
                    attributes.put("pagination", result.pagination());
                }
            }

            return ResponseEntity.ok(
                JsonApiResponseBuilder.single("widget-data", componentId, attributes));

        } catch (DashboardDataService.WidgetExecutionException e) {
            log.warn("Widget execution failed: componentId={}, error={}", componentId, e.getMessage());
            return ResponseEntity.badRequest().body(
                JsonApiResponseBuilder.error("400", "Bad Request", e.getMessage()));
        } catch (Exception e) {
            log.error("Widget execution error: componentId={}", componentId, e);
            return ResponseEntity.internalServerError().body(
                JsonApiResponseBuilder.error("500", "Internal Server Error",
                    "Failed to execute widget"));
        }
    }

    // =========================================================================
    // Data loading helpers
    // =========================================================================

    private Map<String, Object> loadDashboard(String dashboardId) {
        CollectionDefinition dashboardsDef = collectionRegistry.get("dashboards");
        if (dashboardsDef == null) {
            log.error("Dashboards system collection not found in registry");
            return null;
        }
        return queryEngine.getById(dashboardsDef, dashboardId).orElse(null);
    }

    private List<Map<String, Object>> loadDashboardComponents(String dashboardId) {
        CollectionDefinition componentsDef = collectionRegistry.get("dashboard-components");
        if (componentsDef == null) {
            log.error("Dashboard-components system collection not found in registry");
            return List.of();
        }

        List<FilterCondition> filters = List.of(
            new FilterCondition("dashboardId", FilterOperator.EQ, dashboardId));
        List<SortField> sorting = List.of(
            new SortField("sortOrder", SortDirection.ASC));

        QueryRequest request = new QueryRequest(
            new Pagination(1, 100), sorting, List.of(), filters);

        QueryResult result = queryEngine.executeQuery(componentsDef, request);
        return result.data();
    }

    private Map<String, Object> loadComponent(String componentId, String dashboardId) {
        CollectionDefinition componentsDef = collectionRegistry.get("dashboard-components");
        if (componentsDef == null) {
            log.error("Dashboard-components system collection not found in registry");
            return null;
        }

        Optional<Map<String, Object>> component = queryEngine.getById(componentsDef, componentId);
        if (component.isEmpty()) return null;

        // Verify the component belongs to the requested dashboard
        String compDashboardId = (String) component.get().get("dashboardId");
        if (!dashboardId.equals(compDashboardId)) {
            log.warn("Component {} does not belong to dashboard {}", componentId, dashboardId);
            return null;
        }

        return component.get();
    }
}
