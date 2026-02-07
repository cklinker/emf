package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.PermissionSet;
import com.emf.controlplane.service.PermissionSetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for permission set management operations.
 * All endpoints are tenant-scoped via TenantContextHolder.
 */
@RestController
@RequestMapping("/control/permission-sets")
public class PermissionSetController {

    private final PermissionSetService permissionSetService;

    public PermissionSetController(PermissionSetService permissionSetService) {
        this.permissionSetService = permissionSetService;
    }

    @GetMapping
    public ResponseEntity<List<PermissionSetDto>> listPermissionSets() {
        List<PermissionSet> sets = permissionSetService.listPermissionSets();
        List<PermissionSetDto> dtos = sets.stream()
                .map(PermissionSetDto::fromEntitySummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<PermissionSetDto> createPermissionSet(
            @Valid @RequestBody CreatePermissionSetRequest request) {
        PermissionSet ps = permissionSetService.createPermissionSet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PermissionSetDto.fromEntity(ps));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionSetDto> getPermissionSet(@PathVariable String id) {
        PermissionSet ps = permissionSetService.getPermissionSet(id);
        return ResponseEntity.ok(PermissionSetDto.fromEntity(ps));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionSetDto> updatePermissionSet(
            @PathVariable String id,
            @Valid @RequestBody UpdatePermissionSetRequest request) {
        PermissionSet ps = permissionSetService.updatePermissionSet(id, request);
        return ResponseEntity.ok(PermissionSetDto.fromEntitySummary(ps));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePermissionSet(@PathVariable String id) {
        permissionSetService.deletePermissionSet(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/object-permissions/{collectionId}")
    public ResponseEntity<Void> setObjectPermissions(
            @PathVariable String id,
            @PathVariable String collectionId,
            @Valid @RequestBody ObjectPermissionRequest request) {
        permissionSetService.setObjectPermissions(id, collectionId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/field-permissions")
    public ResponseEntity<Void> setFieldPermissions(
            @PathVariable String id,
            @Valid @RequestBody List<FieldPermissionRequest> requests) {
        permissionSetService.setFieldPermissions(id, requests);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/system-permissions")
    public ResponseEntity<Void> setSystemPermissions(
            @PathVariable String id,
            @Valid @RequestBody List<SystemPermissionRequest> requests) {
        permissionSetService.setSystemPermissions(id, requests);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign/{userId}")
    public ResponseEntity<Void> assignToUser(
            @PathVariable String id,
            @PathVariable String userId) {
        permissionSetService.assignToUser(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/assign/{userId}")
    public ResponseEntity<Void> removeFromUser(
            @PathVariable String id,
            @PathVariable String userId) {
        permissionSetService.removeFromUser(id, userId);
        return ResponseEntity.noContent().build();
    }
}
