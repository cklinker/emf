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

    /**
     * Find all active collections with pagination.
     */
    Page<Collection> findByActiveTrue(Pageable pageable);

    /**
     * Find all active collections.
     */
    List<Collection> findByActiveTrue();

    /**
     * Find active collection by ID.
     */
    Optional<Collection> findByIdAndActiveTrue(String id);

    /**
     * Find collection by name.
     */
    Optional<Collection> findByName(String name);

    /**
     * Find active collection by name.
     */
    Optional<Collection> findByNameAndActiveTrue(String name);

    /**
     * Check if a collection with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Check if an active collection with the given name exists.
     */
    boolean existsByNameAndActiveTrue(String name);

    /**
     * Find active collections with name containing the search term (case-insensitive).
     */
    @Query("SELECT c FROM Collection c WHERE c.active = true AND LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Collection> findByActiveAndNameContaining(@Param("search") String search, Pageable pageable);

    /**
     * Find active collections with name or description containing the search term.
     */
    @Query("SELECT c FROM Collection c WHERE c.active = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Collection> findByActiveAndSearchTerm(@Param("search") String search, Pageable pageable);

    /**
     * Count active collections.
     */
    long countByActiveTrue();
    
    /**
     * Find all active collections with fields eagerly loaded.
     */
    @Query("SELECT DISTINCT c FROM Collection c LEFT JOIN FETCH c.fields WHERE c.active = true")
    List<Collection> findByActiveTrueWithFields();
}
