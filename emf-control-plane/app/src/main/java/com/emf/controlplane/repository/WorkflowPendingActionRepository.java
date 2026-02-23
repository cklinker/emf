package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowPendingAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WorkflowPendingActionRepository extends JpaRepository<WorkflowPendingAction, String> {

    /**
     * Finds pending actions that are due for execution.
     */
    List<WorkflowPendingAction> findByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
        String status, Instant now);

    /**
     * Finds pending actions for a specific workflow rule.
     */
    List<WorkflowPendingAction> findByWorkflowRuleIdAndStatus(String workflowRuleId, String status);

    /**
     * Finds pending actions for a specific tenant.
     */
    List<WorkflowPendingAction> findByTenantIdAndStatusOrderByScheduledAtAsc(String tenantId, String status);
}
