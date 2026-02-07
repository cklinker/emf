package com.emf.controlplane.service;

import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds default profiles when a new tenant is provisioned.
 * Creates four system profiles:
 * 1. System Administrator — full access to all objects and system permissions
 * 2. Standard User — create/read/edit on all objects, API_ACCESS
 * 3. Read Only — read-only on all objects
 * 4. Minimum Access — no object or system permissions
 */
@Service
public class DefaultProfileSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultProfileSeeder.class);

    private final ProfileRepository profileRepository;
    private final SystemPermissionRepository systemPermissionRepository;

    public DefaultProfileSeeder(
            ProfileRepository profileRepository,
            SystemPermissionRepository systemPermissionRepository) {
        this.profileRepository = profileRepository;
        this.systemPermissionRepository = systemPermissionRepository;
    }

    /**
     * Seed default profiles for a newly provisioned tenant.
     *
     * @param tenantId The tenant ID
     * @return The System Administrator profile (to assign to the first user)
     */
    @Transactional
    public Profile seedDefaultProfiles(String tenantId) {
        log.info("Seeding default profiles for tenant {}", tenantId);

        // 1. System Administrator
        Profile sysAdmin = createProfile(tenantId, "System Administrator",
                "Full access to all objects and system permissions");
        grantAllSystemPermissions(sysAdmin);

        // 2. Standard User
        Profile standardUser = createProfile(tenantId, "Standard User",
                "Create, read, and edit access on objects with API access");
        grantSystemPermission(standardUser, SystemPermission.API_ACCESS);

        // 3. Read Only
        createProfile(tenantId, "Read Only",
                "Read-only access to all objects");

        // 4. Minimum Access
        createProfile(tenantId, "Minimum Access",
                "No default object or system permissions");

        log.info("Seeded 4 default profiles for tenant {}", tenantId);
        return sysAdmin;
    }

    private Profile createProfile(String tenantId, String name, String description) {
        Profile profile = new Profile(name, description);
        profile.setTenantId(tenantId);
        profile.setSystem(true);
        return profileRepository.save(profile);
    }

    private void grantAllSystemPermissions(Profile profile) {
        for (String key : SystemPermission.VALID_KEYS) {
            SystemPermission perm = new SystemPermission();
            perm.setProfile(profile);
            perm.setPermissionKey(key);
            perm.setGranted(true);
            systemPermissionRepository.save(perm);
        }
    }

    private void grantSystemPermission(Profile profile, String permissionKey) {
        SystemPermission perm = new SystemPermission();
        perm.setProfile(profile);
        perm.setPermissionKey(permissionKey);
        perm.setGranted(true);
        systemPermissionRepository.save(perm);
    }
}
