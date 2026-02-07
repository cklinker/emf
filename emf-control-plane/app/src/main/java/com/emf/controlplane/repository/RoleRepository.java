package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Role entity operations.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, String> {

    // ---- Tenant-scoped methods ----

    Optional<Role> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    List<Role> findByTenantIdOrderByNameAsc(String tenantId);

    // ---- Role hierarchy methods ----

    List<Role> findByParentRoleId(String parentRoleId);

    List<Role> findByTenantIdAndParentRoleIsNull(String tenantId);

    List<Role> findByTenantIdOrderByHierarchyLevelAscNameAsc(String tenantId);

    // ---- Legacy methods ----

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    List<Role> findAllByOrderByNameAsc();
}
