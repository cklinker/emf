package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, String> {

    List<UserGroup> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<UserGroup> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    @Query("SELECT g FROM UserGroup g JOIN g.members m WHERE m.id = :userId")
    List<UserGroup> findGroupsByUserId(@Param("userId") String userId);

    /**
     * Find an OIDC-synced group by tenant and original OIDC group name.
     */
    Optional<UserGroup> findByTenantIdAndOidcGroupName(String tenantId, String oidcGroupName);

    /**
     * Find all groups by source type within a tenant.
     */
    List<UserGroup> findByTenantIdAndSourceOrderByNameAsc(String tenantId, String source);

    /**
     * Find all OIDC-synced groups for a tenant.
     */
    @Query("SELECT g FROM UserGroup g WHERE g.tenantId = :tenantId AND g.source = 'OIDC' ORDER BY g.name ASC")
    List<UserGroup> findOidcGroupsByTenantId(@Param("tenantId") String tenantId);

    /**
     * Find the system "All Authenticated Users" group for a tenant.
     */
    @Query("SELECT g FROM UserGroup g WHERE g.tenantId = :tenantId AND g.source = 'SYSTEM' AND g.name = 'All Authenticated Users'")
    Optional<UserGroup> findAllAuthenticatedUsersGroup(@Param("tenantId") String tenantId);
}
