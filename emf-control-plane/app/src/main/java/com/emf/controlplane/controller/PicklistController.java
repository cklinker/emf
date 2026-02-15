package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.GlobalPicklist;
import com.emf.controlplane.entity.PicklistDependency;
import com.emf.controlplane.entity.PicklistValue;
import com.emf.controlplane.service.PicklistService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/control/picklists")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Picklists", description = "Picklist management APIs")
public class PicklistController {

    private static final Logger log = LoggerFactory.getLogger(PicklistController.class);

    private final PicklistService picklistService;

    public PicklistController(PicklistService picklistService) {
        this.picklistService = picklistService;
    }

    // --- Global Picklists ---

    @GetMapping("/global")
    @Operation(summary = "List global picklists")
    public ResponseEntity<List<GlobalPicklistDto>> listGlobalPicklists() {
        String tenantId = TenantContextHolder.requireTenantId();
        List<GlobalPicklist> picklists = picklistService.listGlobalPicklists(tenantId);
        List<GlobalPicklistDto> dtos = picklists.stream()
                .map(GlobalPicklistDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/global")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Create global picklist")
    public ResponseEntity<GlobalPicklistDto> createGlobalPicklist(
            @Valid @RequestBody CreateGlobalPicklistRequest request) {
        log.info("REST request to create global picklist: {}", request.getName());
        String tenantId = TenantContextHolder.requireTenantId();
        GlobalPicklist created = picklistService.createGlobalPicklist(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GlobalPicklistDto.fromEntity(created));
    }

    @GetMapping("/global/{id}")
    @Operation(summary = "Get global picklist")
    public ResponseEntity<GlobalPicklistDto> getGlobalPicklist(@PathVariable String id) {
        GlobalPicklist picklist = picklistService.getGlobalPicklist(id);
        return ResponseEntity.ok(GlobalPicklistDto.fromEntity(picklist));
    }

    @PutMapping("/global/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Update global picklist")
    public ResponseEntity<GlobalPicklistDto> updateGlobalPicklist(
            @PathVariable String id,
            @Valid @RequestBody UpdateGlobalPicklistRequest request) {
        log.info("REST request to update global picklist: {}", id);
        GlobalPicklist updated = picklistService.updateGlobalPicklist(id, request);
        return ResponseEntity.ok(GlobalPicklistDto.fromEntity(updated));
    }

    @DeleteMapping("/global/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Delete global picklist")
    public ResponseEntity<Void> deleteGlobalPicklist(@PathVariable String id) {
        log.info("REST request to delete global picklist: {}", id);
        picklistService.deleteGlobalPicklist(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/global/{id}/values")
    @Operation(summary = "Get global picklist values")
    public ResponseEntity<List<PicklistValueDto>> getGlobalPicklistValues(@PathVariable String id) {
        List<PicklistValue> values = picklistService.getGlobalPicklistValues(id);
        List<PicklistValueDto> dtos = values.stream()
                .map(PicklistValueDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/global/{id}/values")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Set global picklist values")
    public ResponseEntity<List<PicklistValueDto>> setGlobalPicklistValues(
            @PathVariable String id,
            @Valid @RequestBody List<PicklistValueRequest> values) {
        log.info("REST request to set values for global picklist: {}", id);
        List<PicklistValue> saved = picklistService.setGlobalPicklistValues(id, values);
        List<PicklistValueDto> dtos = saved.stream()
                .map(PicklistValueDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // --- Field Picklist Values ---

    @GetMapping("/fields/{fieldId}/values")
    @Operation(summary = "Get field picklist values")
    public ResponseEntity<List<PicklistValueDto>> getFieldPicklistValues(@PathVariable String fieldId) {
        List<PicklistValue> values = picklistService.getFieldPicklistValues(fieldId);
        List<PicklistValueDto> dtos = values.stream()
                .map(PicklistValueDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/fields/{fieldId}/values")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Set field picklist values")
    public ResponseEntity<List<PicklistValueDto>> setFieldPicklistValues(
            @PathVariable String fieldId,
            @Valid @RequestBody List<PicklistValueRequest> values) {
        log.info("REST request to set values for field picklist: {}", fieldId);
        List<PicklistValue> saved = picklistService.setFieldPicklistValues(fieldId, values);
        List<PicklistValueDto> dtos = saved.stream()
                .map(PicklistValueDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // --- Dependencies ---

    @GetMapping("/fields/{fieldId}/dependencies")
    @Operation(summary = "Get picklist dependencies for a field")
    public ResponseEntity<List<PicklistDependencyDto>> getDependencies(@PathVariable String fieldId) {
        List<PicklistDependency> deps = picklistService.getDependencies(fieldId);
        List<PicklistDependencyDto> dtos = deps.stream()
                .map(PicklistDependencyDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/dependencies")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Set picklist dependency")
    public ResponseEntity<PicklistDependencyDto> setDependency(
            @Valid @RequestBody SetDependencyRequest request) {
        log.info("REST request to set dependency: {} -> {}",
                request.getControllingFieldId(), request.getDependentFieldId());
        PicklistDependency dep = picklistService.setDependency(
                request.getControllingFieldId(),
                request.getDependentFieldId(),
                request.getMapping());
        return ResponseEntity.ok(PicklistDependencyDto.fromEntity(dep));
    }

    @DeleteMapping("/dependencies/{controllingFieldId}/{dependentFieldId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    @Operation(summary = "Remove picklist dependency")
    public ResponseEntity<Void> removeDependency(
            @PathVariable String controllingFieldId,
            @PathVariable String dependentFieldId) {
        log.info("REST request to remove dependency: {} -> {}", controllingFieldId, dependentFieldId);
        picklistService.removeDependency(controllingFieldId, dependentFieldId);
        return ResponseEntity.noContent().build();
    }
}
