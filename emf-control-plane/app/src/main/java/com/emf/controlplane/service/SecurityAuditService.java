package com.emf.controlplane.service;

import com.emf.controlplane.entity.SecurityAuditLog;
import com.emf.controlplane.repository.SecurityAuditLogRepository;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for logging security-related events to the audit trail.
 * Automatically captures actor, tenant, IP, and user-agent from request context.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public SecurityAuditService(SecurityAuditLogRepository repository,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Log a security audit event.
     * Uses a new transaction to ensure the audit entry is persisted
     * even if the enclosing transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String eventType, String eventCategory,
                    String targetType, String targetId, String targetName,
                    Map<String, Object> details) {
        try {
            SecurityAuditLog entry = new SecurityAuditLog();
            entry.setTenantId(TenantContextHolder.getTenantId());
            entry.setEventType(eventType);
            entry.setEventCategory(eventCategory);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setTargetName(targetName);

            // Resolve actor from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                entry.setActorEmail(auth.getName());
            }

            // Serialize details to JSON
            if (details != null && !details.isEmpty()) {
                entry.setDetails(objectMapper.writeValueAsString(details));
            }

            // Extract request info
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entry.setIpAddress(extractClientIp(request));
                entry.setUserAgent(request.getHeader("User-Agent"));
                entry.setCorrelationId(request.getHeader("X-Correlation-ID"));
            }

            repository.save(entry);
            log.debug("Audit: {} {} target={}:{}", eventCategory, eventType, targetType, targetId);
        } catch (Exception e) {
            log.error("Failed to write security audit log: {} {}: {}",
                    eventCategory, eventType, e.getMessage());
        }
    }

    // Convenience methods for common event types

    public void logPermissionDenied(String endpoint, String method, String requiredPermission) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("endpoint", endpoint);
        details.put("method", method);
        details.put("requiredPermission", requiredPermission);
        log("PERMISSION_DENIED", "AUTHZ", null, null, null, details);
    }

    public void logProfileAssigned(String userId, String email, String profileId, String profileName) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("profileName", profileName);
        details.put("targetEmail", email);
        log("PROFILE_ASSIGNED", "CONFIG", "USER", userId, email, details);
    }

    public void logProfileCreated(String profileId, String profileName) {
        log("PROFILE_CREATED", "CONFIG", "PROFILE", profileId, profileName, null);
    }

    public void logProfileUpdated(String profileId, String profileName, Map<String, Object> changes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("profileName", profileName);
        if (changes != null) details.put("changes", changes);
        log("PROFILE_UPDATED", "CONFIG", "PROFILE", profileId, profileName, details);
    }

    public void logProfileDeleted(String profileId, String profileName) {
        log("PROFILE_DELETED", "CONFIG", "PROFILE", profileId, profileName, null);
    }

    public void logPermsetCreated(String permsetId, String permsetName) {
        log("PERMSET_CREATED", "CONFIG", "PERMISSION_SET", permsetId, permsetName, null);
    }

    public void logPermsetUpdated(String permsetId, String permsetName) {
        log("PERMSET_UPDATED", "CONFIG", "PERMISSION_SET", permsetId, permsetName, null);
    }

    public void logPermsetDeleted(String permsetId, String permsetName) {
        log("PERMSET_DELETED", "CONFIG", "PERMISSION_SET", permsetId, permsetName, null);
    }

    public void logPermsetAssigned(String permsetName, String targetType, String targetId, String targetName) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("permsetName", permsetName);
        details.put("targetType", targetType);
        log("PERMSET_ASSIGNED", "CONFIG", targetType, targetId, targetName, details);
    }

    public void logUserProvisioned(String userId, String email, String provider) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", provider);
        log("USER_PROVISIONED_JIT", "DATA", "USER", userId, email, details);
    }

    public void logUserDeactivated(String userId, String email) {
        log("USER_DEACTIVATED", "DATA", "USER", userId, email, null);
    }

    public void logUserActivated(String userId, String email) {
        log("USER_ACTIVATED", "DATA", "USER", userId, email, null);
    }

    public void logBulkExport(String collection, int recordCount, String format) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("collection", collection);
        details.put("recordCount", recordCount);
        details.put("format", format);
        log("BULK_EXPORT", "DATA", "COLLECTION", null, collection, details);
    }

    // Query methods

    @Transactional(readOnly = true)
    public Page<SecurityAuditLog> queryAuditLog(String tenantId, String category,
                                                  String eventType, String actorId,
                                                  Pageable pageable) {
        return repository.findByFilters(tenantId, category, eventType, actorId, pageable);
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditLog> queryAuditLogList(String tenantId, String category,
                                                     String eventType, String actorId) {
        return repository.findAllByFilters(tenantId, category, eventType, actorId);
    }

    @Transactional(readOnly = true)
    public Page<SecurityAuditLog> getAuditLogForTenant(String tenantId, Pageable pageable) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAuditSummary(String tenantId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Instant last24h = Instant.now().minusSeconds(86400);
        summary.put("totalEventsLast24h", repository.countByTenantIdAndCreatedAtAfter(tenantId, last24h));
        summary.put("authEvents", repository.countByTenantIdAndEventCategory(tenantId, "AUTH"));
        summary.put("authzEvents", repository.countByTenantIdAndEventCategory(tenantId, "AUTHZ"));
        summary.put("configEvents", repository.countByTenantIdAndEventCategory(tenantId, "CONFIG"));
        summary.put("dataEvents", repository.countByTenantIdAndEventCategory(tenantId, "DATA"));
        summary.put("permissionDenials", repository.countByTenantIdAndEventType(tenantId, "PERMISSION_DENIED"));
        return summary;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
