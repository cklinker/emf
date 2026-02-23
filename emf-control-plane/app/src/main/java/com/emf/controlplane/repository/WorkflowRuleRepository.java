package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, String> {

    List<WorkflowRule> findByTenantIdOrderByNameAsc(String tenantId);

    List<WorkflowRule> findByTenantIdAndCollectionIdAndActiveTrueOrderByExecutionOrderAsc(
            String tenantId, String collectionId);

    List<WorkflowRule> findByTenantIdAndCollectionIdAndTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(
            String tenantId, String collectionId, String triggerType);

    /**
     * Finds all active SCHEDULED workflow rules across all tenants.
     */
    List<WorkflowRule> findByTriggerTypeAndActiveTrueOrderByExecutionOrderAsc(String triggerType);
}
