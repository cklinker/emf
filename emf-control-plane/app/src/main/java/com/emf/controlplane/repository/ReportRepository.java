package com.emf.controlplane.repository;

import com.emf.controlplane.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {

    List<Report> findByTenantIdOrderByNameAsc(String tenantId);

    @Query("SELECT r FROM Report r WHERE r.tenantId = :tenantId " +
           "AND (r.accessLevel = 'PUBLIC' OR r.createdBy = :userId) ORDER BY r.name ASC")
    List<Report> findAccessibleReports(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    List<Report> findByTenantIdAndPrimaryCollectionIdOrderByNameAsc(String tenantId, String collectionId);

    List<Report> findByFolderIdOrderByNameAsc(String folderId);
}
