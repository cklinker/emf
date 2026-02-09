package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Worker entities.
 */
@Repository
public interface WorkerRepository extends JpaRepository<Worker, String> {

    List<Worker> findByStatus(String status);

    List<Worker> findByPool(String pool);

    List<Worker> findByPoolAndStatus(String pool, String status);

    List<Worker> findByTenantAffinity(String tenantAffinity);

    Optional<Worker> findByHost(String host);
}
