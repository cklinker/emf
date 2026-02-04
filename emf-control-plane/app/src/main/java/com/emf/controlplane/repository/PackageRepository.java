package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ConfigPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfigPackage entity operations.
 */
@Repository
public interface PackageRepository extends JpaRepository<ConfigPackage, String> {

    /**
     * Find package by name.
     */
    Optional<ConfigPackage> findByName(String name);

    /**
     * Find package by name and version.
     */
    Optional<ConfigPackage> findByNameAndVersion(String name, String version);

    /**
     * Find all packages with a given name ordered by creation date descending.
     */
    List<ConfigPackage> findByNameOrderByCreatedAtDesc(String name);

    /**
     * Find all packages ordered by creation date descending.
     */
    List<ConfigPackage> findAllByOrderByCreatedAtDesc();

    /**
     * Find all packages with pagination ordered by creation date descending.
     */
    Page<ConfigPackage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Check if a package with the given name and version exists.
     */
    boolean existsByNameAndVersion(String name, String version);
}
