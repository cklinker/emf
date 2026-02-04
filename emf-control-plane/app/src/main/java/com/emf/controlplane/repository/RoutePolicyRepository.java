package com.emf.controlplane.repository;

import com.emf.controlplane.entity.RoutePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RoutePolicy entity operations.
 */
@Repository
public interface RoutePolicyRepository extends JpaRepository<RoutePolicy, String> {

    /**
     * Find all route policies for a collection.
     */
    List<RoutePolicy> findByCollectionId(String collectionId);

    /**
     * Find route policy by collection and operation.
     */
    Optional<RoutePolicy> findByCollectionIdAndOperation(String collectionId, String operation);

    /**
     * Find all route policies for a specific policy.
     */
    List<RoutePolicy> findByPolicyId(String policyId);

    /**
     * Delete all route policies for a collection.
     */
    void deleteByCollectionId(String collectionId);

    /**
     * Check if a route policy exists for a collection and operation.
     */
    boolean existsByCollectionIdAndOperation(String collectionId, String operation);
}
