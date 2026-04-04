package io.kelta.worker.controller;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.system.SystemCollectionDefinitions;
import io.kelta.runtime.query.*;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.DashboardDataService;
import io.kelta.worker.service.DashboardDataService.WidgetExecutionException;
import io.kelta.worker.service.DashboardDataService.WidgetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardDataControllerTest {

    private DashboardDataService dashboardDataService;
    private QueryEngine queryEngine;
    private CollectionRegistry collectionRegistry;
    private DashboardDataController controller;

    @BeforeEach
    void setUp() {
        dashboardDataService = mock(DashboardDataService.class);
        queryEngine = mock(QueryEngine.class);
        collectionRegistry = mock(CollectionRegistry.class);
        controller = new DashboardDataController(dashboardDataService, queryEngine, collectionRegistry);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttributes(Map<String, Object> body) {
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (Map<String, Object>) data.get("attributes");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMeta(Map<String, Object> body) {
        return (Map<String, Object>) body.get("meta");
    }

    // =========================================================================
    // Dashboard data endpoint tests
    // =========================================================================

    @Test
    void shouldExecuteDashboardWithMultipleWidgets() {
        setupDashboardRegistry();

        // Dashboard record
        Map<String, Object> dashboard = Map.of("id", "dash-1", "name", "Sales Dashboard");
        when(queryEngine.getById(any(), eq("dash-1")))
            .thenReturn(Optional.of(dashboard));

        // Components
        List<Map<String, Object>> components = List.of(
            Map.of("id", "comp-1", "componentType", "metric", "dashboardId", "dash-1",
                   "sortOrder", 1, "config", Map.of()),
            Map.of("id", "comp-2", "componentType", "chart", "dashboardId", "dash-1",
                   "sortOrder", 2, "config", Map.of()));
        QueryResult componentsResult = QueryResult.of(components, 2L, new Pagination(1, 100));

        // First getById for dashboard, then executeQuery for components
        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);
        when(queryEngine.executeQuery(eq(componentsDef), any())).thenReturn(componentsResult);

        // Widget execution results
        Map<String, WidgetResult> widgetResults = new LinkedHashMap<>();
        widgetResults.put("comp-1", new WidgetResult("metric",
            Map.of("value", 42L), null, null));
        widgetResults.put("comp-2", new WidgetResult("chart",
            Map.of("series", List.of()), null, null));
        when(dashboardDataService.executeDashboard(eq("dash-1"), eq(components), any()))
            .thenReturn(widgetResults);

        ResponseEntity<Map<String, Object>> response = controller.executeDashboard(
            "dash-1", Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> attrs = getAttributes(response.getBody());
        assertEquals("dash-1", attrs.get("dashboardId"));
        assertEquals("Sales Dashboard", attrs.get("dashboardName"));
        assertNotNull(attrs.get("widgets"));

        Map<String, Object> meta = getMeta(response.getBody());
        assertEquals(2, meta.get("widgetCount"));
        assertEquals(0L, meta.get("errorCount"));
    }

    @Test
    void shouldReturn404WhenDashboardNotFound() {
        setupDashboardRegistry();
        when(queryEngine.getById(any(), eq("nonexistent")))
            .thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.executeDashboard(
            "nonexistent", null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn404WhenDashboardsCollectionMissing() {
        when(collectionRegistry.get("dashboards")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.executeDashboard(
            "dash-1", null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturnEmptyWidgetsWhenNoComponents() {
        setupDashboardRegistry();

        Map<String, Object> dashboard = Map.of("id", "dash-2", "name", "Empty Dashboard");
        when(queryEngine.getById(any(), eq("dash-2")))
            .thenReturn(Optional.of(dashboard));

        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);
        when(queryEngine.executeQuery(eq(componentsDef), any()))
            .thenReturn(QueryResult.empty(new Pagination(1, 100)));

        ResponseEntity<Map<String, Object>> response = controller.executeDashboard(
            "dash-2", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> attrs = getAttributes(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> widgets = (Map<String, Object>) attrs.get("widgets");
        assertTrue(widgets.isEmpty());
    }

    @Test
    void shouldReportWidgetErrorsInResponse() {
        setupDashboardRegistry();

        Map<String, Object> dashboard = Map.of("id", "dash-3", "name", "Error Dashboard");
        when(queryEngine.getById(any(), eq("dash-3")))
            .thenReturn(Optional.of(dashboard));

        List<Map<String, Object>> components = List.of(
            Map.of("id", "comp-err", "componentType", "metric", "dashboardId", "dash-3",
                   "sortOrder", 1, "config", Map.of()));
        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);
        when(queryEngine.executeQuery(eq(componentsDef), any()))
            .thenReturn(QueryResult.of(components, 1L, new Pagination(1, 100)));

        Map<String, WidgetResult> widgetResults = Map.of(
            "comp-err", WidgetResult.error("Collection not found"));
        when(dashboardDataService.executeDashboard(eq("dash-3"), eq(components), any()))
            .thenReturn(widgetResults);

        ResponseEntity<Map<String, Object>> response = controller.executeDashboard(
            "dash-3", Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> meta = getMeta(response.getBody());
        assertEquals(1L, meta.get("errorCount"));
    }

    // =========================================================================
    // Single component endpoint tests
    // =========================================================================

    @Test
    void shouldExecuteSingleComponent() {
        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);

        Map<String, Object> component = new HashMap<>();
        component.put("id", "comp-single");
        component.put("componentType", "metric");
        component.put("dashboardId", "dash-1");
        component.put("config", Map.of("collectionName", "accounts", "aggregateFunction", "COUNT"));
        when(queryEngine.getById(eq(componentsDef), eq("comp-single")))
            .thenReturn(Optional.of(component));

        WidgetResult widgetResult = new WidgetResult("metric",
            Map.of("value", 99L), null, null);
        when(dashboardDataService.executeWidget(eq(component), any()))
            .thenReturn(widgetResult);

        ResponseEntity<Map<String, Object>> response = controller.executeComponent(
            "dash-1", "comp-single", Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> attrs = getAttributes(response.getBody());
        assertEquals("comp-single", attrs.get("componentId"));
        assertEquals("dash-1", attrs.get("dashboardId"));
        assertEquals("metric", attrs.get("type"));
    }

    @Test
    void shouldReturn404WhenComponentNotFound() {
        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);
        when(queryEngine.getById(eq(componentsDef), eq("nonexistent")))
            .thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.executeComponent(
            "dash-1", "nonexistent", null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn404WhenComponentBelongsToDifferentDashboard() {
        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);

        Map<String, Object> component = new HashMap<>();
        component.put("id", "comp-other");
        component.put("dashboardId", "dash-other");
        when(queryEngine.getById(eq(componentsDef), eq("comp-other")))
            .thenReturn(Optional.of(component));

        ResponseEntity<Map<String, Object>> response = controller.executeComponent(
            "dash-1", "comp-other", null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn400WhenWidgetExecutionFails() {
        CollectionDefinition componentsDef = SystemCollectionDefinitions.dashboardComponents();
        when(collectionRegistry.get("dashboard-components")).thenReturn(componentsDef);

        Map<String, Object> component = new HashMap<>();
        component.put("id", "comp-bad");
        component.put("dashboardId", "dash-1");
        component.put("componentType", "metric");
        when(queryEngine.getById(eq(componentsDef), eq("comp-bad")))
            .thenReturn(Optional.of(component));

        when(dashboardDataService.executeWidget(eq(component), any()))
            .thenThrow(new WidgetExecutionException("Invalid config"));

        ResponseEntity<Map<String, Object>> response = controller.executeComponent(
            "dash-1", "comp-bad", Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturn404WhenComponentsCollectionMissing() {
        when(collectionRegistry.get("dashboard-components")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.executeComponent(
            "dash-1", "comp-1", null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setupDashboardRegistry() {
        CollectionDefinition dashboardsDef = SystemCollectionDefinitions.dashboards();
        when(collectionRegistry.get("dashboards")).thenReturn(dashboardsDef);
    }
}
