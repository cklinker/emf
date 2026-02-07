package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowExecutionLogRepository extends JpaRepository<WorkflowExecutionLog, String> {

    List<WorkflowExecutionLog> findByTenantIdOrderByExecutedAtDesc(String tenantId);

    List<WorkflowExecutionLog> findByWorkflowRuleIdOrderByExecutedAtDesc(String workflowRuleId);

    List<WorkflowExecutionLog> findByRecordIdOrderByExecutedAtDesc(String recordId);
}
