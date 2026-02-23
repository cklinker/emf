package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowActionLogRepository extends JpaRepository<WorkflowActionLog, String> {

    List<WorkflowActionLog> findByExecutionLogIdOrderByExecutedAtAsc(String executionLogId);

    List<WorkflowActionLog> findByActionIdOrderByExecutedAtDesc(String actionId);
}
