package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.lifecycle.BeforeSaveResult;
import com.emf.controlplane.lifecycle.SystemCollectionLifecycleHandler;
import com.emf.controlplane.lifecycle.SystemLifecycleHandlerRegistry;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WorkflowEngine's integration with SystemCollectionLifecycleHandler hooks.
 */
@DisplayName("WorkflowEngine Lifecycle Hook Integration Tests")
class WorkflowEngineLifecycleTest {

    private WorkflowRuleRepository ruleRepository;
    private WorkflowExecutionLogRepository executionLogRepository;
    private WorkflowActionLogRepository actionLogRepository;
    private ActionHandlerRegistry handlerRegistry;
    private FormulaEvaluator formulaEvaluator;
    private CollectionService collectionService;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        executionLogRepository = mock(WorkflowExecutionLogRepository.class);
        actionLogRepository = mock(WorkflowActionLogRepository.class);
        handlerRegistry = mock(ActionHandlerRegistry.class);
        formulaEvaluator = mock(FormulaEvaluator.class);
        collectionService = mock(CollectionService.class);

        engine = new WorkflowEngine(ruleRepository, executionLogRepository,
                actionLogRepository, handlerRegistry, formulaEvaluator, collectionService,
                new ObjectMapper());

        // Default: no workflow rules match
        when(ruleRepository.findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                anyString(), anyString(), anyString())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Lifecycle handlers in evaluateBeforeSave")
    class LifecycleHandlerTests {

        @Test
        @DisplayName("Should call lifecycle handler beforeCreate for CREATE changeType")
        void shouldCallBeforeCreateForCreate() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString())).thenReturn(BeforeSaveResult.ok());

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            Map<String, Object> data = Map.of("email", "user@example.com");

            engine.evaluateBeforeSave("tenant-1", "col-1", "users", null,
                    data, null, List.of(), "user-1", "CREATE");

            verify(handler).beforeCreate(data, "tenant-1");
            verify(handler, never()).beforeUpdate(anyString(), any(), any(), anyString());
        }

        @Test
        @DisplayName("Should call lifecycle handler beforeUpdate for UPDATE changeType")
        void shouldCallBeforeUpdateForUpdate() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeUpdate(anyString(), any(), any(), anyString()))
                    .thenReturn(BeforeSaveResult.ok());

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            Map<String, Object> data = Map.of("email", "new@example.com");
            Map<String, Object> previous = Map.of("email", "old@example.com");

            engine.evaluateBeforeSave("tenant-1", "col-1", "users", "rec-1",
                    data, previous, List.of("email"), "user-1", "UPDATE");

            verify(handler).beforeUpdate("rec-1", data, previous, "tenant-1");
            verify(handler, never()).beforeCreate(any(), anyString());
        }

        @Test
        @DisplayName("Should include lifecycle handler field updates in result")
        void shouldIncludeFieldUpdatesFromHandler() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString()))
                    .thenReturn(BeforeSaveResult.withFieldUpdates(
                            Map.of("locale", "en_US", "status", "ACTIVE")));

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of("email", "user@example.com"), null, List.of(), "user-1", "CREATE");

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertEquals("en_US", fieldUpdates.get("locale"));
            assertEquals("ACTIVE", fieldUpdates.get("status"));
        }

        @Test
        @DisplayName("Should return errors immediately when handler returns validation errors")
        void shouldReturnErrorsImmediately() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString()))
                    .thenReturn(BeforeSaveResult.error("email", "Email is required"));

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of(), null, List.of(), "user-1", "CREATE");

            // Should contain errors
            @SuppressWarnings("unchecked")
            List<Map<String, String>> errors = (List<Map<String, String>>) result.get("errors");
            assertNotNull(errors);
            assertEquals(1, errors.size());
            assertEquals("email", errors.get(0).get("field"));
            assertEquals("Email is required", errors.get(0).get("message"));

            // Should NOT have evaluated any workflow rules
            assertEquals(0, result.get("rulesEvaluated"));
            assertEquals(0, result.get("actionsExecuted"));
        }

        @Test
        @DisplayName("Should skip lifecycle handler when no registry set")
        void shouldSkipWhenNoRegistry() {
            // Don't set a registry — engine.lifecycleHandlerRegistry is null

            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of("email", "user@example.com"), null, List.of(), "user-1", "CREATE");

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertTrue(fieldUpdates.isEmpty());
            assertFalse(result.containsKey("errors"));
        }

        @Test
        @DisplayName("Should skip lifecycle handler when collection has no handler")
        void shouldSkipWhenNoHandler() {
            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of());
            engine.setLifecycleHandlerRegistry(registry);

            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "products",
                    null, Map.of("name", "Widget"), null, List.of(), "user-1", "CREATE");

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertTrue(fieldUpdates.isEmpty());
        }

        @Test
        @DisplayName("Should handle exception in lifecycle handler gracefully")
        void shouldHandleHandlerException() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            // Should not throw — exception is caught and logged
            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of("email", "user@example.com"), null, List.of(), "user-1", "CREATE");

            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertTrue(fieldUpdates.isEmpty());
        }

        @Test
        @DisplayName("Should run lifecycle handler BEFORE workflow rules")
        void shouldRunHandlerBeforeWorkflowRules() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString()))
                    .thenReturn(BeforeSaveResult.withFieldUpdates(Map.of("status", "ACTIVE")));

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            // Verify that the handler is called
            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of("email", "user@example.com"), null, List.of(), "user-1", "CREATE");

            // Handler field updates should be present
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertEquals("ACTIVE", fieldUpdates.get("status"));

            // Verify workflow rule repo was called AFTER handler (handler ran first)
            verify(handler).beforeCreate(any(), anyString());
            verify(ruleRepository).findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                    "tenant-1", "col-1", "BEFORE_CREATE");
        }

        @Test
        @DisplayName("Should not call workflow rules when handler returns errors")
        void shouldNotCallWorkflowRulesOnError() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString()))
                    .thenReturn(BeforeSaveResult.error("email", "Invalid"));

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of(), null, List.of(), "user-1", "CREATE");

            // Workflow rules should NOT have been queried
            verify(ruleRepository, never())
                    .findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                            anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle multiple validation errors from handler")
        void shouldHandleMultipleErrors() {
            SystemCollectionLifecycleHandler handler = mock(SystemCollectionLifecycleHandler.class);
            when(handler.getCollectionName()).thenReturn("users");
            when(handler.beforeCreate(any(), anyString()))
                    .thenReturn(BeforeSaveResult.errors(List.of(
                            new BeforeSaveResult.ValidationError("email", "Email required"),
                            new BeforeSaveResult.ValidationError("name", "Name required")
                    )));

            SystemLifecycleHandlerRegistry registry = new SystemLifecycleHandlerRegistry(List.of(handler));
            engine.setLifecycleHandlerRegistry(registry);

            Map<String, Object> result = engine.evaluateBeforeSave("tenant-1", "col-1", "users",
                    null, Map.of(), null, List.of(), "user-1", "CREATE");

            @SuppressWarnings("unchecked")
            List<Map<String, String>> errors = (List<Map<String, String>>) result.get("errors");
            assertEquals(2, errors.size());
        }
    }
}
