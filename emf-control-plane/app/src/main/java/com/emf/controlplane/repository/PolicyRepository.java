package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Policy entity operations.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, String> {

    /**
     * Find policy by name.
     */
    Optional<Policy> findByName(String name);

    /**
     * Check if a policy with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Find all policies ordered by name.
     */
    List<Policy> findAllByOrderByNameAsc();
}
