package com.emf.controlplane.repository;

import com.emf.controlplane.entity.RecordType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecordTypeRepository extends JpaRepository<RecordType, String> {

    List<RecordType> findByCollectionIdAndActiveTrueOrderByNameAsc(String collectionId);

    List<RecordType> findByCollectionIdOrderByNameAsc(String collectionId);

    Optional<RecordType> findByTenantIdAndCollectionIdAndName(
            String tenantId, String collectionId, String name);

    boolean existsByTenantIdAndCollectionIdAndName(
            String tenantId, String collectionId, String name);

    Optional<RecordType> findByCollectionIdAndIsDefaultTrue(String collectionId);
}
