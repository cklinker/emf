package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ConfigPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfigPackage entity operations.
 */
@Repository
public interface PackageRepository extends JpaRepository<ConfigPackage, String> {

    // ---- Tenant-scoped methods ----

    Optional<ConfigPackage> findByTenantIdAndName(String tenantId, String name);

    Optional<ConfigPackage> findByTenantIdAndNameAndVersion(String tenantId, String name, String version);

    List<ConfigPackage> findByTenantIdAndNameOrderByCreatedAtDesc(String tenantId, String name);

    List<ConfigPackage> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Page<ConfigPackage> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    boolean existsByTenantIdAndNameAndVersion(String tenantId, String name, String version);

    // ---- Legacy methods ----

    Optional<ConfigPackage> findByName(String name);

    Optional<ConfigPackage> findByNameAndVersion(String name, String version);

    List<ConfigPackage> findByNameOrderByCreatedAtDesc(String name);

    List<ConfigPackage> findAllByOrderByCreatedAtDesc();

    Page<ConfigPackage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByNameAndVersion(String name, String version);
}
