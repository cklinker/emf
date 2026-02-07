package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ListView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListViewRepository extends JpaRepository<ListView, String> {

    List<ListView> findByTenantIdAndCollectionIdOrderByNameAsc(String tenantId, String collectionId);

    @Query("SELECT v FROM ListView v WHERE v.tenantId = :tenantId AND v.collection.id = :collectionId " +
           "AND (v.visibility = 'PUBLIC' OR v.createdBy = :userId) ORDER BY v.name ASC")
    List<ListView> findAccessibleViews(
            @Param("tenantId") String tenantId,
            @Param("collectionId") String collectionId,
            @Param("userId") String userId);

    boolean existsByTenantIdAndCollectionIdAndNameAndCreatedBy(
            String tenantId, String collectionId, String name, String createdBy);
}
