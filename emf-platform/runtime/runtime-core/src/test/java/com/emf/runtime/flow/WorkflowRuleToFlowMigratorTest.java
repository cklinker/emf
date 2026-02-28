package com.emf.runtime.flow;

import com.emf.runtime.workflow.WorkflowActionData;
import com.emf.runtime.workflow.WorkflowRuleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkflowRuleToFlowMigrator}.
 */
class WorkflowRuleToFlowMigratorTest {

    private WorkflowRuleToFlowMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = new WorkflowRuleToFlowMigrator();
    }

    // -------------------------------------------------------------------------
    // Flow Type Mapping
    // -------------------------------------------------------------------------

    @Nested
    class FlowTypeMapping {

        @Test
        void mapsOnCreateToRecordTriggered() {
            assertEquals("RECORD_TRIGGERED", migrator.mapFlowType("ON_CREATE"));
        }

        @Test
        void mapsOnUpdateToRecordTriggered() {
            assertEquals("RECORD_TRIGGERED", migrator.mapFlowType("ON_UPDATE"));
        }

        @Test
        void mapsOnDeleteToRecordTriggered() {
            assertEquals("RECORD_TRIGGERED", migrator.mapFlowType("ON_DELETE"));
        }

        @Test
        void mapsOnCreateOrUpdateToRecordTriggered() {
            assertEquals("RECORD_TRIGGERED", migrator.mapFlowType("ON_CREATE_OR_UPDATE"));
        }

        @Test
        void mapsBeforeCreateToRecordTriggered() {
            assertEquals("RECORD_TRIGGERED", migrator.mapFlowType("BEFORE_CREATE"));
        }

        @Test
        void mapsBeforeUpdateToRecordTriggered() {
            assertEquals("RECORD_TRIGGERED", migrator.mapFlowType("BEFORE_UPDATE"));
        }

        @Test
        void mapsScheduledToScheduled() {
            assertEquals("SCHEDULED", migrator.mapFlowType("SCHEDULED"));
        }

        @Test
        void mapsManualToAutolaunched() {
            assertEquals("AUTOLAUNCHED", migrator.mapFlowType("MANUAL"));
        }

        @Test
        void mapsNullToAutolaunched() {
            assertEquals("AUTOLAUNCHED", migrator.mapFlowType(null));
        }

        @Test
        void mapsUnknownToAutolaunched() {
            assertEquals("AUTOLAUNCHED", migrator.mapFlowType("UNKNOWN_TYPE"));
        }
    }

    // -------------------------------------------------------------------------
    // Trigger Config Building
    // -------------------------------------------------------------------------

    @Nested
    class TriggerConfigBuilding {

        @Test
        void onCreateSetsCollectionAndCreatedEvent() {
            WorkflowRuleData rule = buildRule("ON_CREATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            assertEquals("orders", result.triggerConfig().get("collection"));
            assertEquals(List.of("CREATED"), result.triggerConfig().get("events"));
        }

        @Test
        void onUpdateSetsCollectionAndUpdatedEvent() {
            WorkflowRuleData rule = buildRule("ON_UPDATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            assertEquals(List.of("UPDATED"), result.triggerConfig().get("events"));
        }

        @Test
        void onUpdateIncludesTriggerFields() {
            WorkflowRuleData rule = buildRule("ON_UPDATE", "orders", null,
                    List.of("status", "amount"), null, null);
            var result = migrator.migrate(rule);
            assertEquals(List.of("status", "amount"), result.triggerConfig().get("triggerFields"));
        }

        @Test
        void onDeleteSetsDeletedEvent() {
            WorkflowRuleData rule = buildRule("ON_DELETE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            assertEquals(List.of("DELETED"), result.triggerConfig().get("events"));
        }

        @Test
        void onCreateOrUpdateSetsBothEvents() {
            WorkflowRuleData rule = buildRule("ON_CREATE_OR_UPDATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            assertEquals(List.of("CREATED", "UPDATED"), result.triggerConfig().get("events"));
        }

        @Test
        void beforeCreateSetsSynchronousFlag() {
            WorkflowRuleData rule = buildRule("BEFORE_CREATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            assertEquals(true, result.triggerConfig().get("synchronous"));
            assertEquals(List.of("CREATED"), result.triggerConfig().get("events"));
        }

        @Test
        void beforeUpdateSetsSynchronousFlag() {
            WorkflowRuleData rule = buildRule("BEFORE_UPDATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            assertEquals(true, result.triggerConfig().get("synchronous"));
            assertEquals(List.of("UPDATED"), result.triggerConfig().get("events"));
        }

        @Test
        void scheduledSetsCronAndTimezone() {
            WorkflowRuleData rule = buildRule("SCHEDULED", null, null, null,
                    "0 0 8 * * MON-FRI", "America/Chicago");
            var result = migrator.migrate(rule);
            assertEquals("0 0 8 * * MON-FRI", result.triggerConfig().get("cronExpression"));
            assertEquals("America/Chicago", result.triggerConfig().get("timezone"));
        }

        @Test
        void filterFormulaIncludedInConfig() {
            WorkflowRuleData rule = buildRule("ON_CREATE", "orders",
                    "status == 'ACTIVE'", null, null, null);
            var result = migrator.migrate(rule);
            assertEquals("status == 'ACTIVE'", result.triggerConfig().get("filterFormula"));
        }

        @Test
        void emptyFilterFormulaExcludedFromConfig() {
            WorkflowRuleData rule = buildRule("ON_CREATE", "orders", "", null, null, null);
            var result = migrator.migrate(rule);
            assertFalse(result.triggerConfig().containsKey("filterFormula"));
        }
    }

    // -------------------------------------------------------------------------
    // Definition Building
    // -------------------------------------------------------------------------

    @Nested
    class DefinitionBuilding {

        @Test
        void noActiveActionsProducesSucceedOnly() {
            WorkflowRuleData rule = buildRule("ON_CREATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            assertEquals(1, states.size());
            assertTrue(states.containsKey("FlowSucceeded"));
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("no active actions")));
        }

        @Test
        void singleActionProducesTaskAndSucceed() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(action("a1", "FIELD_UPDATE", 1)));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            assertEquals("Step1_FIELD_UPDATE", result.definition().get("StartAt"));
            assertEquals(3, states.size()); // Task + Succeed + Failed
            assertTrue(states.containsKey("Step1_FIELD_UPDATE"));
            assertTrue(states.containsKey("FlowSucceeded"));
            assertTrue(states.containsKey("FlowFailed"));
        }

        @Test
        void multipleActionsChainedSequentially() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(
                            action("a1", "FIELD_UPDATE", 1),
                            action("a2", "EMAIL_ALERT", 2),
                            action("a3", "CREATE_RECORD", 3)
                    ));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            assertEquals("Step1_FIELD_UPDATE", result.definition().get("StartAt"));

            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_FIELD_UPDATE");
            assertEquals("Step2_EMAIL_ALERT", step1.get("Next"));

            @SuppressWarnings("unchecked")
            var step2 = (Map<String, Object>) states.get("Step2_EMAIL_ALERT");
            assertEquals("Step3_CREATE_RECORD", step2.get("Next"));

            @SuppressWarnings("unchecked")
            var step3 = (Map<String, Object>) states.get("Step3_CREATE_RECORD");
            assertEquals("FlowSucceeded", step3.get("Next"));
        }

        @Test
        void stopOnErrorAddsCatchToFlowFailed() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(action("a1", "FIELD_UPDATE", 1)));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_FIELD_UPDATE");
            @SuppressWarnings("unchecked")
            var catchRules = (List<Map<String, Object>>) step1.get("Catch");
            assertNotNull(catchRules);
            assertEquals(1, catchRules.size());
            assertEquals("FlowFailed", catchRules.get(0).get("Next"));
        }

        @Test
        void continueOnErrorAddsCatchToNextState() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "CONTINUE_ON_ERROR",
                    List.of(
                            action("a1", "FIELD_UPDATE", 1),
                            action("a2", "EMAIL_ALERT", 2)
                    ));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_FIELD_UPDATE");
            @SuppressWarnings("unchecked")
            var catchRules = (List<Map<String, Object>>) step1.get("Catch");
            assertEquals("Step2_EMAIL_ALERT", catchRules.get(0).get("Next"));
            assertFalse(states.containsKey("FlowFailed"));
        }

        @Test
        void continueOnErrorLastStepCatchesToSucceed() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "CONTINUE_ON_ERROR",
                    List.of(action("a1", "FIELD_UPDATE", 1)));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_FIELD_UPDATE");
            @SuppressWarnings("unchecked")
            var catchRules = (List<Map<String, Object>>) step1.get("Catch");
            assertEquals("FlowSucceeded", catchRules.get(0).get("Next"));
        }

        @Test
        void retryPolicyIncludedWhenConfigured() {
            var actionWithRetry = new WorkflowActionData("a1", "HTTP_CALLOUT", 1,
                    "{}", true, 3, 5, "EXPONENTIAL");
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(actionWithRetry));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_HTTP_CALLOUT");
            @SuppressWarnings("unchecked")
            var retry = (List<Map<String, Object>>) step1.get("Retry");
            assertNotNull(retry);
            assertEquals(1, retry.size());
            assertEquals(3, retry.get(0).get("MaxAttempts"));
            assertEquals(5, retry.get(0).get("IntervalSeconds"));
            assertEquals(2.0, retry.get(0).get("BackoffRate"));
        }

        @Test
        void fixedBackoffUsesRateOfOne() {
            var actionWithRetry = new WorkflowActionData("a1", "HTTP_CALLOUT", 1,
                    "{}", true, 2, 10, "FIXED");
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(actionWithRetry));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_HTTP_CALLOUT");
            @SuppressWarnings("unchecked")
            var retry = (List<Map<String, Object>>) step1.get("Retry");
            assertEquals(1.0, retry.get(0).get("BackoffRate"));
        }

        @Test
        void noRetryWhenRetryCountIsZero() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(action("a1", "FIELD_UPDATE", 1)));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_FIELD_UPDATE");
            assertFalse(step1.containsKey("Retry"));
        }

        @Test
        void taskStateHasResourceAndType() {
            WorkflowRuleData rule = buildRuleWithActions("ON_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(action("a1", "CREATE_RECORD", 1)));
            var result = migrator.migrate(rule);

            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            @SuppressWarnings("unchecked")
            var step1 = (Map<String, Object>) states.get("Step1_CREATE_RECORD");
            assertEquals("Task", step1.get("Type"));
            assertEquals("CREATE_RECORD", step1.get("Resource"));
        }

        @Test
        void definitionIncludesComment() {
            WorkflowRuleData rule = buildRule("ON_CREATE", "orders", null, null, null, null);
            var result = migrator.migrate(rule);
            String comment = (String) result.definition().get("Comment");
            assertTrue(comment.contains("Migrated from workflow rule"));
            assertTrue(comment.contains("Test Rule"));
        }
    }

    // -------------------------------------------------------------------------
    // State ID Building
    // -------------------------------------------------------------------------

    @Nested
    class StateIdBuilding {

        @Test
        void stateIdIncludesIndexAndType() {
            var action = action("a1", "FIELD_UPDATE", 1);
            assertEquals("Step1_FIELD_UPDATE", migrator.buildStateId(action, 0));
            assertEquals("Step3_FIELD_UPDATE", migrator.buildStateId(action, 2));
        }

        @Test
        void stateIdSanitizesSpecialChars() {
            var action = action("a1", "my-custom.action", 1);
            assertEquals("Step1_my_custom_action", migrator.buildStateId(action, 0));
        }
    }

    // -------------------------------------------------------------------------
    // Full Migration E2E
    // -------------------------------------------------------------------------

    @Nested
    class FullMigration {

        @Test
        void scheduledRuleMigration() {
            WorkflowRuleData rule = new WorkflowRuleData(
                    "rule-1", "tenant-1", "coll-1", null,
                    "Daily Report", "Runs daily report",
                    true, "SCHEDULED", null, false, 1,
                    "STOP_ON_ERROR", null,
                    "0 0 8 * * *", "UTC", null, "SEQUENTIAL",
                    List.of(action("a1", "HTTP_CALLOUT", 1))
            );

            var result = migrator.migrate(rule);
            assertEquals("SCHEDULED", result.flowType());
            assertEquals("0 0 8 * * *", result.triggerConfig().get("cronExpression"));
            assertEquals("UTC", result.triggerConfig().get("timezone"));
            assertNotNull(result.definition().get("States"));
        }

        @Test
        void manualRuleMigration() {
            WorkflowRuleData rule = new WorkflowRuleData(
                    "rule-1", "tenant-1", "coll-1", "orders",
                    "Manual Process", null,
                    true, "MANUAL", null, false, 1,
                    "CONTINUE_ON_ERROR", null,
                    null, null, null, "SEQUENTIAL",
                    List.of(
                            action("a1", "FIELD_UPDATE", 1),
                            action("a2", "SEND_NOTIFICATION", 2)
                    )
            );

            var result = migrator.migrate(rule);
            assertEquals("AUTOLAUNCHED", result.flowType());
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        void beforeSaveRuleSetsFlowTypeAndSynchronous() {
            WorkflowRuleData rule = buildRuleWithActions("BEFORE_CREATE", "orders", "STOP_ON_ERROR",
                    List.of(action("a1", "FIELD_UPDATE", 1)));
            var result = migrator.migrate(rule);
            assertEquals("RECORD_TRIGGERED", result.flowType());
            assertEquals(true, result.triggerConfig().get("synchronous"));
        }

        @Test
        void inactiveActionsExcludedFromDefinition() {
            var active = new WorkflowActionData("a1", "FIELD_UPDATE", 1, "{}", true, 0, 60, "FIXED");
            var inactive = new WorkflowActionData("a2", "EMAIL_ALERT", 2, "{}", false, 0, 60, "FIXED");
            WorkflowRuleData rule = new WorkflowRuleData(
                    "rule-1", "tenant-1", "coll-1", "orders",
                    "Test Rule", null, true, "ON_CREATE", null,
                    false, 1, "STOP_ON_ERROR", null, null, null, null, "SEQUENTIAL",
                    List.of(active, inactive)
            );

            var result = migrator.migrate(rule);
            @SuppressWarnings("unchecked")
            var states = (Map<String, Object>) result.definition().get("States");
            // Should have: Step1_FIELD_UPDATE, FlowSucceeded, FlowFailed (stop on error)
            assertEquals(3, states.size());
            assertTrue(states.containsKey("Step1_FIELD_UPDATE"));
            assertFalse(states.containsKey("Step2_EMAIL_ALERT"));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkflowRuleData buildRule(String triggerType, String collection,
                                        String filterFormula, List<String> triggerFields,
                                        String cron, String timezone) {
        return new WorkflowRuleData(
                "rule-1", "tenant-1", "coll-1", collection,
                "Test Rule", null, true, triggerType, filterFormula,
                false, 1, "STOP_ON_ERROR", triggerFields,
                cron, timezone, null, "SEQUENTIAL",
                List.of() // no actions
        );
    }

    private WorkflowRuleData buildRuleWithActions(String triggerType, String collection,
                                                    String errorHandling,
                                                    List<WorkflowActionData> actions) {
        return new WorkflowRuleData(
                "rule-1", "tenant-1", "coll-1", collection,
                "Test Rule", null, true, triggerType, null,
                false, 1, errorHandling, null,
                null, null, null, "SEQUENTIAL",
                actions
        );
    }

    private WorkflowActionData action(String id, String type, int order) {
        return WorkflowActionData.of(id, type, order, "{}", true);
    }
}
