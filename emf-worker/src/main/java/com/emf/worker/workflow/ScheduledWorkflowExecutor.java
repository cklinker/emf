package com.emf.worker.workflow;

import com.emf.runtime.workflow.WorkflowEngine;
import com.emf.runtime.workflow.WorkflowRuleData;
import com.emf.runtime.workflow.WorkflowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Polls for active SCHEDULED workflow rules and executes them when due.
 * <p>
 * Runs every 60 seconds (configurable via {@code emf.workflow.scheduled.poll-interval-ms}).
 * Uses optimistic locking via {@link WorkflowStore#claimScheduledRule} to ensure that
 * only one worker pod executes a scheduled rule, even in multi-instance deployments.
 * <p>
 * For each SCHEDULED rule:
 * <ol>
 *   <li>Parse the cron expression and determine if execution is due</li>
 *   <li>Atomically claim the rule via optimistic lock on {@code lastScheduledRun}</li>
 *   <li>Delegate execution to {@link WorkflowEngine#executeScheduledRule}</li>
 * </ol>
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(WorkflowEngine.class)
public class ScheduledWorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledWorkflowExecutor.class);
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final WorkflowStore workflowStore;
    private final WorkflowEngine workflowEngine;

    public ScheduledWorkflowExecutor(WorkflowStore workflowStore, WorkflowEngine workflowEngine) {
        this.workflowStore = workflowStore;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Polls for due scheduled workflow rules.
     */
    @Scheduled(fixedRateString = "${emf.workflow.scheduled.poll-interval-ms:60000}")
    public void pollScheduledWorkflows() {
        List<WorkflowRuleData> scheduledRules = workflowStore.findScheduledRules();

        if (scheduledRules.isEmpty()) {
            return;
        }

        log.debug("Checking {} scheduled workflow rules", scheduledRules.size());
        int executed = 0;

        for (WorkflowRuleData rule : scheduledRules) {
            try {
                if (isDue(rule)) {
                    if (claimAndExecute(rule)) {
                        executed++;
                    }
                }
            } catch (Exception e) {
                log.error("Error processing scheduled rule '{}': {}", rule.name(), e.getMessage(), e);
            }
        }

        if (executed > 0) {
            log.info("Executed {} scheduled workflow rules", executed);
        }
    }

    /**
     * Determines if a scheduled workflow rule is due for execution based on
     * its cron expression, timezone, and last scheduled run time.
     *
     * @param rule the workflow rule to check
     * @return true if the rule should execute now
     */
    boolean isDue(WorkflowRuleData rule) {
        String cronExpr = rule.cronExpression();
        if (cronExpr == null || cronExpr.isBlank()) {
            log.warn("Scheduled rule '{}' has no cron expression, skipping", rule.name());
            return false;
        }

        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            ZoneId zoneId = resolveTimezone(rule);
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            // Determine the reference point: last run time or a default starting point
            Instant lastRun = rule.lastScheduledRun();
            LocalDateTime referenceTime;

            if (lastRun != null) {
                referenceTime = LocalDateTime.ofInstant(lastRun, zoneId);
            } else {
                // Never run before — use one day ago as reference
                referenceTime = now.toLocalDateTime().minusDays(1);
            }

            // Find the next scheduled execution after the reference time
            LocalDateTime nextExecution = cron.next(referenceTime);

            if (nextExecution == null) {
                log.warn("Cron expression for rule '{}' has no next execution", rule.name());
                return false;
            }

            // Due if the next execution is at or before now
            ZonedDateTime nextZoned = nextExecution.atZone(zoneId);
            return !nextZoned.isAfter(now);

        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression '{}' for rule '{}': {}",
                cronExpr, rule.name(), e.getMessage());
            return false;
        }
    }

    /**
     * Atomically claims a scheduled rule and executes it.
     * <p>
     * Uses optimistic locking: claims the rule by updating {@code lastScheduledRun}
     * only if it hasn't changed since we read it. If another pod already claimed it,
     * the claim fails and we skip execution.
     *
     * @param rule the workflow rule to execute
     * @return true if this pod executed the rule, false if skipped
     */
    private boolean claimAndExecute(WorkflowRuleData rule) {
        Instant now = Instant.now();

        // Attempt atomic claim via optimistic lock
        boolean claimed = workflowStore.claimScheduledRule(
            rule.id(), rule.lastScheduledRun(), now);

        if (!claimed) {
            log.debug("Scheduled rule '{}' already claimed by another worker, skipping", rule.name());
            return false;
        }

        log.info("Claimed and executing scheduled workflow rule: '{}'", rule.name());

        try {
            workflowEngine.executeScheduledRule(rule);
        } catch (Exception e) {
            log.error("Error executing scheduled rule '{}': {}", rule.name(), e.getMessage(), e);
        }

        return true;
    }

    /**
     * Resolves the timezone for a workflow rule, falling back to UTC.
     */
    private ZoneId resolveTimezone(WorkflowRuleData rule) {
        String timezone = rule.timezone();
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for rule '{}', falling back to UTC",
                timezone, rule.name());
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
