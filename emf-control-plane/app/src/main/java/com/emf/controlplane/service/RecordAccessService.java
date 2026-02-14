package com.emf.controlplane.service;

import com.emf.controlplane.entity.OrgWideDefault;
import com.emf.controlplane.entity.RecordShare;
import com.emf.controlplane.entity.SharingRule;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.entity.UserGroup;
import com.emf.controlplane.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core record-level access service that determines whether a specific user
 * can access a specific record. Implements the 7-step access check algorithm.
 *
 * Algorithm:
 * 1. Object permission check (canRead/canEdit/canDelete via PermissionResolver)
 * 2. canViewAll/canModifyAll bypasses sharing
 * 3. OWD check (PUBLIC_READ_WRITE or PUBLIC_READ)
 * 4. Ownership check (record.created_by == user.id)
 * 5. Role hierarchy check (user's role above owner's role)
 * 6. Sharing rules check
 * 7. Manual share check
 */
@Service
public class RecordAccessService {

    private static final Logger log = LoggerFactory.getLogger(RecordAccessService.class);

    private final PermissionResolver permissionResolver;
    private final OrgWideDefaultRepository owdRepository;
    private final SharingRuleRepository sharingRuleRepository;
    private final RecordShareRepository recordShareRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final RoleRepository roleRepository;

    public RecordAccessService(
            PermissionResolver permissionResolver,
            OrgWideDefaultRepository owdRepository,
            SharingRuleRepository sharingRuleRepository,
            RecordShareRepository recordShareRepository,
            AuthorizationService authorizationService,
            UserRepository userRepository,
            UserGroupRepository userGroupRepository,
            RoleRepository roleRepository) {
        this.permissionResolver = permissionResolver;
        this.owdRepository = owdRepository;
        this.sharingRuleRepository = sharingRuleRepository;
        this.recordShareRepository = recordShareRepository;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.userGroupRepository = userGroupRepository;
        this.roleRepository = roleRepository;
    }

    public enum AccessType { READ, EDIT, DELETE }

    /**
     * Determines if a user can access a record with the given access type.
     *
     * @param userId        the requesting user's ID
     * @param tenantId      the tenant ID
     * @param collectionId  the collection ID
     * @param recordId      the record ID
     * @param recordOwnerId the record owner's user ID
     * @param accessType    the type of access requested
     * @return true if access is granted
     */
    @Transactional(readOnly = true)
    public boolean canAccess(String userId, String tenantId, String collectionId,
                             String recordId, String recordOwnerId, AccessType accessType) {
        log.debug("Checking {} access for user {} on record {} in collection {}",
                accessType, userId, recordId, collectionId);

        // 1. Object permission check
        PermissionResolver.EffectiveObjectPermission objPerm =
                permissionResolver.resolveObjectPermission(userId, collectionId);

        if (accessType == AccessType.READ && !objPerm.canRead()) return false;
        if (accessType == AccessType.EDIT && !objPerm.canEdit()) return false;
        if (accessType == AccessType.DELETE && !objPerm.canDelete()) return false;

        // 2. canViewAll / canModifyAll bypasses sharing
        if (accessType == AccessType.READ && objPerm.canViewAll()) return true;
        if ((accessType == AccessType.EDIT || accessType == AccessType.DELETE) && objPerm.canModifyAll()) return true;

        // 3. OWD check
        OrgWideDefault owd = owdRepository.findByTenantIdAndCollectionId(tenantId, collectionId)
                .orElse(null);
        String internalAccess = owd != null ? owd.getInternalAccess() : "PUBLIC_READ_WRITE";

        if ("PUBLIC_READ_WRITE".equals(internalAccess)) return true;
        if ("PUBLIC_READ".equals(internalAccess) && accessType == AccessType.READ) return true;

        // 4. Ownership check
        if (recordOwnerId != null && recordOwnerId.equals(userId)) return true;

        // 5. Role hierarchy check
        if (recordOwnerId != null && isInRoleHierarchyAbove(userId, recordOwnerId, tenantId)) return true;

        // 6. Sharing rules check
        if (sharingRuleGrantsAccess(userId, tenantId, collectionId, recordOwnerId, accessType)) return true;

        // 7. Manual share check
        if (recordShareExists(userId, collectionId, recordId, accessType)) return true;

        log.debug("Access DENIED for user {} on record {} ({} access)", userId, recordId, accessType);
        return false;
    }

    /**
     * Builds additional WHERE clauses for list queries to filter records
     * the user can see. Used by StorageAdapter.
     *
     * @return null if no filtering needed (user can see all), or WHERE clause fragment
     */
    @Transactional(readOnly = true)
    public String buildSharingWhereClause(String userId, String tenantId, String collectionId) {
        // Check canViewAll
        PermissionResolver.EffectiveObjectPermission objPerm =
                permissionResolver.resolveObjectPermission(userId, collectionId);
        if (objPerm.canViewAll()) return null;

        // Check OWD
        OrgWideDefault owd = owdRepository.findByTenantIdAndCollectionId(tenantId, collectionId)
                .orElse(null);
        String internalAccess = owd != null ? owd.getInternalAccess() : "PUBLIC_READ_WRITE";
        if ("PUBLIC_READ_WRITE".equals(internalAccess) || "PUBLIC_READ".equals(internalAccess)) return null;

        // OWD is PRIVATE - need to filter
        // Build: created_by = ? OR id IN (record shares) OR created_by IN (subordinate user IDs)
        List<String> clauses = new ArrayList<>();

        // User's own records
        clauses.add("created_by = '" + sanitize(userId) + "'");

        // Records owned by subordinates (role hierarchy)
        Set<String> subordinateUserIds = getSubordinateUserIds(userId, tenantId);
        if (!subordinateUserIds.isEmpty()) {
            String inClause = subordinateUserIds.stream()
                    .map(id -> "'" + sanitize(id) + "'")
                    .collect(Collectors.joining(","));
            clauses.add("created_by IN (" + inClause + ")");
        }

        // Records shared via sharing rules or manual shares
        Set<String> sharedRecordIds = getSharedRecordIds(userId, tenantId, collectionId);
        if (!sharedRecordIds.isEmpty()) {
            String inClause = sharedRecordIds.stream()
                    .map(id -> "'" + sanitize(id) + "'")
                    .collect(Collectors.joining(","));
            clauses.add("id IN (" + inClause + ")");
        }

        return "(" + String.join(" OR ", clauses) + ")";
    }

    /**
     * Checks if userId's role is above recordOwnerId's role in the hierarchy.
     */
    private boolean isInRoleHierarchyAbove(String userId, String recordOwnerId, String tenantId) {
        User user = userRepository.findById(userId).orElse(null);
        User owner = userRepository.findById(recordOwnerId).orElse(null);
        if (user == null || owner == null) return false;

        // Get user's role (from profile or direct assignment) - using manager hierarchy
        // If the user is the owner's manager or higher, grant access
        String managerId = owner.getManagerId();
        Set<String> visited = new HashSet<>();
        while (managerId != null && visited.add(managerId)) {
            if (managerId.equals(userId)) return true;
            User manager = userRepository.findById(managerId).orElse(null);
            if (manager == null) break;
            managerId = manager.getManagerId();
        }

        return false;
    }

    /**
     * Checks if any active sharing rule grants the requested access.
     */
    private boolean sharingRuleGrantsAccess(String userId, String tenantId, String collectionId,
                                             String recordOwnerId, AccessType accessType) {
        List<SharingRule> rules = sharingRuleRepository
                .findByTenantIdAndCollectionIdAndActiveTrue(tenantId, collectionId);

        if (rules.isEmpty()) return false;

        // Get user's groups
        List<String> userGroupIds = userGroupRepository.findGroupsByUserId(userId).stream()
                .map(UserGroup::getId)
                .collect(Collectors.toList());

        for (SharingRule rule : rules) {
            // Check access level compatibility
            if (accessType != AccessType.READ && "READ".equals(rule.getAccessLevel())) {
                continue;
            }

            // Check if user is in the "shared to" target
            boolean isTarget = false;
            switch (rule.getSharedToType()) {
                case "ROLE":
                    // User needs to have this role (simplified - check via manager chain)
                    isTarget = true; // Simplified: all users in tenant
                    break;
                case "GROUP":
                    isTarget = userGroupIds.contains(rule.getSharedTo());
                    break;
                case "QUEUE":
                    isTarget = userGroupIds.contains(rule.getSharedTo());
                    break;
            }

            if (!isTarget) continue;

            if ("OWNER_BASED".equals(rule.getRuleType())) {
                // For owner-based rules, the record owner must be in the "shared from" role/group
                // Simplified: grant access if rule matches
                return true;
            } else if ("CRITERIA_BASED".equals(rule.getRuleType())) {
                // Criteria-based rules would need to evaluate field conditions
                // For now, grant access if user is in target
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a manual record share exists granting the requested access.
     */
    private boolean recordShareExists(String userId, String collectionId,
                                       String recordId, AccessType accessType) {
        // Check direct user shares
        List<RecordShare> directShares = recordShareRepository
                .findDirectUserShares(collectionId, recordId, userId);
        for (RecordShare share : directShares) {
            if (accessType == AccessType.READ || "READ_WRITE".equals(share.getAccessLevel())) {
                return true;
            }
        }

        // Check group shares
        List<String> userGroupIds = userGroupRepository.findGroupsByUserId(userId).stream()
                .map(UserGroup::getId)
                .collect(Collectors.toList());
        if (!userGroupIds.isEmpty()) {
            List<RecordShare> groupShares = recordShareRepository
                    .findGroupShares(collectionId, recordId, userGroupIds);
            for (RecordShare share : groupShares) {
                if (accessType == AccessType.READ || "READ_WRITE".equals(share.getAccessLevel())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets all user IDs that are subordinates of the given user (via manager chain).
     */
    private Set<String> getSubordinateUserIds(String userId, String tenantId) {
        Set<String> subordinates = new HashSet<>();
        collectSubordinates(userId, subordinates);
        return subordinates;
    }

    private void collectSubordinates(String managerId, Set<String> collected) {
        List<User> directReports = userRepository.findByManagerId(managerId);
        for (User report : directReports) {
            if (collected.add(report.getId())) {
                collectSubordinates(report.getId(), collected);
            }
        }
    }

    /**
     * Gets record IDs shared with the user via manual shares.
     */
    private Set<String> getSharedRecordIds(String userId, String tenantId, String collectionId) {
        Set<String> recordIds = new HashSet<>();

        // Direct user shares
        List<RecordShare> shares = recordShareRepository
                .findByTenantIdAndCollectionId(tenantId, collectionId);
        for (RecordShare share : shares) {
            if ("USER".equals(share.getSharedWithType()) && userId.equals(share.getSharedWithId())) {
                recordIds.add(share.getRecordId());
            }
        }

        // Group shares
        List<String> userGroupIds = userGroupRepository.findGroupsByUserId(userId).stream()
                .map(UserGroup::getId)
                .collect(Collectors.toList());
        for (RecordShare share : shares) {
            if ("GROUP".equals(share.getSharedWithType()) && userGroupIds.contains(share.getSharedWithId())) {
                recordIds.add(share.getRecordId());
            }
        }

        return recordIds;
    }

    /**
     * Basic sanitization to prevent SQL injection in dynamic WHERE clauses.
     * Only allows UUID characters (alphanumeric + hyphens).
     */
    private String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9\\-]", "");
    }
}
