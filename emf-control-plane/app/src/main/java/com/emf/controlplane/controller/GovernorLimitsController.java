package com.emf.controlplane.controller;

import com.emf.controlplane.service.GovernorLimitsService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governor-limits")
public class GovernorLimitsController {

    private final GovernorLimitsService governorLimitsService;

    public GovernorLimitsController(GovernorLimitsService governorLimitsService) {
        this.governorLimitsService = governorLimitsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GovernorLimitsService.GovernorLimitsStatus> getStatus() {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.ok(governorLimitsService.getStatus(tenantId));
    }
}
