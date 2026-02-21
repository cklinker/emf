package com.emf.controlplane.service;

import com.emf.controlplane.config.CacheConfig;
import com.emf.controlplane.config.ControlPlaneProperties;
import com.emf.controlplane.dto.ObjectPermissions;
import com.emf.controlplane.dto.ResolvedPermissions;
import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves the effective permissions for a user by combining:
 * 1. Profile permissions (base)
 * 2. Direct permission set assignments (additive)
 * 3. Group-inherited permission set assignments (additive)
 *
 * Most permissive grant wins for each permission.
 */
@Service
public class PermissionResolutionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolutionService.class);

    private final ProfileRepository profileRepository;
    private final ProfileSystemPermissionRepository profileSysPermRepo;
    private final ProfileObjectPermissionRepository profileObjPermRepo;
    private final ProfileFieldPermissionRepository profileFieldPermRepo;
    private final PermissionSetRepository permissionSetRepository;
    private final PermsetSystemPermissionRepository permsetSysPermRepo;
    private final PermsetObjectPermissionRepository permsetObjPermRepo;
    private final PermsetFieldPermissionRepository permsetFieldPermRepo;
    private final UserPermissionSetRepository userPermSetRepo;
    private final GroupPermissionSetRepository groupPermSetRepo;
    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final ControlPlaneProperties properties;

    public PermissionResolutionService(
            ProfileRepository profileRepository,
            ProfileSystemPermissionRepository profileSysPermRepo,
            ProfileObjectPermissionRepository profileObjPermRepo,
            ProfileFieldPermissionRepository profileFieldPermRepo,
            PermissionSetRepository permissionSetRepository,
            PermsetSystemPermissionRepository permsetSysPermRepo,
            PermsetObjectPermissionRepository permsetObjPermRepo,
            PermsetFieldPermissionRepository permsetFieldPermRepo,
            UserPermissionSetRepository userPermSetRepo,
            GroupPermissionSetRepository groupPermSetRepo,
            UserGroupRepository userGroupRepository,
            UserRepository userRepository,
            ControlPlaneProperties properties) {
        this.profileRepository = profileRepository;
        this.profileSysPermRepo = profileSysPermRepo;
        this.profileObjPermRepo = profileObjPermRepo;
        this.profileFieldPermRepo = profileFieldPermRepo;
        this.permissionSetRepository = permissionSetRepository;
        this.permsetSysPermRepo = permsetSysPermRepo;
        this.permsetObjPermRepo = permsetObjPermRepo;
        this.permsetFieldPermRepo = permsetFieldPermRepo;
        this.userPermSetRepo = userPermSetRepo;
        this.groupPermSetRepo = groupPermSetRepo;
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /**
     * Resolves the full effective permissions for a user.
     * Results are cached in Redis (or in-memory fallback) with a 5-minute TTL.
     * When the permissions feature flag is disabled, returns all-permissive defaults.
     */
    @Cacheable(value = CacheConfig.PERMISSIONS_CACHE, key = "'permissions:' + #tenantId + ':' + #userId")
    @Transactional(readOnly = true)
    public ResolvedPermissions resolveForUser(String tenantId, String userId) {
        if (!properties.getSecurity().isPermissionsEnabled()) {
            log.debug("Permissions feature disabled, returning all-permissive defaults for user {}", userId);
            return allPermissiveDefaults();
        }
        log.debug("Resolving permissions for user {} in tenant {}", userId, tenantId);

        User user = userRepository.findByIdAndTenantId(userId, tenantId).orElse(null);
        if (user == null || user.getProfileId() == null) {
            log.debug("User {} has no profile, returning empty permissions", userId);
            return emptyPermissions();
        }

        // 1. Start with profile permissions
        Map<String, Boolean> systemPerms = resolveProfileSystemPermissions(user.getProfileId());
        Map<String, ObjectPermissions> objectPerms = resolveProfileObjectPermissions(user.getProfileId());
        Map<String, Map<String, FieldVisibility>> fieldPerms = resolveProfileFieldPermissions(user.getProfileId());

        // 2. Collect all applicable permission set IDs (direct + group-inherited)
        Set<String> permissionSetIds = new HashSet<>();

        // Direct user assignments
        permissionSetIds.addAll(userPermSetRepo.findPermissionSetIdsByUserId(userId));

        // Group-inherited assignments
        List<UserGroup> userGroups = userGroupRepository.findGroupsByUserId(userId);
        if (!userGroups.isEmpty()) {
            List<String> groupIds = userGroups.stream()
                    .map(UserGroup::getId)
                    .collect(Collectors.toList());
            permissionSetIds.addAll(groupPermSetRepo.findPermissionSetIdsByGroupIds(groupIds));
        }

        // 3. Merge permission set permissions (additive, most permissive wins)
        for (String permSetId : permissionSetIds) {
            mergePermsetSystemPermissions(permSetId, systemPerms);
            mergePermsetObjectPermissions(permSetId, objectPerms);
            mergePermsetFieldPermissions(permSetId, fieldPerms);
        }

        return new ResolvedPermissions(systemPerms, objectPerms, fieldPerms);
    }

    /**
     * Check a single system permission for a user.
     */
    @Transactional(readOnly = true)
    public boolean hasSystemPermission(String tenantId, String userId, String permissionName) {
        ResolvedPermissions resolved = resolveForUser(tenantId, userId);
        return resolved.hasSystemPermission(permissionName);
    }

    /**
     * Get object-level permissions for a collection.
     */
    @Transactional(readOnly = true)
    public ObjectPermissions getObjectPermissions(String tenantId, String userId, String collectionId) {
        ResolvedPermissions resolved = resolveForUser(tenantId, userId);
        return resolved.getObjectPermissions(collectionId);
    }

    /**
     * Get field visibility for all fields in a collection.
     */
    @Transactional(readOnly = true)
    public Map<String, FieldVisibility> getFieldPermissions(String tenantId, String userId, String collectionId) {
        ResolvedPermissions resolved = resolveForUser(tenantId, userId);
        Map<String, Map<String, FieldVisibility>> allFieldPerms = resolved.fieldPermissions();
        return allFieldPerms.getOrDefault(collectionId, Collections.emptyMap());
    }

    private Map<String, Boolean> resolveProfileSystemPermissions(String profileId) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (SystemPermission perm : SystemPermission.values()) {
            result.put(perm.name(), false);
        }
        List<ProfileSystemPermission> profilePerms = profileSysPermRepo.findByProfileId(profileId);
        for (ProfileSystemPermission psp : profilePerms) {
            if (psp.isGranted()) {
                result.put(psp.getPermissionName(), true);
            }
        }
        return result;
    }

    private Map<String, ObjectPermissions> resolveProfileObjectPermissions(String profileId) {
        Map<String, ObjectPermissions> result = new HashMap<>();
        List<ProfileObjectPermission> objPerms = profileObjPermRepo.findByProfileId(profileId);
        for (ProfileObjectPermission pop : objPerms) {
            result.put(pop.getCollectionId(), new ObjectPermissions(
                    pop.isCanCreate(), pop.isCanRead(), pop.isCanEdit(),
                    pop.isCanDelete(), pop.isCanViewAll(), pop.isCanModifyAll()
            ));
        }
        return result;
    }

    private Map<String, Map<String, FieldVisibility>> resolveProfileFieldPermissions(String profileId) {
        Map<String, Map<String, FieldVisibility>> result = new HashMap<>();
        List<ProfileFieldPermission> fieldPerms = profileFieldPermRepo.findByProfileId(profileId);
        for (ProfileFieldPermission pfp : fieldPerms) {
            result.computeIfAbsent(pfp.getCollectionId(), k -> new HashMap<>())
                    .put(pfp.getFieldId(), pfp.getVisibility());
        }
        return result;
    }

    private void mergePermsetSystemPermissions(String permSetId, Map<String, Boolean> systemPerms) {
        List<PermsetSystemPermission> perms = permsetSysPermRepo.findByPermissionSetIdAndGrantedTrue(permSetId);
        for (PermsetSystemPermission psp : perms) {
            systemPerms.put(psp.getPermissionName(), true);
        }
    }

    private void mergePermsetObjectPermissions(String permSetId, Map<String, ObjectPermissions> objectPerms) {
        List<PermsetObjectPermission> perms = permsetObjPermRepo.findByPermissionSetId(permSetId);
        for (PermsetObjectPermission pop : perms) {
            ObjectPermissions permsetObj = new ObjectPermissions(
                    pop.isCanCreate(), pop.isCanRead(), pop.isCanEdit(),
                    pop.isCanDelete(), pop.isCanViewAll(), pop.isCanModifyAll()
            );
            objectPerms.merge(pop.getCollectionId(), permsetObj, ObjectPermissions::merge);
        }
    }

    private void mergePermsetFieldPermissions(String permSetId,
                                               Map<String, Map<String, FieldVisibility>> fieldPerms) {
        List<PermsetFieldPermission> perms = permsetFieldPermRepo.findByPermissionSetId(permSetId);
        for (PermsetFieldPermission pfp : perms) {
            Map<String, FieldVisibility> collectionFields =
                    fieldPerms.computeIfAbsent(pfp.getCollectionId(), k -> new HashMap<>());
            // Most permissive wins: VISIBLE > READ_ONLY > HIDDEN
            FieldVisibility existing = collectionFields.get(pfp.getFieldId());
            FieldVisibility incoming = pfp.getVisibility();
            if (existing == null || morePermissive(incoming, existing)) {
                collectionFields.put(pfp.getFieldId(), incoming);
            }
        }
    }

    private boolean morePermissive(FieldVisibility a, FieldVisibility b) {
        return a.ordinal() < b.ordinal(); // VISIBLE(0) < READ_ONLY(1) < HIDDEN(2)
    }

    private ResolvedPermissions emptyPermissions() {
        Map<String, Boolean> systemPerms = new LinkedHashMap<>();
        for (SystemPermission perm : SystemPermission.values()) {
            systemPerms.put(perm.name(), false);
        }
        return new ResolvedPermissions(systemPerms, Collections.emptyMap(), Collections.emptyMap());
    }

    private ResolvedPermissions allPermissiveDefaults() {
        Map<String, Boolean> systemPerms = new LinkedHashMap<>();
        for (SystemPermission perm : SystemPermission.values()) {
            systemPerms.put(perm.name(), true);
        }
        return new ResolvedPermissions(systemPerms, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Evicts all entries from the permissions cache.
     * Call this when profiles, permission sets, or user/group assignments change.
     */
    @CacheEvict(value = CacheConfig.PERMISSIONS_CACHE, allEntries = true)
    public void evictPermissionsCache() {
        log.info("Evicting all entries from permissions cache");
    }
}
