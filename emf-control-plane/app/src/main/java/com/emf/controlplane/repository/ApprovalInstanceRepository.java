package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ApprovalInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, String> {

    List<ApprovalInstance> findByTenantIdOrderBySubmittedAtDesc(String tenantId);

    List<ApprovalInstance> findByCollectionIdAndRecordIdOrderBySubmittedAtDesc(
            String collectionId, String recordId);

    List<ApprovalInstance> findBySubmittedByOrderBySubmittedAtDesc(String submittedBy);

    @Query("SELECT ai FROM ApprovalInstance ai JOIN ai.stepInstances si " +
           "WHERE si.assignedTo = :userId AND si.status = 'PENDING' ORDER BY ai.submittedAt DESC")
    List<ApprovalInstance> findPendingForUser(@Param("userId") String userId);
}
