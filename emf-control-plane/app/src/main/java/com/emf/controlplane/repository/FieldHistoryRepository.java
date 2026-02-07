package com.emf.controlplane.repository;

import com.emf.controlplane.entity.FieldHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for FieldHistory entity operations.
 */
@Repository
public interface FieldHistoryRepository extends JpaRepository<FieldHistory, String> {

    Page<FieldHistory> findByCollectionIdAndRecordIdOrderByChangedAtDesc(
            String collectionId, String recordId, Pageable pageable);

    Page<FieldHistory> findByCollectionIdAndRecordIdAndFieldNameOrderByChangedAtDesc(
            String collectionId, String recordId, String fieldName, Pageable pageable);

    Page<FieldHistory> findByCollectionIdAndFieldNameOrderByChangedAtDesc(
            String collectionId, String fieldName, Pageable pageable);

    Page<FieldHistory> findByChangedByOrderByChangedAtDesc(
            String userId, Pageable pageable);

    long countByCollectionIdAndRecordId(String collectionId, String recordId);
}
