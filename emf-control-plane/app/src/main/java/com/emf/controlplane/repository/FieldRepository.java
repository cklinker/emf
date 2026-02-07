package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Field;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Field entity operations.
 */
@Repository
public interface FieldRepository extends JpaRepository<Field, String> {

    /**
     * Find all fields for a collection.
     */
    List<Field> findByCollectionId(String collectionId);

    /**
     * Find all active fields for a collection.
     */
    List<Field> findByCollectionIdAndActiveTrue(String collectionId);

    /**
     * Find field by ID and collection ID.
     */
    Optional<Field> findByIdAndCollectionId(String id, String collectionId);

    /**
     * Find active field by ID and collection ID.
     */
    Optional<Field> findByIdAndCollectionIdAndActiveTrue(String id, String collectionId);

    /**
     * Find field by name within a collection.
     */
    Optional<Field> findByCollectionIdAndName(String collectionId, String name);

    /**
     * Find active field by name within a collection.
     */
    Optional<Field> findByCollectionIdAndNameAndActiveTrue(String collectionId, String name);

    /**
     * Check if a field with the given name exists in the collection.
     */
    boolean existsByCollectionIdAndName(String collectionId, String name);

    /**
     * Check if an active field with the given name exists in the collection.
     */
    boolean existsByCollectionIdAndNameAndActiveTrue(String collectionId, String name);

    /**
     * Count active fields for a collection.
     */
    long countByCollectionIdAndActiveTrue(String collectionId);

    /**
     * Find all required fields for a collection.
     */
    @Query("SELECT f FROM Field f WHERE f.collection.id = :collectionId AND f.active = true AND f.required = true")
    List<Field> findRequiredFieldsByCollectionId(@Param("collectionId") String collectionId);

    /**
     * Count active MASTER_DETAIL relationship fields in a collection.
     */
    @Query("SELECT COUNT(f) FROM Field f WHERE f.collection.id = :collectionId AND f.active = true AND f.relationshipType = 'MASTER_DETAIL'")
    long countMasterDetailFieldsByCollectionId(@Param("collectionId") String collectionId);

    /**
     * Find all active fields that reference a given collection (by referenceCollectionId).
     */
    List<Field> findByReferenceCollectionIdAndActiveTrue(String referenceCollectionId);

    /**
     * Find all active fields with history tracking enabled for a collection.
     */
    List<Field> findByCollectionIdAndTrackHistoryTrueAndActiveTrue(String collectionId);
}
