package com.emf.controlplane.controller;

import com.emf.controlplane.dto.EffectivePermissionsDto;
import com.emf.controlplane.dto.ObjectPermissionDto;
import com.emf.controlplane.service.PermissionResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Internal API for the gateway to fetch effective permissions for a user.
 * Not exposed to external clients — used for gateway permission caching.
 */
@RestController
@RequestMapping("/internal/permissions")
public class InternalPermissionController {

    private final PermissionResolver permissionResolver;

    public InternalPermissionController(PermissionResolver permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<EffectivePermissionsDto> getEffectivePermissions(@PathVariable String userId) {
        // Resolve all object permissions
        Map<String, PermissionResolver.EffectiveObjectPermission> objPerms =
                permissionResolver.resolveAllObjectPermissions(userId);

        Map<String, ObjectPermissionDto> objPermDtos = new HashMap<>();
        for (var entry : objPerms.entrySet()) {
            ObjectPermissionDto dto = new ObjectPermissionDto();
            dto.setCollectionId(entry.getKey());
            dto.setCanCreate(entry.getValue().canCreate());
            dto.setCanRead(entry.getValue().canRead());
            dto.setCanEdit(entry.getValue().canEdit());
            dto.setCanDelete(entry.getValue().canDelete());
            dto.setCanViewAll(entry.getValue().canViewAll());
            dto.setCanModifyAll(entry.getValue().canModifyAll());
            objPermDtos.put(entry.getKey(), dto);
        }

        // Resolve all system permissions
        Set<String> sysPerms = permissionResolver.getEffectiveSystemPermissions(userId);

        EffectivePermissionsDto dto = new EffectivePermissionsDto(
                userId, objPermDtos, Map.of(), sysPerms);

        return ResponseEntity.ok(dto);
    }
}
