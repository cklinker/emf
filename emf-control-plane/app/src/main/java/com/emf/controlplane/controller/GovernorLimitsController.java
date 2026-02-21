package com.emf.controlplane.controller;

import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.service.GovernorLimitsService;
import com.emf.controlplane.service.TenantService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/control/governor-limits")
public class GovernorLimitsController {

    private final GovernorLimitsService governorLimitsService;
    private final TenantService tenantService;

    public GovernorLimitsController(GovernorLimitsService governorLimitsService, TenantService tenantService) {
        this.governorLimitsService = governorLimitsService;
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("@securityService.hasPermission(#root, 'VIEW_SETUP')")
    public ResponseEntity<GovernorLimitsService.GovernorLimitsStatus> getStatus() {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.ok(governorLimitsService.getStatus(tenantId));
    }

    @PutMapping
    @PreAuthorize("@securityService.isPlatformAdmin(#root.authentication)")
    public ResponseEntity<GovernorLimits> updateLimits(@RequestBody GovernorLimits limits) {
        String tenantId = TenantContextHolder.requireTenantId();
        GovernorLimits updated = tenantService.updateGovernorLimits(tenantId, limits);
        return ResponseEntity.ok(updated);
    }
}
