package com.emf.controlplane.service;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.*;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.*;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing profiles and their associated permissions.
 * Profiles define the base-level permissions for users.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;
    private final ObjectPermissionRepository objectPermissionRepository;
    private final FieldPermissionRepository fieldPermissionRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final UserRepository userRepository;

    public ProfileService(
            ProfileRepository profileRepository,
            ObjectPermissionRepository objectPermissionRepository,
            FieldPermissionRepository fieldPermissionRepository,
            SystemPermissionRepository systemPermissionRepository,
            UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.objectPermissionRepository = objectPermissionRepository;
        this.fieldPermissionRepository = fieldPermissionRepository;
        this.systemPermissionRepository = systemPermissionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Profile> listProfiles() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing profiles for tenant: {}", tenantId);
        if (tenantId != null) {
            return profileRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return profileRepository.findAll();
    }

    @Transactional
    public Profile createProfile(CreateProfileRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating profile '{}' for tenant: {}", request.getName(), tenantId);

        if (tenantId != null && profileRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateResourceException("Profile", "name", request.getName());
        }

        Profile profile = new Profile(request.getName(), request.getDescription());
        if (tenantId != null) {
            profile.setTenantId(tenantId);
        }

        profile = profileRepository.save(profile);
        log.info("Created profile {} ('{}')", profile.getId(), profile.getName());
        return profile;
    }

    @Transactional(readOnly = true)
    public Profile getProfile(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return profileRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile", id));
        }
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", id));
    }

    @Transactional
    public Profile updateProfile(String id, UpdateProfileRequest request) {
        Profile profile = getProfile(id);

        if (request.getName() != null && !request.getName().equals(profile.getName())) {
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null && profileRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new DuplicateResourceException("Profile", "name", request.getName());
            }
            profile.setName(request.getName());
        }
        if (request.getDescription() != null) {
            profile.setDescription(request.getDescription());
        }

        profile = profileRepository.save(profile);
        log.info("Updated profile {}", profile.getId());
        return profile;
    }

    @Transactional
    public void deleteProfile(String id) {
        Profile profile = getProfile(id);

        if (profile.isSystem()) {
            throw new ValidationException("Cannot delete system profile: " + profile.getName());
        }

        profile = profileRepository.save(profile);
        profileRepository.delete(profile);
        log.info("Deleted profile {}", id);
    }

    @Transactional
    public void setObjectPermissions(String profileId, String collectionId, ObjectPermissionRequest request) {
        Profile profile = getProfile(profileId);

        ObjectPermission perm = objectPermissionRepository
                .findByProfileIdAndCollectionId(profileId, collectionId)
                .orElseGet(() -> {
                    ObjectPermission p = new ObjectPermission();
                    p.setProfile(profile);
                    p.setCollectionId(collectionId);
                    return p;
                });

        perm.setCanCreate(request.isCanCreate());
        perm.setCanRead(request.isCanRead());
        perm.setCanEdit(request.isCanEdit());
        perm.setCanDelete(request.isCanDelete());
        perm.setCanViewAll(request.isCanViewAll());
        perm.setCanModifyAll(request.isCanModifyAll());

        objectPermissionRepository.save(perm);
        log.info("Set object permissions for profile {} on collection {}", profileId, collectionId);
    }

    @Transactional
    public void setFieldPermissions(String profileId, List<FieldPermissionRequest> requests) {
        Profile profile = getProfile(profileId);

        for (FieldPermissionRequest req : requests) {
            String visibility = req.getVisibility();
            if (!"VISIBLE".equals(visibility) && !"READ_ONLY".equals(visibility) && !"HIDDEN".equals(visibility)) {
                throw new ValidationException("Invalid visibility: " + visibility);
            }

            FieldPermission perm = fieldPermissionRepository
                    .findByProfileIdAndFieldId(profileId, req.getFieldId())
                    .orElseGet(() -> {
                        FieldPermission p = new FieldPermission();
                        p.setProfile(profile);
                        p.setFieldId(req.getFieldId());
                        return p;
                    });

            perm.setVisibility(visibility);
            fieldPermissionRepository.save(perm);
        }

        log.info("Set {} field permissions for profile {}", requests.size(), profileId);
    }

    @Transactional
    public void setSystemPermissions(String profileId, List<SystemPermissionRequest> requests) {
        Profile profile = getProfile(profileId);

        for (SystemPermissionRequest req : requests) {
            if (!SystemPermission.VALID_KEYS.contains(req.getPermissionKey())) {
                throw new ValidationException("Invalid system permission key: " + req.getPermissionKey());
            }

            SystemPermission perm = systemPermissionRepository
                    .findByProfileIdAndPermissionKey(profileId, req.getPermissionKey())
                    .orElseGet(() -> {
                        SystemPermission p = new SystemPermission();
                        p.setProfile(profile);
                        p.setPermissionKey(req.getPermissionKey());
                        return p;
                    });

            perm.setGranted(req.isGranted());
            systemPermissionRepository.save(perm);
        }

        log.info("Set {} system permissions for profile {}", requests.size(), profileId);
    }

    /**
     * Create a profile programmatically (used for default profile seeding).
     */
    @Transactional
    public Profile createSystemProfile(String tenantId, String name, String description) {
        Profile profile = new Profile(name, description);
        profile.setTenantId(tenantId);
        profile.setSystem(true);
        return profileRepository.save(profile);
    }
}
