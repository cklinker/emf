package com.emf.controlplane.controller;

import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.*;
import com.emf.controlplane.service.PermissionResolutionService;
import com.emf.controlplane.service.SecurityAuditService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for managing permission sets.
 * Permission sets are additive permission bundles that can be assigned to users or groups.
 */
@RestController
@RequestMapping("/control/permission-sets")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Permission Sets", description = "Permission set management")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_USERS')")
public class PermissionSetController {

    private static final Logger log = LoggerFactory.getLogger(PermissionSetController.class);

    private final PermissionSetRepository permissionSetRepository;
    private final PermsetSystemPermissionRepository permsetSysPermRepo;
    private final PermsetObjectPermissionRepository permsetObjPermRepo;
    private final PermsetFieldPermissionRepository permsetFieldPermRepo;
    private final UserPermissionSetRepository userPermSetRepo;
    private final GroupPermissionSetRepository groupPermSetRepo;
    private final SecurityAuditService auditService;
    private final PermissionResolutionService permissionResolutionService;

    public PermissionSetController(PermissionSetRepository permissionSetRepository,
                                    PermsetSystemPermissionRepository permsetSysPermRepo,
                                    PermsetObjectPermissionRepository permsetObjPermRepo,
                                    PermsetFieldPermissionRepository permsetFieldPermRepo,
                                    UserPermissionSetRepository userPermSetRepo,
                                    GroupPermissionSetRepository groupPermSetRepo,
                                    SecurityAuditService auditService,
                                    PermissionResolutionService permissionResolutionService) {
        this.permissionSetRepository = permissionSetRepository;
        this.permsetSysPermRepo = permsetSysPermRepo;
        this.permsetObjPermRepo = permsetObjPermRepo;
        this.permsetFieldPermRepo = permsetFieldPermRepo;
        this.userPermSetRepo = userPermSetRepo;
        this.groupPermSetRepo = groupPermSetRepo;
        this.auditService = auditService;
        this.permissionResolutionService = permissionResolutionService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List permission sets", description = "List all permission sets for the current tenant")
    public ResponseEntity<List<PermissionSet>> listPermissionSets() {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.ok(permissionSetRepository.findByTenantIdOrderByNameAsc(tenantId));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get permission set", description = "Get permission set detail")
    public ResponseEntity<PermissionSet> getPermissionSet(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        return permissionSetRepository.findByIdAndTenantId(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create permission set", description = "Create a new permission set")
    public ResponseEntity<PermissionSet> createPermissionSet(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        String name = (String) body.get("name");
        String description = (String) body.get("description");

        if (permissionSetRepository.existsByTenantIdAndName(tenantId, name)) {
            return ResponseEntity.badRequest().build();
        }

        PermissionSet permSet = new PermissionSet(tenantId, name, description, false);
        permSet = permissionSetRepository.save(permSet);
        auditService.logPermsetCreated(permSet.getId(), name);
        return ResponseEntity.ok(permSet);
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Update permission set", description = "Update a permission set")
    public ResponseEntity<PermissionSet> updatePermissionSet(@PathVariable String id,
                                                              @RequestBody Map<String, Object> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PermissionSet permSet = opt.get();
        if (body.containsKey("name")) permSet.setName((String) body.get("name"));
        if (body.containsKey("description")) permSet.setDescription((String) body.get("description"));
        permSet = permissionSetRepository.save(permSet);
        auditService.logPermsetUpdated(permSet.getId(), permSet.getName());
        return ResponseEntity.ok(permSet);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Delete permission set", description = "Delete a permission set")
    public ResponseEntity<Void> deletePermissionSet(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PermissionSet permSet = opt.get();
        if (permSet.isSystem()) {
            return ResponseEntity.badRequest().build();
        }

        String name = permSet.getName();
        permissionSetRepository.delete(permSet);
        auditService.logPermsetDeleted(id, name);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/assignments")
    @Transactional(readOnly = true)
    @Operation(summary = "List assignments", description = "List users and groups assigned to this permission set")
    public ResponseEntity<Map<String, Object>> getAssignments(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (permissionSetRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", userPermSetRepo.findByPermissionSetId(id));
        result.put("groups", groupPermSetRepo.findByPermissionSetId(id));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/assignments/users")
    @Transactional
    @Operation(summary = "Assign to users", description = "Assign the permission set to users")
    public ResponseEntity<Void> assignToUsers(@PathVariable String id,
                                               @RequestBody Map<String, List<String>> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        List<String> userIds = body.get("userIds");
        if (userIds != null) {
            for (String userId : userIds) {
                if (!userPermSetRepo.existsByUserIdAndPermissionSetId(userId, id)) {
                    userPermSetRepo.save(new UserPermissionSet(userId, id));
                    auditService.logPermsetAssigned(opt.get().getName(), "USER", userId, null);
                }
            }
        }
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/assignments/users/{userId}")
    @Transactional
    @Operation(summary = "Unassign from user", description = "Remove the permission set from a user")
    public ResponseEntity<Void> unassignFromUser(@PathVariable String id, @PathVariable String userId) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (permissionSetRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        userPermSetRepo.deleteByUserIdAndPermissionSetId(userId, id);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assignments/groups")
    @Transactional
    @Operation(summary = "Assign to groups", description = "Assign the permission set to groups")
    public ResponseEntity<Void> assignToGroups(@PathVariable String id,
                                                @RequestBody Map<String, List<String>> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        List<String> groupIds = body.get("groupIds");
        if (groupIds != null) {
            for (String groupId : groupIds) {
                if (!groupPermSetRepo.existsByGroupIdAndPermissionSetId(groupId, id)) {
                    groupPermSetRepo.save(new GroupPermissionSet(groupId, id));
                    auditService.logPermsetAssigned(opt.get().getName(), "GROUP", groupId, null);
                }
            }
        }
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/assignments/groups/{groupId}")
    @Transactional
    @Operation(summary = "Unassign from group", description = "Remove the permission set from a group")
    public ResponseEntity<Void> unassignFromGroup(@PathVariable String id, @PathVariable String groupId) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (permissionSetRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        groupPermSetRepo.deleteByGroupIdAndPermissionSetId(groupId, id);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.noContent().build();
    }

    // ── System Permissions ──────────────────────────────────────────────

    @GetMapping("/{id}/system-permissions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get system permissions", description = "Get system permissions for a permission set")
    public ResponseEntity<List<PermsetSystemPermission>> getSystemPermissions(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (permissionSetRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(permsetSysPermRepo.findByPermissionSetId(id));
    }

    @PutMapping("/{id}/system-permissions")
    @Transactional
    @Operation(summary = "Set system permissions", description = "Batch set system permissions for a permission set")
    public ResponseEntity<Void> setSystemPermissions(@PathVariable String id,
                                                      @RequestBody Map<String, Boolean> permissions) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        permsetSysPermRepo.deleteByPermissionSetId(id);
        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            permsetSysPermRepo.save(new PermsetSystemPermission(id, entry.getKey(), entry.getValue()));
        }

        auditService.logPermsetUpdated(id, opt.get().getName());
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    // ── Object Permissions ──────────────────────────────────────────────

    @GetMapping("/{id}/object-permissions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get object permissions", description = "Get object permissions for a permission set")
    public ResponseEntity<List<PermsetObjectPermission>> getObjectPermissions(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (permissionSetRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(permsetObjPermRepo.findByPermissionSetId(id));
    }

    @PutMapping("/{id}/object-permissions")
    @Transactional
    @Operation(summary = "Set object permissions", description = "Batch set object permissions for a permission set")
    public ResponseEntity<Void> setObjectPermissions(@PathVariable String id,
                                                      @RequestBody List<Map<String, Object>> permissions) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        permsetObjPermRepo.deleteByPermissionSetId(id);
        for (Map<String, Object> perm : permissions) {
            PermsetObjectPermission pop = new PermsetObjectPermission();
            pop.setPermissionSetId(id);
            pop.setCollectionId((String) perm.get("collectionId"));
            pop.setCanCreate(Boolean.TRUE.equals(perm.get("canCreate")));
            pop.setCanRead(Boolean.TRUE.equals(perm.get("canRead")));
            pop.setCanEdit(Boolean.TRUE.equals(perm.get("canEdit")));
            pop.setCanDelete(Boolean.TRUE.equals(perm.get("canDelete")));
            pop.setCanViewAll(Boolean.TRUE.equals(perm.get("canViewAll")));
            pop.setCanModifyAll(Boolean.TRUE.equals(perm.get("canModifyAll")));
            permsetObjPermRepo.save(pop);
        }

        auditService.logPermsetUpdated(id, opt.get().getName());
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    // ── Field Permissions ───────────────────────────────────────────────

    @GetMapping("/{id}/field-permissions/{collectionId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get field permissions", description = "Get field permissions for a permission set and collection")
    public ResponseEntity<List<PermsetFieldPermission>> getFieldPermissions(
            @PathVariable String id, @PathVariable String collectionId) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (permissionSetRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(permsetFieldPermRepo.findByPermissionSetIdAndCollectionId(id, collectionId));
    }

    @PutMapping("/{id}/field-permissions/{collectionId}")
    @Transactional
    @Operation(summary = "Set field permissions", description = "Batch set field permissions for a permission set and collection")
    public ResponseEntity<Void> setFieldPermissions(
            @PathVariable String id, @PathVariable String collectionId,
            @RequestBody List<Map<String, String>> permissions) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        permsetFieldPermRepo.deleteByPermissionSetIdAndCollectionId(id, collectionId);
        for (Map<String, String> perm : permissions) {
            PermsetFieldPermission pfp = new PermsetFieldPermission();
            pfp.setPermissionSetId(id);
            pfp.setCollectionId(collectionId);
            pfp.setFieldId(perm.get("fieldId"));
            pfp.setVisibility(FieldVisibility.valueOf(perm.get("visibility")));
            permsetFieldPermRepo.save(pfp);
        }

        auditService.logPermsetUpdated(id, opt.get().getName());
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    // ── Clone ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/clone")
    @Transactional
    @Operation(summary = "Clone permission set", description = "Clone a permission set with all its permissions")
    public ResponseEntity<PermissionSet> clonePermissionSet(@PathVariable String id,
                                                             @RequestBody Map<String, String> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<PermissionSet> opt = permissionSetRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PermissionSet source = opt.get();
        String newName = body.get("name");
        if (newName == null || newName.isBlank()) {
            newName = source.getName() + " (Copy)";
        }
        if (permissionSetRepository.existsByTenantIdAndName(tenantId, newName)) {
            return ResponseEntity.badRequest().build();
        }

        PermissionSet clone = new PermissionSet(tenantId, newName, source.getDescription(), false);
        clone = permissionSetRepository.save(clone);

        // Clone system permissions
        for (PermsetSystemPermission sp : permsetSysPermRepo.findByPermissionSetId(source.getId())) {
            permsetSysPermRepo.save(new PermsetSystemPermission(clone.getId(), sp.getPermissionName(), sp.isGranted()));
        }

        // Clone object permissions
        for (PermsetObjectPermission op : permsetObjPermRepo.findByPermissionSetId(source.getId())) {
            PermsetObjectPermission clonedOp = new PermsetObjectPermission();
            clonedOp.setPermissionSetId(clone.getId());
            clonedOp.setCollectionId(op.getCollectionId());
            clonedOp.setCanCreate(op.isCanCreate());
            clonedOp.setCanRead(op.isCanRead());
            clonedOp.setCanEdit(op.isCanEdit());
            clonedOp.setCanDelete(op.isCanDelete());
            clonedOp.setCanViewAll(op.isCanViewAll());
            clonedOp.setCanModifyAll(op.isCanModifyAll());
            permsetObjPermRepo.save(clonedOp);
        }

        // Clone field permissions
        for (PermsetFieldPermission fp : permsetFieldPermRepo.findByPermissionSetId(source.getId())) {
            PermsetFieldPermission clonedFp = new PermsetFieldPermission();
            clonedFp.setPermissionSetId(clone.getId());
            clonedFp.setCollectionId(fp.getCollectionId());
            clonedFp.setFieldId(fp.getFieldId());
            clonedFp.setVisibility(fp.getVisibility());
            permsetFieldPermRepo.save(clonedFp);
        }

        auditService.logPermsetCreated(clone.getId(), newName);
        return ResponseEntity.ok(clone);
    }
}
