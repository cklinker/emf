package com.emf.controlplane.repository;

import com.emf.controlplane.entity.FlowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowExecutionRepository extends JpaRepository<FlowExecution, String> {

    List<FlowExecution> findByTenantIdOrderByStartedAtDesc(String tenantId);

    List<FlowExecution> findByFlowIdOrderByStartedAtDesc(String flowId);

    List<FlowExecution> findByTenantIdAndStatusOrderByStartedAtDesc(String tenantId, String status);
}
