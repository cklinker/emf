package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.WorkflowRule;
import com.emf.controlplane.repository.WorkflowRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Polls for active SCHEDULED workflow rules and executes them when their
 * cron expression is due.
 * <p>
 * Runs every 60 seconds (configurable via {@code emf.workflow.scheduled.poll-interval-ms}).
 * For each SCHEDULED rule, evaluates the cron expression against the last scheduled run
 * to determine if execution is due. When due, delegates execution to
 * {@link WorkflowEngine#executeScheduledRule(WorkflowRule)}.
 * <p>
 * Handles timezone-aware cron evaluation: each rule can specify its own timezone.
 */
@Service
public class ScheduledWorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledWorkflowExecutor.class);
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowEngine workflowEngine;

    public ScheduledWorkflowExecutor(WorkflowRuleRepository ruleRepository,
                                       WorkflowEngine workflowEngine) {
        this.ruleRepository = ruleRepository;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Polls for due scheduled workflow rules every 60 seconds.
     */
    @Scheduled(fixedRateString = "${emf.workflow.scheduled.poll-interval-ms:60000}")
    public void pollScheduledWorkflows() {
        List<WorkflowRule> scheduledRules = ruleRepository
            .findByTriggerTypeAndActiveTrueOrderByExecutionOrderAsc("SCHEDULED");

        if (scheduledRules.isEmpty()) {
            return;
        }

        log.debug("Checking {} scheduled workflow rules", scheduledRules.size());
        int executed = 0;

        for (WorkflowRule rule : scheduledRules) {
            try {
                if (isDue(rule)) {
                    executeAndUpdateLastRun(rule);
                    executed++;
                }
            } catch (Exception e) {
                log.error("Error processing scheduled rule '{}': {}", rule.getName(), e.getMessage(), e);
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
    boolean isDue(WorkflowRule rule) {
        String cronExpr = rule.getCronExpression();
        if (cronExpr == null || cronExpr.isBlank()) {
            log.warn("Scheduled rule '{}' has no cron expression, skipping", rule.getName());
            return false;
        }

        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            ZoneId zoneId = resolveTimezone(rule);
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            // Determine the reference point: last run time or rule creation time
            Instant lastRun = rule.getLastScheduledRun();
            LocalDateTime referenceTime;

            if (lastRun != null) {
                referenceTime = LocalDateTime.ofInstant(lastRun, zoneId);
            } else {
                // Never run before — use creation time as the reference
                referenceTime = rule.getCreatedAt() != null
                    ? LocalDateTime.ofInstant(rule.getCreatedAt(), zoneId)
                    : now.toLocalDateTime().minusDays(1);
            }

            // Find the next scheduled execution after the last run
            LocalDateTime nextExecution = cron.next(referenceTime);

            if (nextExecution == null) {
                log.warn("Cron expression for rule '{}' has no next execution", rule.getName());
                return false;
            }

            // Due if the next execution is at or before now
            ZonedDateTime nextZoned = nextExecution.atZone(zoneId);
            return !nextZoned.isAfter(now);

        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression '{}' for rule '{}': {}",
                cronExpr, rule.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Executes the scheduled rule and updates its last scheduled run timestamp.
     */
    private void executeAndUpdateLastRun(WorkflowRule rule) {
        log.info("Executing scheduled workflow rule: '{}'", rule.getName());
        workflowEngine.executeScheduledRule(rule);
        rule.setLastScheduledRun(Instant.now());
        ruleRepository.save(rule);
    }

    /**
     * Resolves the timezone for a workflow rule, falling back to UTC.
     */
    private ZoneId resolveTimezone(WorkflowRule rule) {
        String timezone = rule.getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for rule '{}', falling back to UTC",
                timezone, rule.getName());
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
