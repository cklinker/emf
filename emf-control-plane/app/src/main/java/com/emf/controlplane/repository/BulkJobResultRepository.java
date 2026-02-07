package com.emf.controlplane.repository;

import com.emf.controlplane.entity.BulkJobResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulkJobResultRepository extends JpaRepository<BulkJobResult, String> {

    List<BulkJobResult> findByBulkJobIdOrderByRecordIndexAsc(String bulkJobId);

    List<BulkJobResult> findByBulkJobIdAndStatus(String bulkJobId, String status);
}
