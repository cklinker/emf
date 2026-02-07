package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateTenantRequest;
import com.emf.controlplane.dto.GovernorLimits;
import com.emf.controlplane.dto.TenantDto;
import com.emf.controlplane.dto.UpdateTenantRequest;
import com.emf.controlplane.entity.Tenant;
import com.emf.controlplane.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for platform-level tenant administration.
 * Uses /platform/tenants path, separate from tenant-scoped /control endpoints.
 * Requires PLATFORM_ADMIN role for all operations.
 */
@RestController
@RequestMapping("/platform/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Page<TenantDto>> listTenants(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Tenant> tenants = tenantService.listTenants(pageable);
        Page<TenantDto> tenantDtos = tenants.map(TenantDto::fromEntity);
        return ResponseEntity.ok(tenantDtos);
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(request);
        TenantDto tenantDto = TenantDto.fromEntity(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantDto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDto> getTenant(@PathVariable String id) {
        Tenant tenant = tenantService.getTenant(id);
        TenantDto tenantDto = TenantDto.fromEntity(tenant);
        return ResponseEntity.ok(tenantDto);
    }

    @GetMapping("/by-slug/{slug}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDto> getTenantBySlug(@PathVariable String slug) {
        Tenant tenant = tenantService.getTenantBySlug(slug);
        TenantDto tenantDto = TenantDto.fromEntity(tenant);
        return ResponseEntity.ok(tenantDto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<TenantDto> updateTenant(
            @PathVariable String id,
            @Valid @RequestBody UpdateTenantRequest request) {
        Tenant tenant = tenantService.updateTenant(id, request);
        TenantDto tenantDto = TenantDto.fromEntity(tenant);
        return ResponseEntity.ok(tenantDto);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> suspendTenant(@PathVariable String id) {
        tenantService.suspendTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> activateTenant(@PathVariable String id) {
        tenantService.activateTenant(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/limits")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<GovernorLimits> getLimits(@PathVariable String id) {
        GovernorLimits limits = tenantService.getGovernorLimits(id);
        return ResponseEntity.ok(limits);
    }
}
