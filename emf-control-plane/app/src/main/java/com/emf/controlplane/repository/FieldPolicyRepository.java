package com.emf.controlplane.repository;

import com.emf.controlplane.entity.FieldPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FieldPolicy entity operations.
 */
@Repository
public interface FieldPolicyRepository extends JpaRepository<FieldPolicy, String> {

    /**
     * Find all field policies for a field.
     */
    List<FieldPolicy> findByFieldId(String fieldId);

    /**
     * Find field policy by field and operation.
     */
    Optional<FieldPolicy> findByFieldIdAndOperation(String fieldId, String operation);

    /**
     * Find all field policies for a specific policy.
     */
    List<FieldPolicy> findByPolicyId(String policyId);

    /**
     * Find all field policies for fields in a collection.
     */
    @Query("SELECT fp FROM FieldPolicy fp WHERE fp.field.collection.id = :collectionId")
    List<FieldPolicy> findByCollectionId(@Param("collectionId") String collectionId);

    /**
     * Delete all field policies for a field.
     */
    void deleteByFieldId(String fieldId);

    /**
     * Check if a field policy exists for a field and operation.
     */
    boolean existsByFieldIdAndOperation(String fieldId, String operation);
}
