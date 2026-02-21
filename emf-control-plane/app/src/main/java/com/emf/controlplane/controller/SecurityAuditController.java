package com.emf.controlplane.controller;

import com.emf.controlplane.entity.SecurityAuditLog;
import com.emf.controlplane.service.SecurityAuditService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for querying the security audit trail.
 */
@RestController
@RequestMapping("/control/security-audit")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Security Audit", description = "Security audit trail")
@PreAuthorize("@securityService.hasPermission(#root, 'VIEW_SETUP')")
public class SecurityAuditController {

    private final SecurityAuditService auditService;

    public SecurityAuditController(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Query audit log", description = "Query security audit log with optional filters")
    public ResponseEntity<Page<SecurityAuditLog>> queryAuditLog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId,
            Pageable pageable) {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.ok(auditService.queryAuditLog(tenantId, category, eventType, actorId, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get audit summary", description = "Get aggregated security audit stats")
    public ResponseEntity<Map<String, Object>> getAuditSummary() {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.ok(auditService.getAuditSummary(tenantId));
    }
}
