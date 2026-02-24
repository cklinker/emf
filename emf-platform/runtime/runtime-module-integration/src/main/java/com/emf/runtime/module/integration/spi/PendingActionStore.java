package com.emf.runtime.module.integration.spi;

import java.time.Instant;

/**
 * SPI interface for storing pending (delayed) workflow actions.
 *
 * <p>Implementations persist pending actions that should be resumed at a scheduled time.
 * A separate executor polls for due pending actions and resumes the workflow.
 *
 * <p>The host application provides the real implementation backed by a database.
 * A no-op logging implementation is provided for testing and standalone use.
 *
 * @since 1.0.0
 */
public interface PendingActionStore {

    /**
     * Saves a pending action for delayed execution.
     *
     * @param tenantId the tenant ID
     * @param executionLogId the workflow execution log ID
     * @param workflowRuleId the workflow rule ID
     * @param actionIndex the action index within the workflow
     * @param recordId the record ID
     * @param scheduledAt when to execute the delayed action
     * @param recordSnapshot JSON snapshot of the record data at delay time
     * @return the generated pending action ID
     */
    String save(String tenantId, String executionLogId, String workflowRuleId,
                int actionIndex, String recordId, Instant scheduledAt, String recordSnapshot);
}
