package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateEmailTemplateRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.EmailLog;
import com.emf.controlplane.entity.EmailTemplate;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.EmailLogRepository;
import com.emf.controlplane.repository.EmailTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);

    private final EmailTemplateRepository templateRepository;
    private final EmailLogRepository logRepository;
    private final CollectionService collectionService;

    public EmailTemplateService(EmailTemplateRepository templateRepository,
                                EmailLogRepository logRepository,
                                CollectionService collectionService) {
        this.templateRepository = templateRepository;
        this.logRepository = logRepository;
        this.collectionService = collectionService;
    }

    @Transactional(readOnly = true)
    public List<EmailTemplate> listTemplates(String tenantId) {
        return templateRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<EmailTemplate> listActiveTemplates(String tenantId) {
        return templateRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public EmailTemplate getTemplate(String id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", id));
    }

    @Transactional
    @SetupAudited(section = "EmailTemplates", entityType = "EmailTemplate")
    public EmailTemplate createTemplate(String tenantId, String userId, CreateEmailTemplateRequest request) {
        log.info("Creating email template '{}' for tenant: {}", request.getName(), tenantId);

        EmailTemplate template = new EmailTemplate();
        template.setTenantId(tenantId);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setSubject(request.getSubject());
        template.setBodyHtml(request.getBodyHtml());
        template.setBodyText(request.getBodyText());
        template.setFolder(request.getFolder());
        template.setActive(request.getActive() != null ? request.getActive() : true);
        template.setCreatedBy(userId);

        if (request.getRelatedCollectionId() != null) {
            Collection collection = collectionService.getCollection(request.getRelatedCollectionId());
            template.setRelatedCollection(collection);
        }

        return templateRepository.save(template);
    }

    @Transactional
    @SetupAudited(section = "EmailTemplates", entityType = "EmailTemplate")
    public EmailTemplate updateTemplate(String id, CreateEmailTemplateRequest request) {
        log.info("Updating email template: {}", id);
        EmailTemplate template = getTemplate(id);

        if (request.getName() != null) template.setName(request.getName());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getSubject() != null) template.setSubject(request.getSubject());
        if (request.getBodyHtml() != null) template.setBodyHtml(request.getBodyHtml());
        if (request.getBodyText() != null) template.setBodyText(request.getBodyText());
        if (request.getFolder() != null) template.setFolder(request.getFolder());
        if (request.getActive() != null) template.setActive(request.getActive());

        if (request.getRelatedCollectionId() != null) {
            Collection collection = collectionService.getCollection(request.getRelatedCollectionId());
            template.setRelatedCollection(collection);
        }

        return templateRepository.save(template);
    }

    @Transactional
    @SetupAudited(section = "EmailTemplates", entityType = "EmailTemplate")
    public void deleteTemplate(String id) {
        log.info("Deleting email template: {}", id);
        EmailTemplate template = getTemplate(id);
        templateRepository.delete(template);
    }

    // --- Email Log ---

    @Transactional(readOnly = true)
    public List<EmailLog> listLogs(String tenantId) {
        return logRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<EmailLog> listLogsByStatus(String tenantId, String status) {
        return logRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }
}
