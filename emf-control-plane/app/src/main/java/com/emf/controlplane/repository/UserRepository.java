package com.emf.controlplane.repository;

import com.emf.controlplane.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // ---- Tenant-scoped methods ----

    Optional<User> findByTenantIdAndEmail(String tenantId, String email);

    Optional<User> findByIdAndTenantId(String id, String tenantId);

    Page<User> findByTenantId(String tenantId, Pageable pageable);

    Page<User> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    boolean existsByTenantIdAndEmail(String tenantId, String email);

    boolean existsByTenantId(String tenantId);

    long countByTenantIdAndStatus(String tenantId, String status);

    long countByTenantId(String tenantId);

    List<User> findByManagerId(String managerId);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND (" +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :filter, '%')))")
    Page<User> findByTenantIdAndFilter(
            @Param("tenantId") String tenantId,
            @Param("filter") String filter,
            Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.status = :status AND (" +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :filter, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :filter, '%')))")
    Page<User> findByTenantIdAndStatusAndFilter(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("filter") String filter,
            Pageable pageable);
}
