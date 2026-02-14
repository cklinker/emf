package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Collection entity operations.
 */
@Repository
public interface CollectionRepository extends JpaRepository<Collection, String> {

    // ---- Tenant-scoped methods ----

    Page<Collection> findByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    List<Collection> findByTenantIdAndActiveTrue(String tenantId);

    Optional<Collection> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    Optional<Collection> findByTenantIdAndName(String tenantId, String name);

    Optional<Collection> findByTenantIdAndNameAndActiveTrue(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndNameAndActiveTrue(String tenantId, String name);

    @Query("SELECT c FROM Collection c WHERE c.tenantId = :tenantId AND c.active = true AND " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Collection> findByTenantIdAndActiveAndNameContaining(@Param("tenantId") String tenantId,
                                                              @Param("search") String search,
                                                              Pageable pageable);

    @Query("SELECT c FROM Collection c WHERE c.tenantId = :tenantId AND c.active = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Collection> findByTenantIdAndActiveAndSearchTerm(@Param("tenantId") String tenantId,
                                                          @Param("search") String search,
                                                          Pageable pageable);

    long countByTenantIdAndActiveTrue(String tenantId);

    @Query("SELECT DISTINCT c FROM Collection c LEFT JOIN FETCH c.fields WHERE c.tenantId = :tenantId AND c.active = true")
    List<Collection> findByTenantIdAndActiveTrueWithFields(@Param("tenantId") String tenantId);

    // ---- Legacy methods (kept for platform-level operations) ----

    Page<Collection> findByActiveTrue(Pageable pageable);

    List<Collection> findByActiveTrue();

    Optional<Collection> findByIdAndActiveTrue(String id);

    Optional<Collection> findByName(String name);

    Optional<Collection> findByNameAndActiveTrue(String name);

    boolean existsByName(String name);

    boolean existsByNameAndActiveTrue(String name);

    @Query("SELECT c FROM Collection c WHERE c.active = true AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Collection> findByActiveAndNameContaining(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM Collection c WHERE c.active = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Collection> findByActiveAndSearchTerm(@Param("search") String search, Pageable pageable);

    long countByActiveTrue();

    @Query("SELECT DISTINCT c FROM Collection c LEFT JOIN FETCH c.fields WHERE c.active = true AND c.systemCollection = false")
    List<Collection> findByActiveTrueWithFields();

    /**
     * Finds ALL active collections with fields including system collections.
     * Used by GatewayBootstrapService which needs system collections for authz cache warm-up.
     */
    @Query("SELECT DISTINCT c FROM Collection c LEFT JOIN FETCH c.fields WHERE c.active = true")
    List<Collection> findAllActiveWithFields();

    /**
     * Counts active collections that have no READY assignment.
     * Used by KEDA scaler to determine if new workers are needed.
     */
    @Query("SELECT COUNT(c) FROM Collection c WHERE c.active = true AND c.id NOT IN " +
           "(SELECT ca.collectionId FROM CollectionAssignment ca WHERE ca.status = 'READY')")
    long countUnassignedCollections();
}
