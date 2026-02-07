package com.emf.controlplane.repository;

import com.emf.controlplane.entity.LayoutAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LayoutAssignmentRepository extends JpaRepository<LayoutAssignment, String> {

    List<LayoutAssignment> findByTenantIdAndCollectionId(String tenantId, String collectionId);

    Optional<LayoutAssignment> findByTenantIdAndCollectionIdAndProfileIdAndRecordTypeId(
            String tenantId, String collectionId, String profileId, String recordTypeId);

    Optional<LayoutAssignment> findByTenantIdAndCollectionIdAndProfileIdAndRecordTypeIdIsNull(
            String tenantId, String collectionId, String profileId);

    void deleteByLayoutId(String layoutId);
}
