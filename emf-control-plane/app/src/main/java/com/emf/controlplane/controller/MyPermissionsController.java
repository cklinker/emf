package com.emf.controlplane.controller;

import com.emf.controlplane.dto.ObjectPermissions;
import com.emf.controlplane.dto.ResolvedPermissions;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.FieldVisibility;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.repository.UserRepository;
import com.emf.controlplane.service.CollectionService;
import com.emf.controlplane.service.PermissionResolutionService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for fetching the current user's effective permissions.
 *
 * <p>Returns object-level CRUD permissions, field-level visibility, and
 * system-level permissions for the authenticated user. Delegates to
 * {@link PermissionResolutionService} for real permission evaluation.
 *
 * <p>PLATFORM_ADMIN users receive fully-permissive responses.
 */
@RestController
@RequestMapping("/control/my-permissions")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "My Permissions", description = "Current user effective permissions")
public class MyPermissionsController {

    private static final Logger log = LoggerFactory.getLogger(MyPermissionsController.class);

    private final PermissionResolutionService permissionService;
    private final UserRepository userRepository;
    private final CollectionService collectionService;

    public MyPermissionsController(PermissionResolutionService permissionService,
                                   UserRepository userRepository,
                                   CollectionService collectionService) {
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.collectionService = collectionService;
    }

    @GetMapping("/system")
    @Operation(
            summary = "Get system permissions",
            description = "Returns all system permission grants for the current user"
    )
    @ApiResponse(responseCode = "200", description = "System permissions returned")
    public ResponseEntity<Map<String, Boolean>> getSystemPermissions() {
        if (isPlatformAdmin()) {
            Map<String, Boolean> all = new LinkedHashMap<>();
            for (com.emf.controlplane.entity.SystemPermission p : com.emf.controlplane.entity.SystemPermission.values()) {
                all.put(p.name(), true);
            }
            return ResponseEntity.ok(all);
        }

        String tenantId = TenantContextHolder.getTenantId();
        String userId = resolveUserId(tenantId);
        if (userId == null) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        ResolvedPermissions resolved = permissionService.resolveForUser(tenantId, userId);
        return ResponseEntity.ok(resolved.systemPermissions());
    }

    @GetMapping("/objects/{collectionName}")
    @Operation(
            summary = "Get object permissions",
            description = "Returns effective object-level CRUD permissions for the current user on a collection"
    )
    @ApiResponse(responseCode = "200", description = "Permission flags returned")
    public ResponseEntity<Map<String, Boolean>> getObjectPermissions(
            @Parameter(description = "Collection API name") @PathVariable String collectionName) {
        log.debug("Fetching object permissions for collection: {}", collectionName);

        if (isPlatformAdmin()) {
            return ResponseEntity.ok(allPermissiveObjectPermissions());
        }

        String tenantId = TenantContextHolder.getTenantId();
        String userId = resolveUserId(tenantId);
        if (userId == null) {
            return ResponseEntity.ok(noObjectPermissions());
        }

        try {
            Collection collection = collectionService.getCollectionByIdOrName(collectionName);
            ObjectPermissions perms = permissionService.getObjectPermissions(tenantId, userId, collection.getId());
            Map<String, Boolean> result = new LinkedHashMap<>();
            result.put("canCreate", perms.canCreate());
            result.put("canRead", perms.canRead());
            result.put("canEdit", perms.canEdit());
            result.put("canDelete", perms.canDelete());
            result.put("canViewAll", perms.canViewAll());
            result.put("canModifyAll", perms.canModifyAll());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.debug("Collection not found for permissions: {}", collectionName);
            return ResponseEntity.ok(noObjectPermissions());
        }
    }

    @GetMapping("/fields/{collectionName}")
    @Operation(
            summary = "Get field permissions",
            description = "Returns effective field-level visibility for the current user on a collection"
    )
    @ApiResponse(responseCode = "200", description = "Field permissions returned")
    public ResponseEntity<Map<String, String>> getFieldPermissions(
            @Parameter(description = "Collection API name") @PathVariable String collectionName) {
        log.debug("Fetching field permissions for collection: {}", collectionName);

        if (isPlatformAdmin()) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        String tenantId = TenantContextHolder.getTenantId();
        String userId = resolveUserId(tenantId);
        if (userId == null) {
            return ResponseEntity.ok(Collections.emptyMap());
        }

        try {
            Collection collection = collectionService.getCollectionByIdOrName(collectionName);
            Map<String, FieldVisibility> fieldPerms =
                    permissionService.getFieldPermissions(tenantId, userId, collection.getId());
            Map<String, String> result = new LinkedHashMap<>();
            fieldPerms.forEach((fieldId, visibility) -> result.put(fieldId, visibility.name()));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.debug("Collection not found for field permissions: {}", collectionName);
            return ResponseEntity.ok(Collections.emptyMap());
        }
    }

    @GetMapping("/effective")
    @Operation(
            summary = "Get all effective permissions",
            description = "Returns the full resolved permission summary for the current user"
    )
    @ApiResponse(responseCode = "200", description = "Effective permissions returned")
    public ResponseEntity<Map<String, Object>> getEffectivePermissions() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (isPlatformAdmin()) {
            Map<String, Boolean> sysPerms = new LinkedHashMap<>();
            for (com.emf.controlplane.entity.SystemPermission p : com.emf.controlplane.entity.SystemPermission.values()) {
                sysPerms.put(p.name(), true);
            }
            result.put("systemPermissions", sysPerms);
            result.put("isPlatformAdmin", true);
            return ResponseEntity.ok(result);
        }

        String tenantId = TenantContextHolder.getTenantId();
        String userId = resolveUserId(tenantId);
        if (userId == null) {
            result.put("systemPermissions", Collections.emptyMap());
            result.put("isPlatformAdmin", false);
            return ResponseEntity.ok(result);
        }

        ResolvedPermissions resolved = permissionService.resolveForUser(tenantId, userId);
        result.put("systemPermissions", resolved.systemPermissions());
        result.put("isPlatformAdmin", false);
        return ResponseEntity.ok(result);
    }

    private boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_PLATFORM_ADMIN"));
    }

    private String resolveUserId(String tenantId) {
        if (tenantId == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String email = auth.getName();
        return userRepository.findByTenantIdAndEmail(tenantId, email)
                .map(User::getId)
                .orElse(null);
    }

    private Map<String, Boolean> allPermissiveObjectPermissions() {
        Map<String, Boolean> perms = new LinkedHashMap<>();
        perms.put("canCreate", true);
        perms.put("canRead", true);
        perms.put("canEdit", true);
        perms.put("canDelete", true);
        perms.put("canViewAll", true);
        perms.put("canModifyAll", true);
        return perms;
    }

    private Map<String, Boolean> noObjectPermissions() {
        Map<String, Boolean> perms = new LinkedHashMap<>();
        perms.put("canCreate", false);
        perms.put("canRead", false);
        perms.put("canEdit", false);
        perms.put("canDelete", false);
        perms.put("canViewAll", false);
        perms.put("canModifyAll", false);
        return perms;
    }
}
