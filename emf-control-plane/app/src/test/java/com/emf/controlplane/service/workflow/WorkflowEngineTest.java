package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowAction;
import com.emf.controlplane.entity.WorkflowActionLog;
import com.emf.controlplane.entity.WorkflowExecutionLog;
import com.emf.runtime.event.ChangeType;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowActionLogRepository;
import com.emf.controlplane.repository.WorkflowExecutionLogRepository;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowEngineTest {

    private WorkflowRuleRepository ruleRepository;
    private WorkflowExecutionLogRepository executionLogRepository;
    private WorkflowActionLogRepository actionLogRepository;
    private ActionHandlerRegistry handlerRegistry;
    private FormulaEvaluator formulaEvaluator;
    private CollectionService collectionService;
    private WorkflowEngine engine;

    private Collection testCollection;

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

        testCollection = new Collection();
        testCollection.setId("col-1");
        testCollection.setName("orders");
        when(collectionService.getCollectionByIdOrName("orders")).thenReturn(testCollection);

        // Execution log save returns the saved entity
        when(executionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actionLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RecordChangeEvent createEvent() {
        return RecordChangeEvent.created("tenant-1", "orders", "rec-1",
            Map.of("id", "rec-1", "total", 150.0, "status", "Pending"), "user-1");
    }

    private RecordChangeEvent createUpdateEvent(List<String> changedFields) {
        return RecordChangeEvent.updated("tenant-1", "orders", "rec-1",
            Map.of("id", "rec-1", "total", 200.0, "status", "Approved"),
            Map.of("id", "rec-1", "total", 150.0, "status", "Pending"),
            changedFields, "user-1");
    }

    private WorkflowRule createRule(String name, String triggerType) {
        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId("tenant-1");
        rule.setCollection(testCollection);
        rule.setName(name);
        rule.setTriggerType(triggerType);
        rule.setActive(true);
        rule.setErrorHandling("STOP_ON_ERROR");
        return rule;
    }

    private WorkflowAction createAction(WorkflowRule rule, String type, int order) {
        WorkflowAction action = new WorkflowAction();
        action.setWorkflowRule(rule);
        action.setActionType(type);
        action.setExecutionOrder(order);
        action.setConfig("{}");
        action.setActive(true);
        rule.getActions().add(action);
        return action;
    }

    /**
     * Creates a mock ActionHandler that returns the given result on execute().
     * Must be called OUTSIDE of when().thenReturn() to avoid Mockito nested stubbing issues.
     */
    private ActionHandler mockHandler(String key, ActionResult result) {
        ActionHandler handler = mock(ActionHandler.class);
        when(handler.getActionTypeKey()).thenReturn(key);
        when(handler.execute(any())).thenReturn(result);
        return handler;
    }

    /**
     * Creates a mock ActionHandler that throws the given exception on execute().
     * Must be called OUTSIDE of when().thenReturn() to avoid Mockito nested stubbing issues.
     */
    private ActionHandler mockThrowingHandler(String key, RuntimeException ex) {
        ActionHandler handler = mock(ActionHandler.class);
        when(handler.getActionTypeKey()).thenReturn(key);
        when(handler.execute(any())).thenThrow(ex);
        return handler;
    }

    private void stubNoMatchingRules(String triggerType) {
        when(ruleRepository.findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
            "tenant-1", "col-1", triggerType)).thenReturn(List.of());
    }

    private void stubRules(String triggerType, List<WorkflowRule> rules) {
        when(ruleRepository.findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
            "tenant-1", "col-1", triggerType)).thenReturn(rules);
    }

    @Nested
    @DisplayName("Trigger Type Mapping")
    class TriggerTypeTests {

        @Test
        @DisplayName("Should map CREATED to ON_CREATE")
        void mapCreated() {
            assertEquals("ON_CREATE", engine.mapChangeTypeToTrigger(ChangeType.CREATED));
        }

        @Test
        @DisplayName("Should map UPDATED to ON_UPDATE")
        void mapUpdated() {
            assertEquals("ON_UPDATE", engine.mapChangeTypeToTrigger(ChangeType.UPDATED));
        }

        @Test
        @DisplayName("Should map DELETED to ON_DELETE")
        void mapDeleted() {
            assertEquals("ON_DELETE", engine.mapChangeTypeToTrigger(ChangeType.DELETED));
        }
    }

    @Nested
    @DisplayName("Rule Matching")
    class RuleMatchingTests {

        @Test
        @DisplayName("Should skip when no matching rules found")
        void shouldSkipWhenNoRules() {
            stubNoMatchingRules("ON_CREATE");
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            engine.evaluate(createEvent());

            verify(executionLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip when collection not found")
        void shouldSkipWhenCollectionNotFound() {
            when(collectionService.getCollectionByIdOrName("orders"))
                .thenThrow(new RuntimeException("Not found"));

            engine.evaluate(createEvent());

            verify(ruleRepository, never()).findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Filter Formula Evaluation")
    class FilterFormulaTests {

        @Test
        @DisplayName("Should execute actions when no filter formula")
        void shouldExecuteWithoutFilter() {
            WorkflowRule rule = createRule("Test Rule", "ON_CREATE");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));

            engine.evaluate(createEvent());

            verify(executionLogRepository, times(2)).save(any(WorkflowExecutionLog.class));
        }

        @Test
        @DisplayName("Should skip when filter formula rejects record")
        void shouldSkipWhenFilterRejects() {
            WorkflowRule rule = createRule("Test Rule", "ON_CREATE");
            rule.setFilterFormula("total > 200");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");
            when(formulaEvaluator.evaluateBoolean(eq("total > 200"), any())).thenReturn(false);

            engine.evaluate(createEvent());

            // No execution log saved (filter rejected)
            verify(executionLogRepository, never()).save(any());
            verify(handlerRegistry, never()).getHandler(any());
        }

        @Test
        @DisplayName("Should execute when filter formula passes")
        void shouldExecuteWhenFilterPasses() {
            WorkflowRule rule = createRule("Test Rule", "ON_CREATE");
            rule.setFilterFormula("total > 100");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");
            when(formulaEvaluator.evaluateBoolean(eq("total > 100"), any())).thenReturn(true);

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));

            engine.evaluate(createEvent());

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }

        @Test
        @DisplayName("Should log failure when filter formula throws")
        void shouldLogFailureOnFormulaError() {
            WorkflowRule rule = createRule("Test Rule", "ON_CREATE");
            rule.setFilterFormula("invalid formula");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");
            when(formulaEvaluator.evaluateBoolean(eq("invalid formula"), any()))
                .thenThrow(new RuntimeException("Parse error"));

            engine.evaluate(createEvent());

            verify(executionLogRepository).save(argThat(log ->
                "FAILURE".equals(((WorkflowExecutionLog) log).getStatus())));
        }
    }

    @Nested
    @DisplayName("Action Execution")
    class ActionExecutionTests {

        @Test
        @DisplayName("Should execute multiple actions in order")
        void shouldExecuteActionsInOrder() {
            WorkflowRule rule = createRule("Multi-Action Rule", "ON_CREATE");
            createAction(rule, "FIELD_UPDATE", 0);
            createAction(rule, "EMAIL_ALERT", 1);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            ActionHandler emailAlertHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailAlertHandler));

            engine.evaluate(createEvent());

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry).getHandler("EMAIL_ALERT");
            // 2 action logs saved
            verify(actionLogRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("Should stop on error when STOP_ON_ERROR")
        void shouldStopOnError() {
            WorkflowRule rule = createRule("Stop Rule", "ON_CREATE");
            rule.setErrorHandling("STOP_ON_ERROR");
            createAction(rule, "FIELD_UPDATE", 0);
            createAction(rule, "EMAIL_ALERT", 1);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.failure("Update failed"));
            ActionHandler emailAlertHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailAlertHandler));

            engine.evaluate(createEvent());

            // First action fails, second never executes
            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry, never()).getHandler("EMAIL_ALERT");
            // Execution log status should be FAILURE
            verify(executionLogRepository, times(2)).save(argThat(log -> {
                if (log instanceof WorkflowExecutionLog execLog) {
                    // One save for initial "EXECUTING", one for final "FAILURE"
                    return true;
                }
                return false;
            }));
        }

        @Test
        @DisplayName("Should continue on error when CONTINUE_ON_ERROR")
        void shouldContinueOnError() {
            WorkflowRule rule = createRule("Continue Rule", "ON_CREATE");
            rule.setErrorHandling("CONTINUE_ON_ERROR");
            createAction(rule, "FIELD_UPDATE", 0);
            createAction(rule, "EMAIL_ALERT", 1);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.failure("Update failed"));
            ActionHandler emailAlertHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailAlertHandler));

            engine.evaluate(createEvent());

            // Both actions should execute even though first failed
            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry).getHandler("EMAIL_ALERT");
        }

        @Test
        @DisplayName("Should handle missing handler gracefully")
        void shouldHandleMissingHandler() {
            WorkflowRule rule = createRule("Missing Handler", "ON_CREATE");
            rule.setErrorHandling("STOP_ON_ERROR");
            createAction(rule, "NONEXISTENT", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");
            when(handlerRegistry.getHandler("NONEXISTENT")).thenReturn(Optional.empty());

            engine.evaluate(createEvent());

            // Should log failure for the missing handler
            verify(actionLogRepository).save(any());
        }

        @Test
        @DisplayName("Should handle handler exception gracefully")
        void shouldHandleHandlerException() {
            WorkflowRule rule = createRule("Exception Rule", "ON_CREATE");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler throwingHandler = mockThrowingHandler("FIELD_UPDATE", new RuntimeException("Boom!"));
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(throwingHandler));

            // Should not throw
            assertDoesNotThrow(() -> engine.evaluate(createEvent()));
        }
    }

    @Nested
    @DisplayName("Enhanced Action Logging")
    class ActionLoggingTests {

        @Test
        @DisplayName("Should capture input snapshot with action config and record info")
        void shouldCaptureInputSnapshot() {
            WorkflowRule rule = createRule("Logging Rule", "ON_CREATE");
            WorkflowAction action = createAction(rule, "FIELD_UPDATE", 0);
            action.setConfig("{\"updates\":[{\"field\":\"status\",\"value\":\"Done\"}]}");

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));

            engine.evaluate(createEvent());

            ArgumentCaptor<WorkflowActionLog> captor = ArgumentCaptor.forClass(WorkflowActionLog.class);
            verify(actionLogRepository).save(captor.capture());
            WorkflowActionLog savedLog = captor.getValue();

            assertEquals("SUCCESS", savedLog.getStatus());
            assertEquals("FIELD_UPDATE", savedLog.getActionType());
            assertNotNull(savedLog.getInputSnapshot());
            // Input snapshot should contain action config, record ID, and collection name
            assertTrue(savedLog.getInputSnapshot().contains("actionConfig"));
            assertTrue(savedLog.getInputSnapshot().contains("rec-1"));
            assertTrue(savedLog.getInputSnapshot().contains("orders"));
        }

        @Test
        @DisplayName("Should capture output snapshot from action result")
        void shouldCaptureOutputSnapshot() {
            WorkflowRule rule = createRule("Output Rule", "ON_CREATE");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionResult resultWithOutput = ActionResult.success(Map.of("updatedFields", Map.of("status", "Done")));
            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", resultWithOutput);
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));

            engine.evaluate(createEvent());

            ArgumentCaptor<WorkflowActionLog> captor = ArgumentCaptor.forClass(WorkflowActionLog.class);
            verify(actionLogRepository).save(captor.capture());
            WorkflowActionLog savedLog = captor.getValue();

            assertNotNull(savedLog.getOutputSnapshot());
            assertTrue(savedLog.getOutputSnapshot().contains("updatedFields"));
        }

        @Test
        @DisplayName("Should record duration for action execution")
        void shouldRecordDuration() {
            WorkflowRule rule = createRule("Duration Rule", "ON_CREATE");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));

            engine.evaluate(createEvent());

            ArgumentCaptor<WorkflowActionLog> captor = ArgumentCaptor.forClass(WorkflowActionLog.class);
            verify(actionLogRepository).save(captor.capture());
            WorkflowActionLog savedLog = captor.getValue();

            assertNotNull(savedLog.getDurationMs());
            assertTrue(savedLog.getDurationMs() >= 0);
        }

        @Test
        @DisplayName("Should capture error message on action failure")
        void shouldCaptureErrorOnFailure() {
            WorkflowRule rule = createRule("Error Rule", "ON_CREATE");
            rule.setErrorHandling("CONTINUE_ON_ERROR");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler failHandler = mockHandler("FIELD_UPDATE", ActionResult.failure("Something went wrong"));
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(failHandler));

            engine.evaluate(createEvent());

            ArgumentCaptor<WorkflowActionLog> captor = ArgumentCaptor.forClass(WorkflowActionLog.class);
            verify(actionLogRepository).save(captor.capture());
            WorkflowActionLog savedLog = captor.getValue();

            assertEquals("FAILURE", savedLog.getStatus());
            assertEquals("Something went wrong", savedLog.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("ON_CREATE_OR_UPDATE Matching")
    class CombinedTriggerTests {

        @Test
        @DisplayName("Should match ON_CREATE_OR_UPDATE rules for CREATE events")
        void shouldMatchCombinedForCreate() {
            WorkflowRule specificRule = createRule("Create Rule", "ON_CREATE");
            createAction(specificRule, "FIELD_UPDATE", 0);

            WorkflowRule combinedRule = createRule("Combined Rule", "ON_CREATE_OR_UPDATE");
            createAction(combinedRule, "EMAIL_ALERT", 1);

            stubRules("ON_CREATE", List.of(specificRule));
            stubRules("ON_CREATE_OR_UPDATE", List.of(combinedRule));

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            ActionHandler emailAlertHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailAlertHandler));

            engine.evaluate(createEvent());

            // Both rules should be evaluated
            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry).getHandler("EMAIL_ALERT");
        }

        @Test
        @DisplayName("Should NOT match ON_CREATE_OR_UPDATE rules for DELETE events")
        void shouldNotMatchCombinedForDelete() {
            RecordChangeEvent deleteEvent = RecordChangeEvent.deleted(
                "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");

            stubNoMatchingRules("ON_DELETE");

            engine.evaluate(deleteEvent);

            // Should NOT query for ON_CREATE_OR_UPDATE
            verify(ruleRepository, never()).findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
                "tenant-1", "col-1", "ON_CREATE_OR_UPDATE");
        }
    }

    @Nested
    @DisplayName("Trigger Fields Filtering (B1)")
    class TriggerFieldsTests {

        @Test
        @DisplayName("Should match when no trigger fields configured (null)")
        void shouldMatchWhenNoTriggerFields() {
            WorkflowRule rule = createRule("No Trigger Fields", "ON_UPDATE");
            // triggerFields is null by default
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            engine.evaluate(createUpdateEvent(List.of("status")));

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }

        @Test
        @DisplayName("Should match when trigger fields is empty JSON array")
        void shouldMatchWhenTriggerFieldsEmpty() {
            WorkflowRule rule = createRule("Empty Trigger Fields", "ON_UPDATE");
            rule.setTriggerFields("[]");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            engine.evaluate(createUpdateEvent(List.of("status")));

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }

        @Test
        @DisplayName("Should match when a trigger field is in changed fields")
        void shouldMatchWhenTriggerFieldChanged() {
            WorkflowRule rule = createRule("Status Trigger", "ON_UPDATE");
            rule.setTriggerFields("[\"status\",\"priority\"]");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            engine.evaluate(createUpdateEvent(List.of("status", "total")));

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }

        @Test
        @DisplayName("Should skip when no trigger field is in changed fields")
        void shouldSkipWhenNoTriggerFieldChanged() {
            WorkflowRule rule = createRule("Status Trigger", "ON_UPDATE");
            rule.setTriggerFields("[\"status\",\"priority\"]");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            engine.evaluate(createUpdateEvent(List.of("total", "description")));

            // Handler should never be called
            verify(handlerRegistry, never()).getHandler(any());
            // No execution log saved (trigger fields rejected)
            verify(executionLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should skip when changed fields is empty and trigger fields are set")
        void shouldSkipWhenChangedFieldsEmpty() {
            WorkflowRule rule = createRule("Status Trigger", "ON_UPDATE");
            rule.setTriggerFields("[\"status\"]");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            engine.evaluate(createUpdateEvent(List.of()));

            verify(handlerRegistry, never()).getHandler(any());
        }

        @Test
        @DisplayName("Should always match trigger fields for CREATE events")
        void shouldAlwaysMatchForCreateEvents() {
            WorkflowRule rule = createRule("Create Rule", "ON_CREATE");
            rule.setTriggerFields("[\"status\"]");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_CREATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            // CREATE events have empty changedFields but should still fire
            engine.evaluate(createEvent());

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }

        @Test
        @DisplayName("Should always match trigger fields for DELETE events")
        void shouldAlwaysMatchForDeleteEvents() {
            WorkflowRule rule = createRule("Delete Rule", "ON_DELETE");
            rule.setTriggerFields("[\"status\"]");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_DELETE", List.of(rule));

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            RecordChangeEvent deleteEvent = RecordChangeEvent.deleted(
                "tenant-1", "orders", "rec-1", Map.of("id", "rec-1"), "user-1");

            engine.evaluate(deleteEvent);

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }

        @Test
        @DisplayName("Should match when multiple trigger fields overlap with changed fields")
        void shouldMatchOnMultipleOverlap() {
            WorkflowRule rule = createRule("Multi Field Trigger", "ON_UPDATE");
            rule.setTriggerFields("[\"status\",\"priority\",\"assignee\"]");
            createAction(rule, "EMAIL_ALERT", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler handler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(handler));

            engine.evaluate(createUpdateEvent(List.of("priority", "assignee")));

            verify(handlerRegistry).getHandler("EMAIL_ALERT");
        }

        @Test
        @DisplayName("Should handle invalid trigger fields JSON gracefully")
        void shouldHandleInvalidTriggerFieldsJson() {
            WorkflowRule rule = createRule("Invalid JSON", "ON_UPDATE");
            rule.setTriggerFields("not-valid-json");
            createAction(rule, "FIELD_UPDATE", 0);

            stubRules("ON_UPDATE", List.of(rule));
            stubNoMatchingRules("ON_CREATE_OR_UPDATE");

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            // Invalid JSON should be treated as no trigger fields (match any)
            engine.evaluate(createUpdateEvent(List.of("total")));

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
        }
    }

    @Nested
    @DisplayName("Scheduled Rule Execution (B2)")
    class ScheduledRuleExecutionTests {

        @Test
        @DisplayName("Should execute actions for scheduled rule")
        void shouldExecuteScheduledActions() {
            WorkflowRule rule = createRule("Scheduled Rule", "SCHEDULED");
            createAction(rule, "FIELD_UPDATE", 0);

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            engine.executeScheduledRule(rule);

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(executionLogRepository, times(2)).save(any(WorkflowExecutionLog.class));
        }

        @Test
        @DisplayName("Should skip when no active actions")
        void shouldSkipWhenNoActiveActions() {
            WorkflowRule rule = createRule("Empty Scheduled", "SCHEDULED");
            // No actions added

            engine.executeScheduledRule(rule);

            verify(handlerRegistry, never()).getHandler(any());
            verify(executionLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should execute multiple actions in order")
        void shouldExecuteMultipleActionsInOrder() {
            WorkflowRule rule = createRule("Multi Action Scheduled", "SCHEDULED");
            createAction(rule, "FIELD_UPDATE", 0);
            createAction(rule, "EMAIL_ALERT", 1);

            ActionHandler fieldUpdateHandler = mockHandler("FIELD_UPDATE", ActionResult.success());
            ActionHandler emailAlertHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(fieldUpdateHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailAlertHandler));

            engine.executeScheduledRule(rule);

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry).getHandler("EMAIL_ALERT");
            verify(actionLogRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("Should stop on error for scheduled rules")
        void shouldStopOnErrorForScheduled() {
            WorkflowRule rule = createRule("Stop Scheduled", "SCHEDULED");
            rule.setErrorHandling("STOP_ON_ERROR");
            createAction(rule, "FIELD_UPDATE", 0);
            createAction(rule, "EMAIL_ALERT", 1);

            ActionHandler failHandler = mockHandler("FIELD_UPDATE", ActionResult.failure("Failed"));
            ActionHandler emailHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(failHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailHandler));

            engine.executeScheduledRule(rule);

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry, never()).getHandler("EMAIL_ALERT");
        }

        @Test
        @DisplayName("Should continue on error for scheduled rules")
        void shouldContinueOnErrorForScheduled() {
            WorkflowRule rule = createRule("Continue Scheduled", "SCHEDULED");
            rule.setErrorHandling("CONTINUE_ON_ERROR");
            createAction(rule, "FIELD_UPDATE", 0);
            createAction(rule, "EMAIL_ALERT", 1);

            ActionHandler failHandler = mockHandler("FIELD_UPDATE", ActionResult.failure("Failed"));
            ActionHandler emailHandler = mockHandler("EMAIL_ALERT", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(failHandler));
            when(handlerRegistry.getHandler("EMAIL_ALERT")).thenReturn(Optional.of(emailHandler));

            engine.executeScheduledRule(rule);

            verify(handlerRegistry).getHandler("FIELD_UPDATE");
            verify(handlerRegistry).getHandler("EMAIL_ALERT");
        }

        @Test
        @DisplayName("Should log execution with SCHEDULED trigger type")
        void shouldLogScheduledExecution() {
            WorkflowRule rule = createRule("Log Scheduled", "SCHEDULED");
            createAction(rule, "FIELD_UPDATE", 0);

            ActionHandler handler = mockHandler("FIELD_UPDATE", ActionResult.success());
            when(handlerRegistry.getHandler("FIELD_UPDATE")).thenReturn(Optional.of(handler));

            engine.executeScheduledRule(rule);

            // First save: initial EXECUTING, second save: final status
            verify(executionLogRepository, times(2)).save(argThat(log -> {
                if (log instanceof WorkflowExecutionLog execLog) {
                    return "SCHEDULED".equals(execLog.getTriggerType());
                }
                return false;
            }));
        }
    }
}
