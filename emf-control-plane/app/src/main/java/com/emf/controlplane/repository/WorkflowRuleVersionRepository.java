package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRuleVersionRepository extends JpaRepository<WorkflowRuleVersion, String> {

    List<WorkflowRuleVersion> findByWorkflowRuleIdOrderByVersionNumberDesc(String workflowRuleId);

    Optional<WorkflowRuleVersion> findByWorkflowRuleIdAndVersionNumber(String workflowRuleId, int versionNumber);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM WorkflowRuleVersion v WHERE v.workflowRuleId = :workflowRuleId")
    int findMaxVersionNumber(String workflowRuleId);
}
