package com.emf.controlplane.service;

import com.emf.controlplane.dto.SetupAuditTrailDto;
import com.emf.controlplane.entity.SetupAuditTrail;
import com.emf.controlplane.repository.SetupAuditTrailRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SetupAuditService {

    private static final Logger log = LoggerFactory.getLogger(SetupAuditService.class);

    private final SetupAuditTrailRepository auditRepository;
    private final ObjectMapper objectMapper;

    public SetupAuditService(SetupAuditTrailRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String section, String entityType,
                    String entityId, String entityName,
                    Object oldValue, Object newValue) {
        String tenantId = TenantContextHolder.getTenantId();
        String userId = getCurrentUserId();

        if (tenantId == null || userId == null) {
            SetupAuditService.log.debug("Skipping audit log: tenantId={}, userId={}", tenantId, userId);
            return;
        }

        SetupAuditTrail trail = new SetupAuditTrail(
                tenantId, userId, action, section, entityType,
                entityId, entityName,
                serialize(oldValue), serialize(newValue));

        auditRepository.save(trail);
        SetupAuditService.log.debug("Audit logged: {} {} {} {}", action, entityType, entityId, entityName);
    }

    @Transactional(readOnly = true)
    public Page<SetupAuditTrailDto> getAuditTrail(String section, String entityType,
                                                   String userId, Instant from, Instant to,
                                                   Pageable pageable) {
        String tenantId = TenantContextHolder.requireTenantId();
        return auditRepository.findFiltered(tenantId, section, entityType, userId, from, to, pageable)
                .map(SetupAuditTrailDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<SetupAuditTrailDto> getEntityHistory(String entityType, String entityId, Pageable pageable) {
        String tenantId = TenantContextHolder.requireTenantId();
        return auditRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByTimestampDesc(
                tenantId, entityType, entityId, pageable)
                .map(SetupAuditTrailDto::fromEntity);
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return null;
    }

    private String serialize(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            SetupAuditService.log.warn("Failed to serialize audit value", e);
            return value.toString();
        }
    }
}
