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

    /**
     * Find all active pages.
     */
    List<UiPage> findByActiveTrue();

    /**
     * Find all active pages ordered by name.
     */
    List<UiPage> findByActiveTrueOrderByNameAsc();

    /**
     * Find active page by ID.
     */
    Optional<UiPage> findByIdAndActiveTrue(String id);

    /**
     * Find page by path.
     */
    Optional<UiPage> findByPath(String path);

    /**
     * Find active page by path.
     */
    Optional<UiPage> findByPathAndActiveTrue(String path);

    /**
     * Find page by name.
     */
    Optional<UiPage> findByName(String name);

    /**
     * Check if a page with the given path exists.
     */
    boolean existsByPath(String path);

    /**
     * Check if an active page with the given path exists.
     */
    boolean existsByPathAndActiveTrue(String path);

    /**
     * Count active pages.
     */
    long countByActiveTrue();
}
