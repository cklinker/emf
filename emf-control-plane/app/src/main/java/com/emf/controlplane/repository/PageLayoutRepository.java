package com.emf.controlplane.repository;

import com.emf.controlplane.entity.PageLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageLayoutRepository extends JpaRepository<PageLayout, String> {

    List<PageLayout> findByTenantIdAndCollectionIdOrderByNameAsc(String tenantId, String collectionId);

    Optional<PageLayout> findByTenantIdAndCollectionIdAndName(String tenantId, String collectionId, String name);

    boolean existsByTenantIdAndCollectionIdAndName(String tenantId, String collectionId, String name);

    Optional<PageLayout> findByTenantIdAndCollectionIdAndIsDefaultTrue(String tenantId, String collectionId);
}
