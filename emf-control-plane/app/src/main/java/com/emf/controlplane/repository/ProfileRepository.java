package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, String> {

    List<Profile> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<Profile> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);

    Optional<Profile> findByTenantIdAndName(String tenantId, String name);

    long countByTenantId(String tenantId);
}
