package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Service entities.
 */
@Repository
public interface ServiceRepository extends JpaRepository<Service, String> {

    // ---- Tenant-scoped methods ----

    Page<Service> findByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    Optional<Service> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    Optional<Service> findByTenantIdAndNameAndActiveTrue(String tenantId, String name);

    boolean existsByTenantIdAndNameAndActiveTrue(String tenantId, String name);

    @Query("SELECT s FROM Service s WHERE s.tenantId = :tenantId AND s.active = true AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Service> findByTenantIdAndActiveAndSearchTerm(@Param("tenantId") String tenantId,
                                                       @Param("searchTerm") String searchTerm,
                                                       Pageable pageable);

    // ---- Legacy methods (kept for platform-level operations) ----

    Page<Service> findByActiveTrue(Pageable pageable);

    Optional<Service> findByIdAndActiveTrue(String id);

    Optional<Service> findByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrue(String name);

    @Query("SELECT s FROM Service s WHERE s.active = true AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Service> findByActiveAndSearchTerm(@Param("searchTerm") String searchTerm, Pageable pageable);
}
