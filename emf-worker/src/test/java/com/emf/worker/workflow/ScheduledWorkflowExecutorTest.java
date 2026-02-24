package com.emf.worker.workflow;

import com.emf.runtime.workflow.WorkflowActionData;
import com.emf.runtime.workflow.WorkflowEngine;
import com.emf.runtime.workflow.WorkflowRuleData;
import com.emf.runtime.workflow.WorkflowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScheduledWorkflowExecutor")
class ScheduledWorkflowExecutorTest {

    private WorkflowStore workflowStore;
    private WorkflowEngine workflowEngine;
    private ScheduledWorkflowExecutor executor;

    @BeforeEach
    void setUp() {
        workflowStore = mock(WorkflowStore.class);
        workflowEngine = mock(WorkflowEngine.class);
        executor = new ScheduledWorkflowExecutor(workflowStore, workflowEngine);
    }

    @Nested
    @DisplayName("isDue")
    class IsDueTests {

        @Test
        @DisplayName("Should return false when cron expression is null")
        void shouldReturnFalseWhenCronIsNull() {
            WorkflowRuleData rule = createScheduledRule("rule-1", null, null, null);
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return false when cron expression is blank")
        void shouldReturnFalseWhenCronIsBlank() {
            WorkflowRuleData rule = createScheduledRule("rule-1", "  ", null, null);
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return false when cron expression is invalid")
        void shouldReturnFalseWhenCronIsInvalid() {
            WorkflowRuleData rule = createScheduledRule("rule-1", "not a cron", null, null);
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return true when rule has never run and cron matches")
        void shouldReturnTrueWhenNeverRunAndCronMatches() {
            // Cron: every minute — should always be due if never run
            WorkflowRuleData rule = createScheduledRule("rule-1", "0 * * * * *", null, null);
            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return true when last run was long ago")
        void shouldReturnTrueWhenLastRunWasLongAgo() {
            // Last run was 2 hours ago, cron is every minute
            Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
            WorkflowRuleData rule = createScheduledRule("rule-1", "0 * * * * *", null, twoHoursAgo);
            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return false when last run was just now")
        void shouldReturnFalseWhenLastRunWasJustNow() {
            // Last run was just now, cron is daily at midnight — not due yet
            Instant now = Instant.now();
            WorkflowRuleData rule = createScheduledRule("rule-1", "0 0 0 * * *", null, now);
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should handle custom timezone")
        void shouldHandleCustomTimezone() {
            // Every minute in America/New_York
            WorkflowRuleData rule = createScheduledRule("rule-1", "0 * * * * *",
                "America/New_York", null);
            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should fall back to UTC for invalid timezone")
        void shouldFallBackToUtcForInvalidTimezone() {
            // Every minute with invalid timezone
            WorkflowRuleData rule = createScheduledRule("rule-1", "0 * * * * *",
                "Invalid/Zone", null);
            assertTrue(executor.isDue(rule));
        }
    }

    @Nested
    @DisplayName("pollScheduledWorkflows")
    class PollTests {

        @Test
        @DisplayName("Should do nothing when no scheduled rules exist")
        void shouldDoNothingWhenNoRulesExist() {
            when(workflowStore.findScheduledRules()).thenReturn(List.of());

            executor.pollScheduledWorkflows();

            verify(workflowEngine, never()).executeScheduledRule(any());
        }

        @Test
        @DisplayName("Should execute due rules")
        void shouldExecuteDueRules() {
            // Rule with cron every minute and never run = always due
            WorkflowRuleData dueRule = createScheduledRule("rule-1", "0 * * * * *", null, null);
            when(workflowStore.findScheduledRules()).thenReturn(List.of(dueRule));
            when(workflowStore.claimScheduledRule(eq("rule-1"), isNull(), any(Instant.class)))
                .thenReturn(true);

            executor.pollScheduledWorkflows();

            verify(workflowEngine).executeScheduledRule(dueRule);
        }

        @Test
        @DisplayName("Should skip rules that are not due")
        void shouldSkipRulesNotDue() {
            // Rule that just ran — daily cron, not due
            Instant now = Instant.now();
            WorkflowRuleData notDueRule = createScheduledRule("rule-1", "0 0 0 * * *", null, now);
            when(workflowStore.findScheduledRules()).thenReturn(List.of(notDueRule));

            executor.pollScheduledWorkflows();

            verify(workflowEngine, never()).executeScheduledRule(any());
            verify(workflowStore, never()).claimScheduledRule(any(), any(), any());
        }

        @Test
        @DisplayName("Should skip rule when claim fails (claimed by another pod)")
        void shouldSkipWhenClaimFails() {
            WorkflowRuleData dueRule = createScheduledRule("rule-1", "0 * * * * *", null, null);
            when(workflowStore.findScheduledRules()).thenReturn(List.of(dueRule));
            when(workflowStore.claimScheduledRule(eq("rule-1"), isNull(), any(Instant.class)))
                .thenReturn(false);

            executor.pollScheduledWorkflows();

            verify(workflowEngine, never()).executeScheduledRule(any());
        }

        @Test
        @DisplayName("Should handle errors during execution gracefully")
        void shouldHandleExecutionErrors() {
            WorkflowRuleData dueRule = createScheduledRule("rule-1", "0 * * * * *", null, null);
            when(workflowStore.findScheduledRules()).thenReturn(List.of(dueRule));
            when(workflowStore.claimScheduledRule(eq("rule-1"), isNull(), any(Instant.class)))
                .thenReturn(true);
            doThrow(new RuntimeException("engine error")).when(workflowEngine)
                .executeScheduledRule(dueRule);

            // Should not throw
            assertDoesNotThrow(() -> executor.pollScheduledWorkflows());
        }

        @Test
        @DisplayName("Should process multiple rules independently")
        void shouldProcessMultipleRulesIndependently() {
            WorkflowRuleData dueRule1 = createScheduledRule("rule-1", "0 * * * * *", null, null);
            WorkflowRuleData dueRule2 = createScheduledRule("rule-2", "0 * * * * *", null, null);
            Instant now = Instant.now();
            WorkflowRuleData notDueRule = createScheduledRule("rule-3", "0 0 0 * * *", null, now);

            when(workflowStore.findScheduledRules())
                .thenReturn(List.of(dueRule1, dueRule2, notDueRule));
            when(workflowStore.claimScheduledRule(eq("rule-1"), isNull(), any()))
                .thenReturn(true);
            when(workflowStore.claimScheduledRule(eq("rule-2"), isNull(), any()))
                .thenReturn(true);

            executor.pollScheduledWorkflows();

            verify(workflowEngine).executeScheduledRule(dueRule1);
            verify(workflowEngine).executeScheduledRule(dueRule2);
            verify(workflowEngine, never()).executeScheduledRule(notDueRule);
        }
    }

    // ---- Helpers ----

    private WorkflowRuleData createScheduledRule(String id, String cronExpression,
                                                    String timezone, Instant lastScheduledRun) {
        return new WorkflowRuleData(
            id, "tenant-1", "col-1", "orders",
            "Scheduled Rule " + id, null, true, "SCHEDULED",
            null, false, 0, "CONTINUE_ON_ERROR",
            null, cronExpression, timezone, lastScheduledRun, "SEQUENTIAL",
            List.of(WorkflowActionData.of("a1", "FIELD_UPDATE", 1, "{}", true)));
    }
}
