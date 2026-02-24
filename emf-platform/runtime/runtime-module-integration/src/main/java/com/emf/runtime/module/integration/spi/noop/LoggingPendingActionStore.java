package com.emf.runtime.module.integration.spi.noop;

import com.emf.runtime.module.integration.spi.PendingActionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * No-op implementation of {@link PendingActionStore} that logs operations
 * and returns dummy IDs. Used as a default when no real persistence is configured.
 *
 * @since 1.0.0
 */
public class LoggingPendingActionStore implements PendingActionStore {

    private static final Logger log = LoggerFactory.getLogger(LoggingPendingActionStore.class);

    @Override
    public String save(String tenantId, String executionLogId, String workflowRuleId,
                       int actionIndex, String recordId, Instant scheduledAt, String recordSnapshot) {
        String id = UUID.randomUUID().toString();
        log.info("[NOOP] PendingActionStore.save: id={}, tenant={}, rule={}, record={}, scheduledAt={}",
            id, tenantId, workflowRuleId, recordId, scheduledAt);
        return id;
    }
}
