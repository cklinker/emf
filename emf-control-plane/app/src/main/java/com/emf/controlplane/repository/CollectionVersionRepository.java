package com.emf.controlplane.repository;

import com.emf.controlplane.entity.CollectionVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CollectionVersion entity operations.
 */
@Repository
public interface CollectionVersionRepository extends JpaRepository<CollectionVersion, String> {

    /**
     * Find all versions for a collection ordered by version descending.
     */
    @Query("SELECT cv FROM CollectionVersion cv JOIN FETCH cv.collection WHERE cv.collection.id = :collectionId ORDER BY cv.version DESC")
    List<CollectionVersion> findByCollectionIdOrderByVersionDesc(@Param("collectionId") String collectionId);

    /**
     * Find all versions for a collection with pagination.
     */
    Page<CollectionVersion> findByCollectionId(String collectionId, Pageable pageable);

    /**
     * Find a specific version of a collection.
     */
    @Query("SELECT cv FROM CollectionVersion cv JOIN FETCH cv.collection WHERE cv.collection.id = :collectionId AND cv.version = :version")
    Optional<CollectionVersion> findByCollectionIdAndVersion(@Param("collectionId") String collectionId, @Param("version") Integer version);

    /**
     * Find the latest version for a collection.
     */
    @Query("SELECT cv FROM CollectionVersion cv WHERE cv.collection.id = :collectionId ORDER BY cv.version DESC LIMIT 1")
    Optional<CollectionVersion> findLatestByCollectionId(@Param("collectionId") String collectionId);

    /**
     * Get the maximum version number for a collection.
     */
    @Query("SELECT MAX(cv.version) FROM CollectionVersion cv WHERE cv.collection.id = :collectionId")
    Optional<Integer> findMaxVersionByCollectionId(@Param("collectionId") String collectionId);

    /**
     * Count versions for a collection.
     */
    long countByCollectionId(String collectionId);

    /**
     * Check if a specific version exists for a collection.
     */
    boolean existsByCollectionIdAndVersion(String collectionId, Integer version);
}
