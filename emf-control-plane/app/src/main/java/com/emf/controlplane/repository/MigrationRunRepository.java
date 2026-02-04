package com.emf.controlplane.repository;

import com.emf.controlplane.entity.MigrationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for MigrationRun entity operations.
 */
@Repository
public interface MigrationRunRepository extends JpaRepository<MigrationRun, String> {

    /**
     * Find all migration runs for a collection ordered by creation date descending.
     */
    List<MigrationRun> findByCollectionIdOrderByCreatedAtDesc(String collectionId);

    /**
     * Find all migration runs with pagination ordered by creation date descending.
     */
    Page<MigrationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find migration runs by status.
     */
    List<MigrationRun> findByStatus(String status);

    /**
     * Find migration runs for a collection by status.
     */
    List<MigrationRun> findByCollectionIdAndStatus(String collectionId, String status);

    /**
     * Count migration runs by status.
     */
    long countByStatus(String status);

    /**
     * Count migration runs for a collection.
     */
    long countByCollectionId(String collectionId);
}
