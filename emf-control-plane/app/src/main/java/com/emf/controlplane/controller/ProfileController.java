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
 * REST controller for managing profiles (permission bundles assigned to users).
 */
@RestController
@RequestMapping("/control/profiles")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Profiles", description = "Profile management")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_USERS')")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileRepository profileRepository;
    private final ProfileSystemPermissionRepository profileSysPermRepo;
    private final ProfileObjectPermissionRepository profileObjPermRepo;
    private final ProfileFieldPermissionRepository profileFieldPermRepo;
    private final UserRepository userRepository;
    private final SecurityAuditService auditService;
    private final PermissionResolutionService permissionResolutionService;

    public ProfileController(ProfileRepository profileRepository,
                             ProfileSystemPermissionRepository profileSysPermRepo,
                             ProfileObjectPermissionRepository profileObjPermRepo,
                             ProfileFieldPermissionRepository profileFieldPermRepo,
                             UserRepository userRepository,
                             SecurityAuditService auditService,
                             PermissionResolutionService permissionResolutionService) {
        this.profileRepository = profileRepository;
        this.profileSysPermRepo = profileSysPermRepo;
        this.profileObjPermRepo = profileObjPermRepo;
        this.profileFieldPermRepo = profileFieldPermRepo;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.permissionResolutionService = permissionResolutionService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List profiles", description = "List all profiles for the current tenant")
    public ResponseEntity<List<Profile>> listProfiles() {
        String tenantId = TenantContextHolder.requireTenantId();
        return ResponseEntity.ok(profileRepository.findByTenantIdOrderByNameAsc(tenantId));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get profile", description = "Get profile detail with all permissions")
    public ResponseEntity<Profile> getProfile(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        return profileRepository.findByIdAndTenantId(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create profile", description = "Create a new profile")
    public ResponseEntity<Profile> createProfile(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        String name = (String) body.get("name");
        String description = (String) body.get("description");

        if (profileRepository.existsByTenantIdAndName(tenantId, name)) {
            return ResponseEntity.badRequest().build();
        }

        Profile profile = new Profile(tenantId, name, description, false);
        profile = profileRepository.save(profile);
        auditService.logProfileCreated(profile.getId(), name);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Update profile", description = "Update profile (not system profiles)")
    public ResponseEntity<Profile> updateProfile(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<Profile> opt = profileRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Profile profile = opt.get();
        if (body.containsKey("name")) profile.setName((String) body.get("name"));
        if (body.containsKey("description")) profile.setDescription((String) body.get("description"));
        profile = profileRepository.save(profile);
        auditService.logProfileUpdated(profile.getId(), profile.getName(), null);
        return ResponseEntity.ok(profile);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Delete profile", description = "Delete a non-system profile")
    public ResponseEntity<Void> deleteProfile(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<Profile> opt = profileRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Profile profile = opt.get();
        if (profile.isSystem()) {
            return ResponseEntity.badRequest().build();
        }

        String name = profile.getName();
        profileRepository.delete(profile);
        auditService.logProfileDeleted(id, name);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clone")
    @Transactional
    @Operation(summary = "Clone profile", description = "Clone a profile (including system profiles)")
    public ResponseEntity<Profile> cloneProfile(@PathVariable String id, @RequestBody Map<String, String> body) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<Profile> opt = profileRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Profile source = opt.get();
        String newName = body.get("name");
        if (newName == null || newName.isBlank()) {
            newName = source.getName() + " (Copy)";
        }
        if (profileRepository.existsByTenantIdAndName(tenantId, newName)) {
            return ResponseEntity.badRequest().build();
        }

        Profile clone = new Profile(tenantId, newName, source.getDescription(), false);
        clone = profileRepository.save(clone);

        // Clone system permissions
        List<ProfileSystemPermission> sysPerms = profileSysPermRepo.findByProfileId(source.getId());
        for (ProfileSystemPermission sp : sysPerms) {
            profileSysPermRepo.save(new ProfileSystemPermission(clone.getId(), sp.getPermissionName(), sp.isGranted()));
        }

        // Clone object permissions
        List<ProfileObjectPermission> objPerms = profileObjPermRepo.findByProfileId(source.getId());
        for (ProfileObjectPermission op : objPerms) {
            profileObjPermRepo.save(new ProfileObjectPermission(clone.getId(), op.getCollectionId(),
                    op.isCanCreate(), op.isCanRead(), op.isCanEdit(),
                    op.isCanDelete(), op.isCanViewAll(), op.isCanModifyAll()));
        }

        // Clone field permissions
        List<ProfileFieldPermission> fieldPerms = profileFieldPermRepo.findByProfileId(source.getId());
        for (ProfileFieldPermission fp : fieldPerms) {
            profileFieldPermRepo.save(new ProfileFieldPermission(clone.getId(), fp.getCollectionId(),
                    fp.getFieldId(), fp.getVisibility()));
        }

        auditService.logProfileCreated(clone.getId(), newName);
        return ResponseEntity.ok(clone);
    }

    @GetMapping("/{id}/system-permissions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get system permissions", description = "Get system permissions for a profile")
    public ResponseEntity<List<ProfileSystemPermission>> getSystemPermissions(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (profileRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileSysPermRepo.findByProfileId(id));
    }

    @PutMapping("/{id}/system-permissions")
    @Transactional
    @Operation(summary = "Set system permissions", description = "Batch set system permissions for a profile")
    public ResponseEntity<Void> setSystemPermissions(@PathVariable String id,
                                                      @RequestBody Map<String, Boolean> permissions) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<Profile> opt = profileRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        // Delete existing and recreate
        profileSysPermRepo.deleteByProfileId(id);
        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            profileSysPermRepo.save(new ProfileSystemPermission(id, entry.getKey(), entry.getValue()));
        }

        auditService.logProfileUpdated(id, opt.get().getName(), null);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/object-permissions")
    @Transactional(readOnly = true)
    @Operation(summary = "Get object permissions", description = "Get object permissions for a profile")
    public ResponseEntity<List<ProfileObjectPermission>> getObjectPermissions(@PathVariable String id) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (profileRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileObjPermRepo.findByProfileId(id));
    }

    @PutMapping("/{id}/object-permissions")
    @Transactional
    @Operation(summary = "Set object permissions", description = "Batch set object permissions for a profile")
    public ResponseEntity<Void> setObjectPermissions(@PathVariable String id,
                                                      @RequestBody List<Map<String, Object>> permissions) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<Profile> opt = profileRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        profileObjPermRepo.deleteByProfileId(id);
        for (Map<String, Object> perm : permissions) {
            ProfileObjectPermission pop = new ProfileObjectPermission(
                    id,
                    (String) perm.get("collectionId"),
                    Boolean.TRUE.equals(perm.get("canCreate")),
                    Boolean.TRUE.equals(perm.get("canRead")),
                    Boolean.TRUE.equals(perm.get("canEdit")),
                    Boolean.TRUE.equals(perm.get("canDelete")),
                    Boolean.TRUE.equals(perm.get("canViewAll")),
                    Boolean.TRUE.equals(perm.get("canModifyAll"))
            );
            profileObjPermRepo.save(pop);
        }

        auditService.logProfileUpdated(id, opt.get().getName(), null);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/field-permissions/{collectionId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get field permissions", description = "Get field permissions for a profile and collection")
    public ResponseEntity<List<ProfileFieldPermission>> getFieldPermissions(
            @PathVariable String id, @PathVariable String collectionId) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (profileRepository.findByIdAndTenantId(id, tenantId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileFieldPermRepo.findByProfileIdAndCollectionId(id, collectionId));
    }

    @PutMapping("/{id}/field-permissions/{collectionId}")
    @Transactional
    @Operation(summary = "Set field permissions", description = "Batch set field permissions for a profile and collection")
    public ResponseEntity<Void> setFieldPermissions(
            @PathVariable String id, @PathVariable String collectionId,
            @RequestBody List<Map<String, String>> permissions) {
        String tenantId = TenantContextHolder.requireTenantId();
        Optional<Profile> opt = profileRepository.findByIdAndTenantId(id, tenantId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        profileFieldPermRepo.deleteByProfileIdAndCollectionId(id, collectionId);
        for (Map<String, String> perm : permissions) {
            ProfileFieldPermission pfp = new ProfileFieldPermission(
                    id, collectionId, perm.get("fieldId"),
                    FieldVisibility.valueOf(perm.get("visibility"))
            );
            profileFieldPermRepo.save(pfp);
        }

        auditService.logProfileUpdated(id, opt.get().getName(), null);
        permissionResolutionService.evictPermissionsCache();
        return ResponseEntity.ok().build();
    }
}
