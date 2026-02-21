package com.emf.controlplane.repository;

import com.emf.controlplane.entity.SecurityAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, String> {

    Page<SecurityAuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Page<SecurityAuditLog> findByTenantIdAndEventCategoryOrderByCreatedAtDesc(
            String tenantId, String eventCategory, Pageable pageable);

    Page<SecurityAuditLog> findByTenantIdAndEventTypeOrderByCreatedAtDesc(
            String tenantId, String eventType, Pageable pageable);

    Page<SecurityAuditLog> findByTenantIdAndActorUserIdOrderByCreatedAtDesc(
            String tenantId, String actorUserId, Pageable pageable);

    @Query("SELECT a FROM SecurityAuditLog a WHERE a.tenantId = :tenantId " +
           "AND a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<SecurityAuditLog> findByTenantIdAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    @Query("SELECT a FROM SecurityAuditLog a WHERE a.tenantId = :tenantId " +
           "AND (:category IS NULL OR a.eventCategory = :category) " +
           "AND (:eventType IS NULL OR a.eventType = :eventType) " +
           "AND (:actorId IS NULL OR a.actorUserId = :actorId) " +
           "ORDER BY a.createdAt DESC")
    Page<SecurityAuditLog> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("category") String category,
            @Param("eventType") String eventType,
            @Param("actorId") String actorId,
            Pageable pageable);

    long countByTenantIdAndEventCategory(String tenantId, String eventCategory);

    long countByTenantIdAndEventType(String tenantId, String eventType);

    long countByTenantIdAndCreatedAtAfter(String tenantId, Instant after);
}
