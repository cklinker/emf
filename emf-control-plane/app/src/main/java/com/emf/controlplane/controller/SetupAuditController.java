package com.emf.controlplane.controller;

import com.emf.controlplane.dto.SetupAuditTrailDto;
import com.emf.controlplane.service.SetupAuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/control/audit")
public class SetupAuditController {

    private final SetupAuditService auditService;

    public SetupAuditController(SetupAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("@securityService.hasPermission(#root, 'VIEW_SETUP')")
    public ResponseEntity<Page<SetupAuditTrailDto>> getAuditTrail(
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                auditService.getAuditTrail(section, entityType, userId, from, to, pageable));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("@securityService.hasPermission(#root, 'VIEW_SETUP')")
    public ResponseEntity<Page<SetupAuditTrailDto>> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditService.getEntityHistory(entityType, entityId, pageable));
    }
}
