package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UiPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UiPage entity operations.
 */
@Repository
public interface UiPageRepository extends JpaRepository<UiPage, String> {

    // ---- Tenant-scoped methods ----

    List<UiPage> findByTenantIdAndActiveTrue(String tenantId);

    List<UiPage> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    Optional<UiPage> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    Optional<UiPage> findByTenantIdAndPath(String tenantId, String path);

    Optional<UiPage> findByTenantIdAndPathAndActiveTrue(String tenantId, String path);

    Optional<UiPage> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndPath(String tenantId, String path);

    boolean existsByTenantIdAndPathAndActiveTrue(String tenantId, String path);

    long countByTenantIdAndActiveTrue(String tenantId);

    // ---- Legacy methods ----

    List<UiPage> findByActiveTrue();

    List<UiPage> findByActiveTrueOrderByNameAsc();

    Optional<UiPage> findByIdAndActiveTrue(String id);

    Optional<UiPage> findByPath(String path);

    Optional<UiPage> findByPathAndActiveTrue(String path);

    Optional<UiPage> findByName(String name);

    boolean existsByPath(String path);

    boolean existsByPathAndActiveTrue(String path);

    long countByActiveTrue();
}
