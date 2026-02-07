package com.emf.controlplane.repository;

import com.emf.controlplane.entity.RecordShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordShareRepository extends JpaRepository<RecordShare, String> {

    List<RecordShare> findByCollectionIdAndRecordId(String collectionId, String recordId);

    @Query("SELECT rs FROM RecordShare rs WHERE rs.collectionId = :collectionId " +
           "AND rs.recordId = :recordId AND rs.sharedWithId = :sharedWithId " +
           "AND rs.sharedWithType = 'USER'")
    List<RecordShare> findDirectUserShares(
            @Param("collectionId") String collectionId,
            @Param("recordId") String recordId,
            @Param("sharedWithId") String sharedWithId);

    @Query("SELECT rs FROM RecordShare rs WHERE rs.collectionId = :collectionId " +
           "AND rs.recordId = :recordId AND rs.sharedWithId IN :groupIds " +
           "AND rs.sharedWithType = 'GROUP'")
    List<RecordShare> findGroupShares(
            @Param("collectionId") String collectionId,
            @Param("recordId") String recordId,
            @Param("groupIds") List<String> groupIds);

    void deleteByCollectionIdAndRecordId(String collectionId, String recordId);

    List<RecordShare> findByTenantIdAndCollectionId(String tenantId, String collectionId);
}
