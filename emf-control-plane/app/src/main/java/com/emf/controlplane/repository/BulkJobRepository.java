package com.emf.controlplane.repository;

import com.emf.controlplane.entity.BulkJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulkJobRepository extends JpaRepository<BulkJob, String> {

    List<BulkJob> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<BulkJob> findByStatus(String status);
}
