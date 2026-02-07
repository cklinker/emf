package com.emf.controlplane.repository;

import com.emf.controlplane.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, String> {

    List<EmailTemplate> findByTenantIdOrderByNameAsc(String tenantId);

    List<EmailTemplate> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    List<EmailTemplate> findByTenantIdAndFolderOrderByNameAsc(String tenantId, String folder);
}
