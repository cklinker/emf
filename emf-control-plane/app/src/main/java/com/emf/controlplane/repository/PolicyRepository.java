package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Policy entity operations.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, String> {

    // ---- Tenant-scoped methods ----

    Optional<Policy> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    List<Policy> findByTenantIdOrderByNameAsc(String tenantId);

    // ---- Legacy methods ----

    Optional<Policy> findByName(String name);

    boolean existsByName(String name);

    List<Policy> findAllByOrderByNameAsc();
}
