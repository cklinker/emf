package com.emf.controlplane.repository;

import com.emf.controlplane.entity.SharingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharingRuleRepository extends JpaRepository<SharingRule, String> {

    List<SharingRule> findByTenantIdAndCollectionIdAndActiveTrue(String tenantId, String collectionId);

    List<SharingRule> findByTenantIdAndCollectionId(String tenantId, String collectionId);

    List<SharingRule> findByCollectionId(String collectionId);

    List<SharingRule> findByTenantId(String tenantId);
}
