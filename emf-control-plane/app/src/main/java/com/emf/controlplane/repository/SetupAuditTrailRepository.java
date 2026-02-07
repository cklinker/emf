package com.emf.controlplane.repository;

import com.emf.controlplane.entity.SetupAuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SetupAuditTrailRepository extends JpaRepository<SetupAuditTrail, String> {

    Page<SetupAuditTrail> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    @Query("SELECT a FROM SetupAuditTrail a WHERE a.tenantId = :tenantId " +
           "AND (:section IS NULL OR a.section = :section) " +
           "AND (:entityType IS NULL OR a.entityType = :entityType) " +
           "AND (:userId IS NULL OR a.userId = :userId) " +
           "AND (:from IS NULL OR a.timestamp >= :from) " +
           "AND (:to IS NULL OR a.timestamp <= :to) " +
           "ORDER BY a.timestamp DESC")
    Page<SetupAuditTrail> findFiltered(
            @Param("tenantId") String tenantId,
            @Param("section") String section,
            @Param("entityType") String entityType,
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    Page<SetupAuditTrail> findByTenantIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
            String tenantId, String entityType, String entityId, Pageable pageable);
}
