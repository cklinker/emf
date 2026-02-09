package com.emf.controlplane.repository;

import com.emf.controlplane.entity.CollectionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CollectionAssignment entities.
 */
@Repository
public interface CollectionAssignmentRepository extends JpaRepository<CollectionAssignment, String> {

    List<CollectionAssignment> findByWorkerId(String workerId);

    List<CollectionAssignment> findByCollectionId(String collectionId);

    List<CollectionAssignment> findByWorkerIdAndStatus(String workerId, String status);

    Optional<CollectionAssignment> findByCollectionIdAndWorkerId(String collectionId, String workerId);

    long countByWorkerId(String workerId);

    long countByWorkerIdAndStatus(String workerId, String status);

    List<CollectionAssignment> findByStatus(String status);
}
