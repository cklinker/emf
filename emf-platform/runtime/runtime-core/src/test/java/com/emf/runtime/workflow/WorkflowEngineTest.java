package com.emf.runtime.workflow;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WorkflowEngine")
class WorkflowEngineTest {

    private WorkflowStore store;
    private ActionHandlerRegistry handlerRegistry;
    private FormulaEvaluator formulaEvaluator;
    private ObjectMapper objectMapper;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        store = mock(WorkflowStore.class);
        handlerRegistry = new ActionHandlerRegistry();
        formulaEvaluator = new FormulaEvaluator(List.of()) {
            @Override
            public boolean evaluateBoolean(String expression, Map<String, Object> context) {
                return "true".equals(expression);
            }
        };
        objectMapper = new ObjectMapper();
        engine = new WorkflowEngine(store, handlerRegistry, formulaEvaluator, objectMapper);
    }

    // ---- Helper methods ----

    private RecordChangeEvent createEvent(ChangeType changeType) {
        return createEvent(changeType, Map.of("status", "Active"), null, List.of());
    }

    private RecordChangeEvent createEvent(ChangeType changeType, Map<String, Object> data,
                                           Map<String, Object> previousData, List<String> changedFields) {
        return new RecordChangeEvent(
            "evt-1", "t1", "orders", "r1", changeType,
            data, previousData, changedFields, "user-1", Instant.now());
    }

    private WorkflowRuleData createRule(String triggerType) {
        return createRule("rule-1", triggerType, null, null, "STOP_ON_ERROR",
            List.of(WorkflowActionData.of("act-1", "FIELD_UPDATE", 0, "{}", true)));
    }

    private WorkflowRuleData createRule(String id, String triggerType, String filterFormula,
                                         List<String> triggerFields, String errorHandling,
                                         List<WorkflowActionData> actions) {
        return new WorkflowRuleData(id, "t1", "col-1", "orders", "Test Rule", null,
            true, triggerType, filterFormula, false, 0, errorHandling,
            triggerFields, null, null, null, "SEQUENTIAL", actions);
    }

    private void registerHandler(String key, ActionResult result) {
        handlerRegistry.register(new ActionHandler() {
            @Override
            public String getActionTypeKey() { return key; }
            @Override
            public ActionResult execute(ActionContext context) { return result; }
        });
    }

    // ---- Tests ----

    @Nested
    @DisplayName("evaluate")
    class EvaluateTests {

        @Test
        @DisplayName("Should skip when no matching rules found")
        void shouldSkipWhenNoRules() {
            when(store.findActiveRules("t1", "orders", "ON_CREATE")).thenReturn(List.of());

            engine.evaluate(createEvent(ChangeType.CREATED));

            verify(store, never()).createExecutionLog(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should also include ON_CREATE_OR_UPDATE rules for creates")
        void shouldIncludeCombinedRulesForCreate() {
            WorkflowRuleData specificRule = createRule("ON_CREATE");
            WorkflowRuleData combinedRule = createRule("rule-2", "ON_CREATE_OR_UPDATE", null, null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-2", "FIELD_UPDATE", 0, "{}", true)));

            when(store.findActiveRules("t1", "orders", "ON_CREATE"))
                .thenReturn(List.of(specificRule));
            when(store.findActiveRules("t1", "orders", "ON_CREATE_OR_UPDATE"))
                .thenReturn(List.of(combinedRule));
            when(store.createExecutionLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-1");

            registerHandler("FIELD_UPDATE", ActionResult.success());
            engine.evaluate(createEvent(ChangeType.CREATED));

            verify(store, times(2)).createExecutionLog(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should not include ON_CREATE_OR_UPDATE rules for deletes")
        void shouldNotIncludeCombinedRulesForDelete() {
            when(store.findActiveRules("t1", "orders", "ON_DELETE")).thenReturn(List.of());

            engine.evaluate(createEvent(ChangeType.DELETED));

            verify(store, never()).findActiveRules("t1", "orders", "ON_CREATE_OR_UPDATE");
        }
    }

    @Nested
    @DisplayName("evaluateRule")
    class EvaluateRuleTests {

        @Test
        @DisplayName("Should execute actions when filter passes")
        void shouldExecuteActionsWhenFilterPasses() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", "true", null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true)));

            when(store.createExecutionLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-1");

            registerHandler("LOG_MESSAGE", ActionResult.success());
            engine.evaluateRule(rule, createEvent(ChangeType.CREATED));

            verify(store).updateExecutionLog(eq("log-1"), eq("SUCCESS"), eq(1), isNull(), anyInt());
        }

        @Test
        @DisplayName("Should skip when filter formula rejects")
        void shouldSkipWhenFilterRejects() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", "false", null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true)));

            engine.evaluateRule(rule, createEvent(ChangeType.CREATED));

            verify(store, never()).createExecutionLog(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should log failure when filter formula errors")
        void shouldLogFailureWhenFilterErrors() {
            FormulaEvaluator errorEvaluator = new FormulaEvaluator(List.of()) {
                @Override
                public boolean evaluateBoolean(String expression, Map<String, Object> context) {
                    throw new RuntimeException("Bad formula");
                }
            };
            WorkflowEngine errorEngine = new WorkflowEngine(store, handlerRegistry, errorEvaluator, objectMapper);

            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", "bad()", null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true)));
            when(store.createExecutionLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-fail");

            errorEngine.evaluateRule(rule, createEvent(ChangeType.CREATED));

            verify(store).updateExecutionLog(eq("log-fail"), eq("FAILURE"), eq(0),
                argThat(s -> s != null && s.contains("Filter formula error")), anyInt());
        }

        @Test
        @DisplayName("Should stop on error when configured")
        void shouldStopOnError() {
            List<WorkflowActionData> actions = List.of(
                WorkflowActionData.of("act-1", "FAIL_ACTION", 0, "{}", true),
                WorkflowActionData.of("act-2", "LOG_MESSAGE", 1, "{}", true)
            );
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", actions);

            when(store.createExecutionLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-1");

            registerHandler("FAIL_ACTION", ActionResult.failure("boom"));
            registerHandler("LOG_MESSAGE", ActionResult.success());

            engine.evaluateRule(rule, createEvent(ChangeType.CREATED));

            verify(store).updateExecutionLog(eq("log-1"), eq("FAILURE"), eq(1),
                argThat(s -> s != null && s.contains("Action 'FAIL_ACTION' failed")), anyInt());
        }

        @Test
        @DisplayName("Should continue on error when configured")
        void shouldContinueOnError() {
            List<WorkflowActionData> actions = List.of(
                WorkflowActionData.of("act-1", "FAIL_ACTION", 0, "{}", true),
                WorkflowActionData.of("act-2", "LOG_MESSAGE", 1, "{}", true)
            );
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "CONTINUE_ON_ERROR", actions);

            when(store.createExecutionLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-1");

            registerHandler("FAIL_ACTION", ActionResult.failure("boom"));
            registerHandler("LOG_MESSAGE", ActionResult.success());

            engine.evaluateRule(rule, createEvent(ChangeType.CREATED));

            verify(store).updateExecutionLog(eq("log-1"), eq("PARTIAL_FAILURE"), eq(2),
                argThat(s -> s != null && s.contains("FAIL_ACTION")), anyInt());
        }

        @Test
        @DisplayName("Should skip rule with no active actions")
        void shouldSkipRuleWithNoActiveActions() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", List.of(new WorkflowActionData("act-1", "LOG_MESSAGE", 0, "{}", false, 0, 60, "FIXED")));

            engine.evaluateRule(rule, createEvent(ChangeType.CREATED));

            verify(store, never()).createExecutionLog(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("matchesTriggerFields")
    class MatchesTriggerFieldsTests {

        @Test
        @DisplayName("Should match when no trigger fields configured")
        void shouldMatchWhenNoTriggerFields() {
            WorkflowRuleData rule = createRule("rule-1", "ON_UPDATE", null, null,
                "STOP_ON_ERROR", List.of());

            assertTrue(engine.matchesTriggerFields(rule, createEvent(ChangeType.UPDATED)));
        }

        @Test
        @DisplayName("Should match when trigger field is in changed fields")
        void shouldMatchWhenTriggerFieldChanged() {
            WorkflowRuleData rule = createRule("rule-1", "ON_UPDATE", null,
                List.of("status"), "STOP_ON_ERROR", List.of());

            RecordChangeEvent event = createEvent(ChangeType.UPDATED,
                Map.of("status", "Done"), Map.of("status", "Active"), List.of("status"));

            assertTrue(engine.matchesTriggerFields(rule, event));
        }

        @Test
        @DisplayName("Should not match when trigger field is not in changed fields")
        void shouldNotMatchWhenTriggerFieldNotChanged() {
            WorkflowRuleData rule = createRule("rule-1", "ON_UPDATE", null,
                List.of("status"), "STOP_ON_ERROR", List.of());

            RecordChangeEvent event = createEvent(ChangeType.UPDATED,
                Map.of("name", "New"), Map.of("name", "Old"), List.of("name"));

            assertFalse(engine.matchesTriggerFields(rule, event));
        }

        @Test
        @DisplayName("Should always match for CREATE events even with trigger fields")
        void shouldAlwaysMatchForCreateEvents() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null,
                List.of("status"), "STOP_ON_ERROR", List.of());

            assertTrue(engine.matchesTriggerFields(rule, createEvent(ChangeType.CREATED)));
        }

        @Test
        @DisplayName("Should always match for DELETE events even with trigger fields")
        void shouldAlwaysMatchForDeleteEvents() {
            WorkflowRuleData rule = createRule("rule-1", "ON_DELETE", null,
                List.of("status"), "STOP_ON_ERROR", List.of());

            assertTrue(engine.matchesTriggerFields(rule, createEvent(ChangeType.DELETED)));
        }

        @Test
        @DisplayName("Should not match when changed fields is empty")
        void shouldNotMatchWhenChangedFieldsEmpty() {
            WorkflowRuleData rule = createRule("rule-1", "ON_UPDATE", null,
                List.of("status"), "STOP_ON_ERROR", List.of());

            RecordChangeEvent event = createEvent(ChangeType.UPDATED,
                Map.of("status", "Active"), null, List.of());

            assertFalse(engine.matchesTriggerFields(rule, event));
        }
    }

    @Nested
    @DisplayName("mapChangeTypeToTrigger")
    class MapChangeTypeTests {

        @Test
        @DisplayName("Should map CREATED to ON_CREATE")
        void shouldMapCreated() {
            assertEquals("ON_CREATE", engine.mapChangeTypeToTrigger(ChangeType.CREATED));
        }

        @Test
        @DisplayName("Should map UPDATED to ON_UPDATE")
        void shouldMapUpdated() {
            assertEquals("ON_UPDATE", engine.mapChangeTypeToTrigger(ChangeType.UPDATED));
        }

        @Test
        @DisplayName("Should map DELETED to ON_DELETE")
        void shouldMapDeleted() {
            assertEquals("ON_DELETE", engine.mapChangeTypeToTrigger(ChangeType.DELETED));
        }
    }

    @Nested
    @DisplayName("executeScheduledRule")
    class ExecuteScheduledRuleTests {

        @Test
        @DisplayName("Should execute all active actions")
        void shouldExecuteAllActiveActions() {
            List<WorkflowActionData> actions = List.of(
                WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true),
                WorkflowActionData.of("act-2", "FIELD_UPDATE", 1, "{}", true)
            );
            WorkflowRuleData rule = createRule("rule-1", "SCHEDULED", null, null,
                "STOP_ON_ERROR", actions);

            when(store.createExecutionLog(anyString(), anyString(), isNull(), eq("SCHEDULED")))
                .thenReturn("log-1");

            registerHandler("LOG_MESSAGE", ActionResult.success());
            registerHandler("FIELD_UPDATE", ActionResult.success());

            engine.executeScheduledRule(rule);

            verify(store).updateExecutionLog(eq("log-1"), eq("SUCCESS"), eq(2), isNull(), anyInt());
            verify(store, times(2)).createActionLog(
                eq("log-1"), anyString(), anyString(), eq("SUCCESS"),
                isNull(), anyString(), isNull(), anyInt(), eq(1));
        }

        @Test
        @DisplayName("Should skip when no active actions")
        void shouldSkipWhenNoActiveActions() {
            WorkflowRuleData rule = createRule("rule-1", "SCHEDULED", null, null,
                "STOP_ON_ERROR", List.of());

            engine.executeScheduledRule(rule);

            verify(store, never()).createExecutionLog(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should stop on first error when configured")
        void shouldStopOnFirstError() {
            List<WorkflowActionData> actions = List.of(
                WorkflowActionData.of("act-1", "FAIL_ACTION", 0, "{}", true),
                WorkflowActionData.of("act-2", "LOG_MESSAGE", 1, "{}", true)
            );
            WorkflowRuleData rule = createRule("rule-1", "SCHEDULED", null, null,
                "STOP_ON_ERROR", actions);

            when(store.createExecutionLog(anyString(), anyString(), isNull(), eq("SCHEDULED")))
                .thenReturn("log-1");

            registerHandler("FAIL_ACTION", ActionResult.failure("scheduled fail"));
            registerHandler("LOG_MESSAGE", ActionResult.success());

            engine.executeScheduledRule(rule);

            verify(store).updateExecutionLog(eq("log-1"), eq("FAILURE"), eq(1),
                argThat(s -> s != null && s.contains("FAIL_ACTION")), anyInt());
        }
    }

    @Nested
    @DisplayName("executeManualRule")
    class ExecuteManualRuleTests {

        @Test
        @DisplayName("Should return execution log ID")
        void shouldReturnExecutionLogId() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true)));

            when(store.createExecutionLog("t1", "rule-1", "r1", "MANUAL"))
                .thenReturn("log-1");

            registerHandler("LOG_MESSAGE", ActionResult.success());

            String logId = engine.executeManualRule(rule, "r1", "user-1");

            assertEquals("log-1", logId);
            verify(store).updateExecutionLog(eq("log-1"), eq("SUCCESS"), eq(1), isNull(), anyInt());
        }

        @Test
        @DisplayName("Should return null when no active actions")
        void shouldReturnNullWhenNoActiveActions() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", List.of());

            assertNull(engine.executeManualRule(rule, "r1", "user-1"));
        }

        @Test
        @DisplayName("Should default user to system when null")
        void shouldDefaultUserToSystem() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true)));

            when(store.createExecutionLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("log-1");

            ActionHandler handler = mock(ActionHandler.class);
            when(handler.getActionTypeKey()).thenReturn("LOG_MESSAGE");
            when(handler.execute(any())).thenAnswer(inv -> {
                ActionContext ctx = inv.getArgument(0);
                assertEquals("system", ctx.userId());
                return ActionResult.success();
            });
            handlerRegistry.register(handler);

            engine.executeManualRule(rule, "r1", null);
        }
    }

    @Nested
    @DisplayName("evaluateBeforeSave")
    class EvaluateBeforeSaveTests {

        @Test
        @DisplayName("Should return empty when no matching rules")
        void shouldReturnEmptyWhenNoRules() {
            when(store.findActiveRules("t1", "orders", "BEFORE_CREATE")).thenReturn(List.of());

            Map<String, Object> result = engine.evaluateBeforeSave(
                "t1", "orders", null, Map.of("status", "Active"),
                null, List.of(), "user-1", "CREATE");

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertTrue(fieldUpdates.isEmpty());
            assertEquals(0, result.get("rulesEvaluated"));
            assertEquals(0, result.get("actionsExecuted"));
        }

        @Test
        @DisplayName("Should accumulate field updates from FIELD_UPDATE actions")
        void shouldAccumulateFieldUpdates() {
            WorkflowRuleData rule = createRule("rule-1", "BEFORE_CREATE", null, null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "FIELD_UPDATE", 0,
                    "{\"updates\": {\"status\": \"Processed\"}}", true)));

            when(store.findActiveRules("t1", "orders", "BEFORE_CREATE"))
                .thenReturn(List.of(rule));

            handlerRegistry.register(new ActionHandler() {
                @Override
                public String getActionTypeKey() { return "FIELD_UPDATE"; }
                @Override
                public ActionResult execute(ActionContext context) {
                    return ActionResult.success(Map.of("updatedFields",
                        Map.of("status", "Processed")));
                }
            });

            Map<String, Object> result = engine.evaluateBeforeSave(
                "t1", "orders", null, Map.of("status", "New"),
                null, List.of(), "user-1", "CREATE");

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldUpdates = (Map<String, Object>) result.get("fieldUpdates");
            assertEquals("Processed", fieldUpdates.get("status"));
            assertEquals(1, result.get("rulesEvaluated"));
            assertEquals(1, result.get("actionsExecuted"));
        }

        @Test
        @DisplayName("Should skip non-FIELD_UPDATE actions")
        void shouldSkipNonFieldUpdateActions() {
            WorkflowRuleData rule = createRule("rule-1", "BEFORE_CREATE", null, null,
                "STOP_ON_ERROR", List.of(
                    WorkflowActionData.of("act-1", "EMAIL_ALERT", 0, "{}", true),
                    WorkflowActionData.of("act-2", "FIELD_UPDATE", 1,
                        "{\"updates\": {\"processed\": true}}", true)
                ));

            when(store.findActiveRules("t1", "orders", "BEFORE_CREATE"))
                .thenReturn(List.of(rule));

            handlerRegistry.register(new ActionHandler() {
                @Override
                public String getActionTypeKey() { return "FIELD_UPDATE"; }
                @Override
                public ActionResult execute(ActionContext context) {
                    return ActionResult.success(Map.of("updatedFields",
                        Map.of("processed", true)));
                }
            });

            Map<String, Object> result = engine.evaluateBeforeSave(
                "t1", "orders", null, Map.of(), null, List.of(), "user-1", "CREATE");

            assertEquals(1, result.get("actionsExecuted"));
        }

        @Test
        @DisplayName("Should check trigger fields for BEFORE_UPDATE")
        void shouldCheckTriggerFieldsForBeforeUpdate() {
            WorkflowRuleData rule = createRule("rule-1", "BEFORE_UPDATE", null,
                List.of("price"), "STOP_ON_ERROR",
                List.of(WorkflowActionData.of("act-1", "FIELD_UPDATE", 0, "{}", true)));

            when(store.findActiveRules("t1", "orders", "BEFORE_UPDATE"))
                .thenReturn(List.of(rule));

            // Changed fields don't include "price"
            Map<String, Object> result = engine.evaluateBeforeSave(
                "t1", "orders", "r1", Map.of("name", "New"),
                Map.of("name", "Old"), List.of("name"), "user-1", "UPDATE");

            assertEquals(1, result.get("rulesEvaluated"));
            assertEquals(0, result.get("actionsExecuted"));
        }

        @Test
        @DisplayName("Should evaluate filter formula")
        void shouldEvaluateFilterFormula() {
            WorkflowRuleData passingRule = createRule("rule-1", "BEFORE_CREATE", "true", null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-1", "FIELD_UPDATE", 0, "{}", true)));
            WorkflowRuleData failingRule = createRule("rule-2", "BEFORE_CREATE", "false", null,
                "STOP_ON_ERROR", List.of(WorkflowActionData.of("act-2", "FIELD_UPDATE", 0, "{}", true)));

            when(store.findActiveRules("t1", "orders", "BEFORE_CREATE"))
                .thenReturn(List.of(passingRule, failingRule));

            handlerRegistry.register(new ActionHandler() {
                @Override
                public String getActionTypeKey() { return "FIELD_UPDATE"; }
                @Override
                public ActionResult execute(ActionContext context) {
                    return ActionResult.success(Map.of("updatedFields", Map.of("flag", true)));
                }
            });

            Map<String, Object> result = engine.evaluateBeforeSave(
                "t1", "orders", null, Map.of(), null, List.of(), "user-1", "CREATE");

            assertEquals(2, result.get("rulesEvaluated"));
            assertEquals(1, result.get("actionsExecuted"));
        }
    }

    @Nested
    @DisplayName("executeActionWithRetry")
    class RetryTests {

        @Test
        @DisplayName("Should succeed on first attempt with no retries")
        void shouldSucceedOnFirstAttempt() {
            WorkflowActionData action = WorkflowActionData.of("act-1", "LOG_MESSAGE", 0, "{}", true);
            WorkflowRuleData rule = createRule("ON_CREATE");
            RecordChangeEvent event = createEvent(ChangeType.CREATED);

            registerHandler("LOG_MESSAGE", ActionResult.success());

            ActionResult result = engine.executeActionWithRetry(action, rule, event, "log-1");

            assertTrue(result.successful());
            verify(store, times(1)).createActionLog(
                anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should retry on failure with configured retries")
        void shouldRetryOnFailure() {
            WorkflowActionData action = new WorkflowActionData(
                "act-1", "FLAKY", 0, "{}", true, 2, 1, "FIXED");
            WorkflowRuleData rule = createRule("ON_CREATE");
            RecordChangeEvent event = createEvent(ChangeType.CREATED);

            // Fails twice, succeeds on third attempt
            int[] attempt = {0};
            handlerRegistry.register(new ActionHandler() {
                @Override
                public String getActionTypeKey() { return "FLAKY"; }
                @Override
                public ActionResult execute(ActionContext context) {
                    attempt[0]++;
                    return attempt[0] <= 2 ? ActionResult.failure("temp error") : ActionResult.success();
                }
            });

            ActionResult result = engine.executeActionWithRetry(action, rule, event, "log-1");

            assertTrue(result.successful());
            assertEquals(3, attempt[0]);
        }

        @Test
        @DisplayName("Should return failure after exhausting retries")
        void shouldFailAfterRetries() {
            WorkflowActionData action = new WorkflowActionData(
                "act-1", "ALWAYS_FAIL", 0, "{}", true, 1, 1, "FIXED");
            WorkflowRuleData rule = createRule("ON_CREATE");
            RecordChangeEvent event = createEvent(ChangeType.CREATED);

            registerHandler("ALWAYS_FAIL", ActionResult.failure("permanent error"));

            ActionResult result = engine.executeActionWithRetry(action, rule, event, "log-1");

            assertFalse(result.successful());
            assertEquals("permanent error", result.errorMessage());
        }
    }

    @Nested
    @DisplayName("executeAction")
    class ExecuteActionTests {

        @Test
        @DisplayName("Should return failure when no handler registered")
        void shouldFailWhenNoHandler() {
            WorkflowActionData action = WorkflowActionData.of("act-1", "UNKNOWN", 0, "{}", true);
            WorkflowRuleData rule = createRule("ON_CREATE");
            RecordChangeEvent event = createEvent(ChangeType.CREATED);

            ActionResult result = engine.executeAction(action, rule, event, "log-1");

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("No handler registered"));
        }

        @Test
        @DisplayName("Should catch and wrap handler exceptions")
        void shouldCatchHandlerExceptions() {
            WorkflowActionData action = WorkflowActionData.of("act-1", "THROWS", 0, "{}", true);
            WorkflowRuleData rule = createRule("ON_CREATE");
            RecordChangeEvent event = createEvent(ChangeType.CREATED);

            handlerRegistry.register(new ActionHandler() {
                @Override
                public String getActionTypeKey() { return "THROWS"; }
                @Override
                public ActionResult execute(ActionContext context) {
                    throw new RuntimeException("Unexpected error");
                }
            });

            ActionResult result = engine.executeAction(action, rule, event, "log-1");

            assertFalse(result.successful());
            assertEquals("Unexpected error", result.errorMessage());
        }

        @Test
        @DisplayName("Should build correct ActionContext")
        void shouldBuildCorrectContext() {
            WorkflowActionData action = WorkflowActionData.of("act-1", "CAPTURE", 0,
                "{\"key\":\"val\"}", true);
            WorkflowRuleData rule = createRule("rule-1", "ON_UPDATE", null, null,
                "STOP_ON_ERROR", List.of(action));

            Map<String, Object> data = Map.of("status", "Done");
            Map<String, Object> prev = Map.of("status", "Active");
            RecordChangeEvent event = createEvent(ChangeType.UPDATED, data, prev, List.of("status"));

            ActionContext[] captured = new ActionContext[1];
            handlerRegistry.register(new ActionHandler() {
                @Override
                public String getActionTypeKey() { return "CAPTURE"; }
                @Override
                public ActionResult execute(ActionContext context) {
                    captured[0] = context;
                    return ActionResult.success();
                }
            });

            engine.executeAction(action, rule, event, "log-1");

            assertNotNull(captured[0]);
            assertEquals("t1", captured[0].tenantId());
            assertEquals("col-1", captured[0].collectionId());
            assertEquals("orders", captured[0].collectionName());
            assertEquals("r1", captured[0].recordId());
            assertEquals(data, captured[0].data());
            assertEquals(prev, captured[0].previousData());
            assertEquals(List.of("status"), captured[0].changedFields());
            assertEquals("user-1", captured[0].userId());
            assertEquals("{\"key\":\"val\"}", captured[0].actionConfigJson());
            assertEquals("rule-1", captured[0].workflowRuleId());
            assertEquals("log-1", captured[0].executionLogId());
        }
    }

    @Nested
    @DisplayName("WorkflowRuleData")
    class WorkflowRuleDataTests {

        @Test
        @DisplayName("activeActions should filter inactive and sort by order")
        void shouldFilterAndSort() {
            List<WorkflowActionData> actions = List.of(
                WorkflowActionData.of("act-3", "C", 2, "{}", true),
                new WorkflowActionData("act-2", "B", 1, "{}", false, 0, 60, "FIXED"),
                WorkflowActionData.of("act-1", "A", 0, "{}", true)
            );
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", actions);

            List<WorkflowActionData> active = rule.activeActions();
            assertEquals(2, active.size());
            assertEquals("A", active.get(0).actionType());
            assertEquals("C", active.get(1).actionType());
        }

        @Test
        @DisplayName("activeActions should return empty list when actions null")
        void shouldReturnEmptyWhenActionsNull() {
            WorkflowRuleData rule = new WorkflowRuleData(
                "r1", "t1", "c1", "orders", "Test", null,
                true, "ON_CREATE", null, false, 0, "STOP_ON_ERROR",
                null, null, null, null, "SEQUENTIAL", null);

            assertTrue(rule.activeActions().isEmpty());
        }

        @Test
        @DisplayName("stopOnError should return true for STOP_ON_ERROR")
        void shouldDetectStopOnError() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "STOP_ON_ERROR", List.of());
            assertTrue(rule.stopOnError());
        }

        @Test
        @DisplayName("stopOnError should return false for CONTINUE_ON_ERROR")
        void shouldDetectContinueOnError() {
            WorkflowRuleData rule = createRule("rule-1", "ON_CREATE", null, null,
                "CONTINUE_ON_ERROR", List.of());
            assertFalse(rule.stopOnError());
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null WorkflowStore")
        void shouldRejectNullStore() {
            assertThrows(NullPointerException.class,
                () -> new WorkflowEngine(null, handlerRegistry, formulaEvaluator, objectMapper));
        }

        @Test
        @DisplayName("Should reject null ActionHandlerRegistry")
        void shouldRejectNullRegistry() {
            assertThrows(NullPointerException.class,
                () -> new WorkflowEngine(store, null, formulaEvaluator, objectMapper));
        }

        @Test
        @DisplayName("Should reject null FormulaEvaluator")
        void shouldRejectNullEvaluator() {
            assertThrows(NullPointerException.class,
                () -> new WorkflowEngine(store, handlerRegistry, null, objectMapper));
        }

        @Test
        @DisplayName("Should reject null ObjectMapper")
        void shouldRejectNullMapper() {
            assertThrows(NullPointerException.class,
                () -> new WorkflowEngine(store, handlerRegistry, formulaEvaluator, null));
        }
    }
}
