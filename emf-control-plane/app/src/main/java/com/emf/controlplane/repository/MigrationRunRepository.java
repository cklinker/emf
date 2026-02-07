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

    // ---- Tenant-scoped methods ----

    List<MigrationRun> findByTenantIdAndCollectionIdOrderByCreatedAtDesc(String tenantId, String collectionId);

    Page<MigrationRun> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<MigrationRun> findByTenantIdAndStatus(String tenantId, String status);

    List<MigrationRun> findByTenantIdAndCollectionIdAndStatus(String tenantId, String collectionId, String status);

    long countByTenantIdAndStatus(String tenantId, String status);

    long countByTenantIdAndCollectionId(String tenantId, String collectionId);

    // ---- Legacy methods ----

    List<MigrationRun> findByCollectionIdOrderByCreatedAtDesc(String collectionId);

    Page<MigrationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<MigrationRun> findByStatus(String status);

    List<MigrationRun> findByCollectionIdAndStatus(String collectionId, String status);

    long countByStatus(String status);

    long countByCollectionId(String collectionId);
}
