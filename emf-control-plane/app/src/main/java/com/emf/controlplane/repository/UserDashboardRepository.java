package com.emf.controlplane.repository;

import com.emf.controlplane.entity.UserDashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDashboardRepository extends JpaRepository<UserDashboard, String> {

    List<UserDashboard> findByTenantIdOrderByNameAsc(String tenantId);

    @Query("SELECT d FROM UserDashboard d WHERE d.tenantId = :tenantId " +
            "AND (d.accessLevel = 'PUBLIC' OR d.createdBy = :userId) ORDER BY d.name ASC")
    List<UserDashboard> findAccessibleDashboards(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);
}
