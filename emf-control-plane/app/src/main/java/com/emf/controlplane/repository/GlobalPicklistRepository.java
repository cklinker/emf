package com.emf.controlplane.repository;

import com.emf.controlplane.entity.GlobalPicklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalPicklistRepository extends JpaRepository<GlobalPicklist, String> {

    List<GlobalPicklist> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<GlobalPicklist> findByTenantIdAndName(String tenantId, String name);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
