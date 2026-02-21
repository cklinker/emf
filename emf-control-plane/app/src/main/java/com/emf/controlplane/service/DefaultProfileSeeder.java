package com.emf.controlplane.service;

import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Seeds the 7 default profiles for a tenant during provisioning.
 * Also creates object permissions for all existing collections.
 */
@Component
public class DefaultProfileSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultProfileSeeder.class);

    private static final Set<String> STANDARD_USER_PERMS = Set.of("API_ACCESS", "MANAGE_LISTVIEWS");
    private static final Set<String> MARKETING_USER_PERMS = Set.of("API_ACCESS", "MANAGE_LISTVIEWS", "MANAGE_EMAIL_TEMPLATES");
    private static final Set<String> CONTRACT_MANAGER_PERMS = Set.of("API_ACCESS", "MANAGE_LISTVIEWS", "MANAGE_APPROVALS");
    private static final Set<String> SOLUTION_MANAGER_PERMS = Set.of(
            "VIEW_SETUP", "CUSTOMIZE_APPLICATION", "MANAGE_REPORTS",
            "MANAGE_WORKFLOWS", "MANAGE_LISTVIEWS", "API_ACCESS");

    private final ProfileRepository profileRepository;
    private final ProfileSystemPermissionRepository profileSysPermRepo;
    private final ProfileObjectPermissionRepository profileObjPermRepo;
    private final CollectionRepository collectionRepository;

    public DefaultProfileSeeder(ProfileRepository profileRepository,
                                ProfileSystemPermissionRepository profileSysPermRepo,
                                ProfileObjectPermissionRepository profileObjPermRepo,
                                CollectionRepository collectionRepository) {
        this.profileRepository = profileRepository;
        this.profileSysPermRepo = profileSysPermRepo;
        this.profileObjPermRepo = profileObjPermRepo;
        this.collectionRepository = collectionRepository;
    }

    /**
     * Seeds all 7 default profiles for a new tenant.
     * Called from TenantSchemaManager.provisionTenant().
     */
    public void seedDefaultProfiles(String tenantId) {
        log.info("Seeding default profiles for tenant: {}", tenantId);

        Profile sysAdmin = createProfile(tenantId, "System Administrator",
                "Full, unrestricted access to all features and data", true);
        Profile stdUser = createProfile(tenantId, "Standard User",
                "Read, create, and edit records in all collections", true);
        Profile readOnly = createProfile(tenantId, "Read Only",
                "View all records and reports, no create/edit/delete capability", true);
        Profile marketing = createProfile(tenantId, "Marketing User",
                "Standard User plus manage email templates", true);
        Profile contract = createProfile(tenantId, "Contract Manager",
                "Standard User plus manage approval processes", true);
        Profile solution = createProfile(tenantId, "Solution Manager",
                "Customize application structure: collections, fields, layouts, picklists, reports", true);
        Profile minAccess = createProfile(tenantId, "Minimum Access",
                "Login only, no data access until explicitly granted via Permission Sets", true);

        // Seed system permissions
        seedAllPermissions(sysAdmin.getId(), true);
        seedPermissions(stdUser.getId(), STANDARD_USER_PERMS);
        seedPermissions(readOnly.getId(), Set.of("VIEW_ALL_DATA"));
        seedPermissions(marketing.getId(), MARKETING_USER_PERMS);
        seedPermissions(contract.getId(), CONTRACT_MANAGER_PERMS);
        seedPermissions(solution.getId(), SOLUTION_MANAGER_PERMS);
        seedAllPermissions(minAccess.getId(), false);

        // Seed object permissions for existing collections
        List<String> collectionIds = collectionRepository.findActiveTenantCollectionIds(tenantId);
        for (String collectionId : collectionIds) {
            seedObjectPermissionsForCollection(tenantId, collectionId);
        }

        log.info("Seeded {} default profiles for tenant: {}", 7, tenantId);
    }

    /**
     * Creates object permissions for a new collection across all profiles in the tenant.
     * Called from CollectionService.createCollection().
     */
    public void seedObjectPermissionsForCollection(String tenantId, String collectionId) {
        List<Profile> profiles = profileRepository.findByTenantIdOrderByNameAsc(tenantId);
        for (Profile profile : profiles) {
            seedObjectPermissionForProfile(profile, collectionId);
        }
    }

    private Profile createProfile(String tenantId, String name, String description, boolean isSystem) {
        Profile profile = new Profile(tenantId, name, description, isSystem);
        return profileRepository.save(profile);
    }

    private void seedAllPermissions(String profileId, boolean granted) {
        for (SystemPermission perm : SystemPermission.values()) {
            profileSysPermRepo.save(new ProfileSystemPermission(profileId, perm.name(), granted));
        }
    }

    private void seedPermissions(String profileId, Set<String> grantedPerms) {
        for (SystemPermission perm : SystemPermission.values()) {
            profileSysPermRepo.save(
                    new ProfileSystemPermission(profileId, perm.name(), grantedPerms.contains(perm.name())));
        }
    }

    private void seedObjectPermissionForProfile(Profile profile, String collectionId) {
        // Determine permissions based on profile name
        ProfileObjectPermission pop;
        switch (profile.getName()) {
            case "System Administrator":
                pop = new ProfileObjectPermission(profile.getId(), collectionId,
                        true, true, true, true, true, true);
                break;
            case "Standard User":
            case "Marketing User":
            case "Contract Manager":
                pop = new ProfileObjectPermission(profile.getId(), collectionId,
                        true, true, true, true, false, false);
                break;
            case "Solution Manager":
                pop = new ProfileObjectPermission(profile.getId(), collectionId,
                        true, true, true, true, true, false);
                break;
            case "Read Only":
                pop = new ProfileObjectPermission(profile.getId(), collectionId,
                        false, true, false, false, true, false);
                break;
            case "Minimum Access":
            default:
                // No object permissions for Minimum Access
                return;
        }
        profileObjPermRepo.save(pop);
    }
}
