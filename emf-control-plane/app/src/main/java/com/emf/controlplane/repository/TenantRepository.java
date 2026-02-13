package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Tenant entity operations.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByIdAndStatus(String id, String status);

    boolean existsBySlug(String slug);

    List<Tenant> findByStatus(String status);

    List<Tenant> findByStatusIn(Collection<String> statuses);

    List<Tenant> findByStatusNot(String status);

    Page<Tenant> findAll(Pageable pageable);
}
