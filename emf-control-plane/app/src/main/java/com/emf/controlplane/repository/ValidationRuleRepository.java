package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, String> {

    List<ValidationRule> findByCollectionIdAndActiveTrueOrderByNameAsc(String collectionId);

    List<ValidationRule> findByCollectionIdOrderByNameAsc(String collectionId);

    Optional<ValidationRule> findByTenantIdAndCollectionIdAndName(
            String tenantId, String collectionId, String name);

    boolean existsByTenantIdAndCollectionIdAndName(
            String tenantId, String collectionId, String name);

    long countByCollectionIdAndActiveTrue(String collectionId);
}
