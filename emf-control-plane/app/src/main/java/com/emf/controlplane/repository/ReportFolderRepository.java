package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ReportFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportFolderRepository extends JpaRepository<ReportFolder, String> {

    List<ReportFolder> findByTenantIdOrderByNameAsc(String tenantId);

    boolean existsByTenantIdAndNameAndCreatedBy(String tenantId, String name, String createdBy);
}
