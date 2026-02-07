package com.emf.controlplane.repository;

import com.emf.controlplane.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, String> {

    List<EmailLog> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<EmailLog> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);

    List<EmailLog> findByTemplateIdOrderByCreatedAtDesc(String templateId);
}
