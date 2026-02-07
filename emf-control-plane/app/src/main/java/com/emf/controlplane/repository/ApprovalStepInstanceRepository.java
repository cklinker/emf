package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ApprovalStepInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalStepInstanceRepository extends JpaRepository<ApprovalStepInstance, String> {

    List<ApprovalStepInstance> findByAssignedToAndStatusOrderByApprovalInstanceSubmittedAtDesc(
            String assignedTo, String status);

    List<ApprovalStepInstance> findByApprovalInstanceIdOrderByStepStepNumberAsc(String approvalInstanceId);
}
