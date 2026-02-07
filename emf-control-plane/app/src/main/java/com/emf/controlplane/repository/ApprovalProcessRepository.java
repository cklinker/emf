package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ApprovalProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalProcessRepository extends JpaRepository<ApprovalProcess, String> {

    List<ApprovalProcess> findByTenantIdOrderByNameAsc(String tenantId);

    List<ApprovalProcess> findByTenantIdAndCollectionIdAndActiveTrueOrderByExecutionOrderAsc(
            String tenantId, String collectionId);
}
