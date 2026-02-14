package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UiMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UiMenu entity operations.
 */
@Repository
public interface UiMenuRepository extends JpaRepository<UiMenu, String> {

    // ---- Tenant-scoped methods ----

    Optional<UiMenu> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);

    List<UiMenu> findByTenantIdOrderByNameAsc(String tenantId);

    @Query("SELECT DISTINCT m FROM UiMenu m LEFT JOIN FETCH m.items WHERE m.tenantId = :tenantId ORDER BY m.displayOrder ASC, m.name ASC")
    List<UiMenu> findByTenantIdWithItemsOrderByNameAsc(@Param("tenantId") String tenantId);

    // ---- Legacy methods ----

    Optional<UiMenu> findByName(String name);

    boolean existsByName(String name);

    List<UiMenu> findAllByOrderByNameAsc();

    @Query("SELECT DISTINCT m FROM UiMenu m LEFT JOIN FETCH m.items ORDER BY m.displayOrder ASC, m.name ASC")
    List<UiMenu> findAllWithItemsOrderByNameAsc();
}
