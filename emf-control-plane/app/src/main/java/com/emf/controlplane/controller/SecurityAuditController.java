package com.emf.controlplane.controller;

import com.emf.controlplane.entity.SecurityAuditLog;
import com.emf.controlplane.service.ExportService;
import com.emf.controlplane.service.SecurityAuditService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final DateTimeFormatter CSV_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private static final List<String> EXPORT_COLUMNS = List.of(
            "timestamp", "eventCategory", "eventType", "actorEmail",
            "targetType", "targetId", "targetName", "ipAddress", "details");

    private final SecurityAuditService auditService;
    private final ExportService exportService;

    public SecurityAuditController(SecurityAuditService auditService,
                                   ExportService exportService) {
        this.auditService = auditService;
        this.exportService = exportService;
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

    @GetMapping("/export")
    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_DATA')")
    @Operation(summary = "Export audit log as CSV", description = "Export filtered audit log entries as a CSV file")
    public ResponseEntity<byte[]> exportAuditLog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId) {
        String tenantId = TenantContextHolder.requireTenantId();
        List<SecurityAuditLog> entries = auditService.queryAuditLogList(tenantId, category, eventType, actorId);

        List<Map<String, Object>> rows = entries.stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", entry.getCreatedAt() != null ? CSV_DATE_FORMAT.format(entry.getCreatedAt()) : "");
            row.put("eventCategory", entry.getEventCategory());
            row.put("eventType", entry.getEventType());
            row.put("actorEmail", entry.getActorEmail() != null ? entry.getActorEmail() : "");
            row.put("targetType", entry.getTargetType() != null ? entry.getTargetType() : "");
            row.put("targetId", entry.getTargetId() != null ? entry.getTargetId() : "");
            row.put("targetName", entry.getTargetName() != null ? entry.getTargetName() : "");
            row.put("ipAddress", entry.getIpAddress() != null ? entry.getIpAddress() : "");
            row.put("details", entry.getDetails() != null ? entry.getDetails() : "");
            return row;
        }).toList();

        byte[] csvData = exportService.exportToCsv(EXPORT_COLUMNS, rows);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"security-audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }
}
