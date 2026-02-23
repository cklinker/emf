package com.emf.controlplane.service.workflow;

import com.emf.controlplane.entity.WorkflowPendingAction;
import com.emf.controlplane.repository.WorkflowPendingActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls for due pending actions (created by {@link com.emf.controlplane.service.workflow.handlers.DelayActionHandler})
 * and marks them as executed.
 * <p>
 * Runs on a configurable schedule (default: every 60 seconds).
 * Future enhancement: resume the workflow from the saved action index.
 */
@Service
public class PendingActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(PendingActionExecutor.class);

    private final WorkflowPendingActionRepository pendingActionRepository;

    public PendingActionExecutor(WorkflowPendingActionRepository pendingActionRepository) {
        this.pendingActionRepository = pendingActionRepository;
    }

    /**
     * Polls for pending actions that are due for execution.
     */
    @Scheduled(fixedDelayString = "${emf.workflow.pending-action-poll-ms:60000}")
    @Transactional
    public void pollAndExecute() {
        List<WorkflowPendingAction> dueActions = pendingActionRepository
            .findByStatusAndScheduledAtBeforeOrderByScheduledAtAsc("PENDING", Instant.now());

        if (dueActions.isEmpty()) {
            return;
        }

        log.info("Found {} pending workflow actions due for execution", dueActions.size());

        for (WorkflowPendingAction pending : dueActions) {
            try {
                executePendingAction(pending);
            } catch (Exception e) {
                log.error("Error executing pending action {}: {}", pending.getId(), e.getMessage(), e);
                pending.setStatus("FAILED");
                pendingActionRepository.save(pending);
            }
        }
    }

    /**
     * Executes a single pending action by marking it as executed.
     * <p>
     * Future enhancement: resume the workflow from the saved action index,
     * executing remaining actions in the rule.
     */
    void executePendingAction(WorkflowPendingAction pending) {
        log.info("Executing pending action: id={}, rule={}, record={}, scheduledAt={}",
            pending.getId(), pending.getWorkflowRuleId(),
            pending.getRecordId(), pending.getScheduledAt());

        pending.setStatus("EXECUTED");
        pendingActionRepository.save(pending);

        log.info("Pending action {} marked as executed", pending.getId());
    }
}
