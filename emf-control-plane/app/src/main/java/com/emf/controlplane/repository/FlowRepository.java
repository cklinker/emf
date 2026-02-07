package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Flow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowRepository extends JpaRepository<Flow, String> {

    List<Flow> findByTenantIdOrderByNameAsc(String tenantId);

    List<Flow> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    List<Flow> findByTenantIdAndFlowTypeOrderByNameAsc(String tenantId, String flowType);
}
