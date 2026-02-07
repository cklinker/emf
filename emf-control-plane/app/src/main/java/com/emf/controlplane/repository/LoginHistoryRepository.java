package com.emf.controlplane.repository;

import com.emf.controlplane.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Repository for LoginHistory entity operations.
 */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, String> {

    Page<LoginHistory> findByUserIdOrderByLoginTimeDesc(String userId, Pageable pageable);

    Page<LoginHistory> findByTenantIdOrderByLoginTimeDesc(String tenantId, Pageable pageable);

    long countByUserIdAndStatusAndLoginTimeAfter(String userId, String status, Instant after);
}
