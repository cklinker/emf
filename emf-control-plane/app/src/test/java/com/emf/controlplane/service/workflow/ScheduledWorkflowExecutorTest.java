package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduledWorkflowExecutorTest {

    private WorkflowRuleRepository ruleRepository;
    private WorkflowEngine workflowEngine;
    private ScheduledWorkflowExecutor executor;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(WorkflowRuleRepository.class);
        workflowEngine = mock(WorkflowEngine.class);
        executor = new ScheduledWorkflowExecutor(ruleRepository, workflowEngine);
    }

    private WorkflowRule createScheduledRule(String name, String cronExpression) {
        Collection collection = new Collection();
        collection.setId("col-1");
        collection.setName("orders");

        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId("tenant-1");
        rule.setCollection(collection);
        rule.setName(name);
        rule.setTriggerType("SCHEDULED");
        rule.setCronExpression(cronExpression);
        rule.setActive(true);
        rule.setErrorHandling("STOP_ON_ERROR");
        return rule;
    }

    @Nested
    @DisplayName("Polling")
    class PollingTests {

        @Test
        @DisplayName("Should skip when no scheduled rules exist")
        void shouldSkipWhenNoRules() {
            when(ruleRepository.findByTriggerTypeAndActiveTrueOrderByExecutionOrderAsc("SCHEDULED"))
                .thenReturn(List.of());

            executor.pollScheduledWorkflows();

            verify(workflowEngine, never()).executeScheduledRule(any());
        }

        @Test
        @DisplayName("Should execute due rules and update last run")
        void shouldExecuteDueRules() {
            // Create a rule that is due (cron every minute, last run 2 min ago)
            WorkflowRule rule = createScheduledRule("Every Minute", "0 * * * * *");
            rule.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            when(ruleRepository.findByTriggerTypeAndActiveTrueOrderByExecutionOrderAsc("SCHEDULED"))
                .thenReturn(List.of(rule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            executor.pollScheduledWorkflows();

            verify(workflowEngine).executeScheduledRule(rule);
            verify(ruleRepository).save(rule);
            assertNotNull(rule.getLastScheduledRun());
        }

        @Test
        @DisplayName("Should not execute rules that are not yet due")
        void shouldNotExecuteRulesNotDue() {
            // Create a rule with daily cron that just ran
            WorkflowRule rule = createScheduledRule("Daily Rule", "0 0 0 * * *");
            rule.setLastScheduledRun(Instant.now().minus(1, ChronoUnit.HOURS));

            when(ruleRepository.findByTriggerTypeAndActiveTrueOrderByExecutionOrderAsc("SCHEDULED"))
                .thenReturn(List.of(rule));

            executor.pollScheduledWorkflows();

            verify(workflowEngine, never()).executeScheduledRule(any());
        }

        @Test
        @DisplayName("Should handle errors in individual rules gracefully")
        void shouldHandleErrorsGracefully() {
            WorkflowRule rule1 = createScheduledRule("Rule 1", "0 * * * * *");
            rule1.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            WorkflowRule rule2 = createScheduledRule("Rule 2", "0 * * * * *");
            rule2.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            when(ruleRepository.findByTriggerTypeAndActiveTrueOrderByExecutionOrderAsc("SCHEDULED"))
                .thenReturn(List.of(rule1, rule2));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // First rule throws, second should still execute
            doThrow(new RuntimeException("Boom")).when(workflowEngine).executeScheduledRule(rule1);

            executor.pollScheduledWorkflows();

            verify(workflowEngine).executeScheduledRule(rule1);
            verify(workflowEngine).executeScheduledRule(rule2);
        }
    }

    @Nested
    @DisplayName("isDue Evaluation")
    class IsDueTests {

        @Test
        @DisplayName("Should return false when no cron expression")
        void shouldReturnFalseForNullCron() {
            WorkflowRule rule = createScheduledRule("No Cron", null);
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return false when cron expression is blank")
        void shouldReturnFalseForBlankCron() {
            WorkflowRule rule = createScheduledRule("Blank Cron", "   ");
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should return false for invalid cron expression")
        void shouldReturnFalseForInvalidCron() {
            WorkflowRule rule = createScheduledRule("Bad Cron", "not-a-cron");
            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should be due when never run before and cron is past")
        void shouldBeDueWhenNeverRun() {
            WorkflowRule rule = createScheduledRule("New Rule", "0 * * * * *");
            rule.setLastScheduledRun(null);
            // Set createdAt to 2 minutes ago so the cron will have passed
            try {
                var createdAtField = rule.getClass().getSuperclass().getSuperclass()
                    .getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(rule, Instant.now().minus(2, ChronoUnit.MINUTES));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should be due when last run was before next cron execution")
        void shouldBeDueAfterCronTime() {
            // Every minute — last run was 2 minutes ago, so at least one cron has passed
            WorkflowRule rule = createScheduledRule("Every Minute", "0 * * * * *");
            rule.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should not be due when last run was recent")
        void shouldNotBeDueWhenRecentlyRun() {
            // Every hour — last run was 5 minutes ago
            WorkflowRule rule = createScheduledRule("Hourly", "0 0 * * * *");
            rule.setLastScheduledRun(Instant.now().minus(5, ChronoUnit.MINUTES));

            assertFalse(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should handle timezone correctly")
        void shouldHandleTimezone() {
            WorkflowRule rule = createScheduledRule("UTC Rule", "0 * * * * *");
            rule.setTimezone("UTC");
            rule.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should fall back to UTC for invalid timezone")
        void shouldFallBackToUtcForInvalidTimezone() {
            WorkflowRule rule = createScheduledRule("Bad TZ", "0 * * * * *");
            rule.setTimezone("Invalid/Timezone");
            rule.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            // Should still work with UTC fallback
            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should fall back to UTC for null timezone")
        void shouldFallBackToUtcForNullTimezone() {
            WorkflowRule rule = createScheduledRule("No TZ", "0 * * * * *");
            rule.setTimezone(null);
            rule.setLastScheduledRun(Instant.now().minus(2, ChronoUnit.MINUTES));

            assertTrue(executor.isDue(rule));
        }

        @Test
        @DisplayName("Should support 6-field cron expressions (with seconds)")
        void shouldSupport6FieldCron() {
            // Spring's CronExpression supports 6 fields: sec min hour day month weekday
            WorkflowRule rule = createScheduledRule("With Seconds", "*/30 * * * * *");
            rule.setLastScheduledRun(Instant.now().minus(1, ChronoUnit.MINUTES));

            assertTrue(executor.isDue(rule));
        }
    }
}
