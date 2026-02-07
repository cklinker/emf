package com.emf.controlplane.service;

import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The single source of truth for "what can this user do?"
 * Merges profile permissions with permission set permissions using OR-merge logic.
 *
 * Resolution rules:
 * - Object permissions: profile OR any(permSets) per boolean flag
 * - Field permissions: most permissive wins (VISIBLE > READ_ONLY > HIDDEN)
 * - System permissions: profile.granted OR any(permSets.granted)
 */
@Service
public class PermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolver.class);

    private static final Map<String, Integer> VISIBILITY_RANK = Map.of(
            "VISIBLE", 3,
            "READ_ONLY", 2,
            "HIDDEN", 1
    );

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ObjectPermissionRepository objectPermissionRepository;
    private final FieldPermissionRepository fieldPermissionRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final UserPermissionSetRepository userPermissionSetRepository;
    private final PermissionSetRepository permissionSetRepository;
    private final PermsetObjectPermissionRepository permsetObjectPermissionRepository;
    private final PermsetFieldPermissionRepository permsetFieldPermissionRepository;
    private final PermsetSystemPermissionRepository permsetSystemPermissionRepository;

    public PermissionResolver(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            ObjectPermissionRepository objectPermissionRepository,
            FieldPermissionRepository fieldPermissionRepository,
            SystemPermissionRepository systemPermissionRepository,
            UserPermissionSetRepository userPermissionSetRepository,
            PermissionSetRepository permissionSetRepository,
            PermsetObjectPermissionRepository permsetObjectPermissionRepository,
            PermsetFieldPermissionRepository permsetFieldPermissionRepository,
            PermsetSystemPermissionRepository permsetSystemPermissionRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.objectPermissionRepository = objectPermissionRepository;
        this.fieldPermissionRepository = fieldPermissionRepository;
        this.systemPermissionRepository = systemPermissionRepository;
        this.userPermissionSetRepository = userPermissionSetRepository;
        this.permissionSetRepository = permissionSetRepository;
        this.permsetObjectPermissionRepository = permsetObjectPermissionRepository;
        this.permsetFieldPermissionRepository = permsetFieldPermissionRepository;
        this.permsetSystemPermissionRepository = permsetSystemPermissionRepository;
    }

    /**
     * Resolve effective object permissions for a user on a collection.
     * OR-merges profile permissions with all assigned permission set permissions.
     */
    @Transactional(readOnly = true)
    public EffectiveObjectPermission resolveObjectPermission(String userId, String collectionId) {
        log.debug("Resolving object permissions for user {} on collection {}", userId, collectionId);

        // Start with profile permissions
        boolean canCreate = false, canRead = false, canEdit = false;
        boolean canDelete = false, canViewAll = false, canModifyAll = false;

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getProfileId() != null) {
            ObjectPermission profilePerm = objectPermissionRepository
                    .findByProfileIdAndCollectionId(user.getProfileId(), collectionId)
                    .orElse(null);
            if (profilePerm != null) {
                canCreate = profilePerm.isCanCreate();
                canRead = profilePerm.isCanRead();
                canEdit = profilePerm.isCanEdit();
                canDelete = profilePerm.isCanDelete();
                canViewAll = profilePerm.isCanViewAll();
                canModifyAll = profilePerm.isCanModifyAll();
            }
        }

        // OR-merge with permission set permissions
        List<String> permSetIds = getUserPermissionSetIds(userId);
        for (String permSetId : permSetIds) {
            PermsetObjectPermission psPerm = permsetObjectPermissionRepository
                    .findByPermissionSetIdAndCollectionId(permSetId, collectionId)
                    .orElse(null);
            if (psPerm != null) {
                canCreate = canCreate || psPerm.isCanCreate();
                canRead = canRead || psPerm.isCanRead();
                canEdit = canEdit || psPerm.isCanEdit();
                canDelete = canDelete || psPerm.isCanDelete();
                canViewAll = canViewAll || psPerm.isCanViewAll();
                canModifyAll = canModifyAll || psPerm.isCanModifyAll();
            }
        }

        return new EffectiveObjectPermission(canCreate, canRead, canEdit, canDelete, canViewAll, canModifyAll);
    }

    /**
     * Resolve effective field permission for a user on a field.
     * Most permissive visibility wins: VISIBLE > READ_ONLY > HIDDEN.
     */
    @Transactional(readOnly = true)
    public EffectiveFieldPermission resolveFieldPermission(String userId, String fieldId) {
        log.debug("Resolving field permissions for user {} on field {}", userId, fieldId);

        String bestVisibility = "HIDDEN";

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getProfileId() != null) {
            FieldPermission profilePerm = fieldPermissionRepository
                    .findByProfileIdAndFieldId(user.getProfileId(), fieldId)
                    .orElse(null);
            if (profilePerm != null) {
                bestVisibility = morePermissive(bestVisibility, profilePerm.getVisibility());
            } else {
                // Default: if no explicit field permission, field is VISIBLE
                bestVisibility = "VISIBLE";
            }
        } else {
            bestVisibility = "VISIBLE";
        }

        // OR-merge with permission set field permissions
        List<String> permSetIds = getUserPermissionSetIds(userId);
        for (String permSetId : permSetIds) {
            PermsetFieldPermission psPerm = permsetFieldPermissionRepository
                    .findByPermissionSetIdAndFieldId(permSetId, fieldId)
                    .orElse(null);
            if (psPerm != null) {
                bestVisibility = morePermissive(bestVisibility, psPerm.getVisibility());
            }
        }

        return new EffectiveFieldPermission(bestVisibility);
    }

    /**
     * Check if a user has a specific system permission.
     */
    @Transactional(readOnly = true)
    public boolean hasSystemPermission(String userId, String permissionKey) {
        log.debug("Checking system permission {} for user {}", permissionKey, userId);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getProfileId() != null) {
            SystemPermission profilePerm = systemPermissionRepository
                    .findByProfileIdAndPermissionKey(user.getProfileId(), permissionKey)
                    .orElse(null);
            if (profilePerm != null && profilePerm.isGranted()) {
                return true;
            }
        }

        // Check permission sets
        List<String> permSetIds = getUserPermissionSetIds(userId);
        for (String permSetId : permSetIds) {
            PermsetSystemPermission psPerm = permsetSystemPermissionRepository
                    .findByPermissionSetIdAndPermissionKey(permSetId, permissionKey)
                    .orElse(null);
            if (psPerm != null && psPerm.isGranted()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all effective system permissions for a user.
     */
    @Transactional(readOnly = true)
    public Set<String> getEffectiveSystemPermissions(String userId) {
        log.debug("Getting all effective system permissions for user {}", userId);
        Set<String> granted = new HashSet<>();

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getProfileId() != null) {
            systemPermissionRepository.findByProfileId(user.getProfileId()).stream()
                    .filter(SystemPermission::isGranted)
                    .map(SystemPermission::getPermissionKey)
                    .forEach(granted::add);
        }

        List<String> permSetIds = getUserPermissionSetIds(userId);
        for (String permSetId : permSetIds) {
            permsetSystemPermissionRepository.findByPermissionSetId(permSetId).stream()
                    .filter(PermsetSystemPermission::isGranted)
                    .map(PermsetSystemPermission::getPermissionKey)
                    .forEach(granted::add);
        }

        return granted;
    }

    /**
     * Resolve all object permissions for a user across all collections.
     * Returns a map of collectionId -> EffectiveObjectPermission.
     */
    @Transactional(readOnly = true)
    public Map<String, EffectiveObjectPermission> resolveAllObjectPermissions(String userId) {
        Map<String, boolean[]> perms = new HashMap<>();

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getProfileId() != null) {
            for (ObjectPermission op : objectPermissionRepository.findByProfileId(user.getProfileId())) {
                boolean[] flags = perms.computeIfAbsent(op.getCollectionId(), k -> new boolean[6]);
                flags[0] |= op.isCanCreate();
                flags[1] |= op.isCanRead();
                flags[2] |= op.isCanEdit();
                flags[3] |= op.isCanDelete();
                flags[4] |= op.isCanViewAll();
                flags[5] |= op.isCanModifyAll();
            }
        }

        List<String> permSetIds = getUserPermissionSetIds(userId);
        for (String permSetId : permSetIds) {
            for (PermsetObjectPermission pop : permsetObjectPermissionRepository.findByPermissionSetId(permSetId)) {
                boolean[] flags = perms.computeIfAbsent(pop.getCollectionId(), k -> new boolean[6]);
                flags[0] |= pop.isCanCreate();
                flags[1] |= pop.isCanRead();
                flags[2] |= pop.isCanEdit();
                flags[3] |= pop.isCanDelete();
                flags[4] |= pop.isCanViewAll();
                flags[5] |= pop.isCanModifyAll();
            }
        }

        return perms.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> new EffectiveObjectPermission(e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        e.getValue()[3], e.getValue()[4], e.getValue()[5])
        ));
    }

    private List<String> getUserPermissionSetIds(String userId) {
        return userPermissionSetRepository.findByUserId(userId).stream()
                .map(UserPermissionSet::getPermissionSetId)
                .collect(Collectors.toList());
    }

    private String morePermissive(String a, String b) {
        int rankA = VISIBILITY_RANK.getOrDefault(a, 0);
        int rankB = VISIBILITY_RANK.getOrDefault(b, 0);
        return rankA >= rankB ? a : b;
    }

    // Records for effective permissions

    public record EffectiveObjectPermission(
            boolean canCreate, boolean canRead, boolean canEdit, boolean canDelete,
            boolean canViewAll, boolean canModifyAll
    ) {}

    public record EffectiveFieldPermission(
            String visibility
    ) {}
}
