package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PermissionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionSetRepository extends JpaRepository<PermissionSet, String> {

    List<PermissionSet> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<PermissionSet> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
