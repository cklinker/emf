package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowActionRepository extends JpaRepository<WorkflowAction, String> {

    List<WorkflowAction> findByWorkflowRuleIdOrderByExecutionOrderAsc(String workflowRuleId);
}
