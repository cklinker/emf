package com.emf.controlplane.repository;

import com.emf.controlplane.entity.OrgWideDefault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrgWideDefaultRepository extends JpaRepository<OrgWideDefault, String> {

    Optional<OrgWideDefault> findByTenantIdAndCollectionId(String tenantId, String collectionId);

    List<OrgWideDefault> findByTenantId(String tenantId);

    void deleteByTenantIdAndCollectionId(String tenantId, String collectionId);
}
