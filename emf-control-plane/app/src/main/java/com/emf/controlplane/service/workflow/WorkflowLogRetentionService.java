package com.emf.controlplane.service.workflow;

import com.emf.controlplane.service.WorkflowRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job that cleans up old workflow execution logs and action logs.
 * <p>
 * Runs daily and deletes logs older than the configured retention period.
 * Default retention: 90 days, configurable via {@code emf.workflow.log-retention-days}.
 */
@Service
public class WorkflowLogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowLogRetentionService.class);

    private final WorkflowRuleService workflowRuleService;
    private final int retentionDays;

    public WorkflowLogRetentionService(
            WorkflowRuleService workflowRuleService,
            @Value("${emf.workflow.log-retention-days:90}") int retentionDays) {
        this.workflowRuleService = workflowRuleService;
        this.retentionDays = retentionDays;
    }

    /**
     * Runs daily at 2:00 AM to clean up old workflow logs.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldLogs() {
        if (retentionDays <= 0) {
            log.debug("Workflow log retention disabled (retention days = {})", retentionDays);
            return;
        }

        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("Running workflow log retention cleanup: deleting logs older than {} ({} days)",
            cutoffDate, retentionDays);

        try {
            int deleted = workflowRuleService.deleteLogsOlderThan(cutoffDate);
            log.info("Workflow log retention cleanup complete: {} execution logs deleted", deleted);
        } catch (Exception e) {
            log.error("Error during workflow log retention cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the configured retention period in days.
     */
    public int getRetentionDays() {
        return retentionDays;
    }
}
