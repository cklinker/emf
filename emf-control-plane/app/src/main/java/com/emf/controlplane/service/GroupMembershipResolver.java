package com.emf.controlplane.service;

import com.emf.controlplane.repository.GroupMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Resolves effective group membership for a user by flattening nested group hierarchies.
 *
 * Given a user, this service computes the complete set of group IDs the user belongs to,
 * including groups they are indirectly a member of through nested group membership.
 *
 * Example: If User A is in Group X, and Group X is in Group Y, then User A's
 * effective groups are {X, Y}.
 *
 * Safety features:
 * - Cycle detection prevents infinite loops in circular group references
 * - Maximum depth limit (10 levels) prevents excessive recursion
 */
@Service
public class GroupMembershipResolver {

    private static final Logger log = LoggerFactory.getLogger(GroupMembershipResolver.class);
    private static final int MAX_DEPTH = 10;

    private final GroupMembershipRepository groupMembershipRepository;

    public GroupMembershipResolver(GroupMembershipRepository groupMembershipRepository) {
        this.groupMembershipRepository = groupMembershipRepository;
    }

    /**
     * Returns all group IDs that a user effectively belongs to, including
     * groups reached through nested group membership.
     *
     * @param userId the user's ID
     * @return set of all effective group IDs
     */
    @Transactional(readOnly = true)
    public Set<String> getEffectiveGroupIds(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        // Get direct group memberships for the user
        List<String> directGroupIds = groupMembershipRepository.findGroupIdsByUserId(userId);

        if (directGroupIds.isEmpty()) {
            return Collections.emptySet();
        }

        // Expand through nested groups
        Set<String> effectiveGroups = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();

        for (String groupId : directGroupIds) {
            expandGroupHierarchy(groupId, effectiveGroups, visited, 0);
        }

        log.debug("User {} has {} effective groups (from {} direct memberships)",
                userId, effectiveGroups.size(), directGroupIds.size());

        return effectiveGroups;
    }

    /**
     * Recursively expands a group through its parent groups.
     * A group's "parents" are groups that contain it as a member.
     *
     * @param groupId         the current group ID to expand
     * @param effectiveGroups accumulator for all effective group IDs
     * @param visited         set of already-visited group IDs (cycle detection)
     * @param depth           current recursion depth
     */
    private void expandGroupHierarchy(String groupId, Set<String> effectiveGroups,
                                       Set<String> visited, int depth) {
        // Add this group to effective groups
        effectiveGroups.add(groupId);

        // Cycle detection
        if (!visited.add(groupId)) {
            log.warn("Cycle detected in group hierarchy at group {}", groupId);
            return;
        }

        // Depth limit
        if (depth >= MAX_DEPTH) {
            log.warn("Maximum group nesting depth ({}) reached at group {}", MAX_DEPTH, groupId);
            return;
        }

        // Find parent groups that contain this group
        List<String> parentGroupIds = groupMembershipRepository
                .findParentGroupsForGroup(groupId).stream()
                .map(gm -> gm.getGroupId())
                .toList();

        for (String parentGroupId : parentGroupIds) {
            if (!effectiveGroups.contains(parentGroupId)) {
                expandGroupHierarchy(parentGroupId, effectiveGroups, visited, depth + 1);
            }
        }
    }

    /**
     * Returns all user IDs that are effective members of a group,
     * including users from nested child groups.
     *
     * @param groupId the group ID
     * @return set of all effective user member IDs
     */
    @Transactional(readOnly = true)
    public Set<String> getEffectiveUserIds(String groupId) {
        if (groupId == null) {
            return Collections.emptySet();
        }

        Set<String> effectiveUsers = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();

        collectUsersRecursively(groupId, effectiveUsers, visited, 0);

        log.debug("Group {} has {} effective user members", groupId, effectiveUsers.size());
        return effectiveUsers;
    }

    /**
     * Recursively collects user members from a group and its child groups.
     */
    private void collectUsersRecursively(String groupId, Set<String> effectiveUsers,
                                          Set<String> visited, int depth) {
        // Cycle detection
        if (!visited.add(groupId)) {
            log.warn("Cycle detected in group hierarchy at group {}", groupId);
            return;
        }

        // Depth limit
        if (depth >= MAX_DEPTH) {
            log.warn("Maximum group nesting depth ({}) reached at group {}", MAX_DEPTH, groupId);
            return;
        }

        // Add direct user members
        List<String> directUserIds = groupMembershipRepository.findUserMemberIdsByGroupId(groupId);
        effectiveUsers.addAll(directUserIds);

        // Recurse into child groups
        List<String> childGroupIds = groupMembershipRepository.findChildGroupIdsByGroupId(groupId);
        for (String childGroupId : childGroupIds) {
            collectUsersRecursively(childGroupId, effectiveUsers, visited, depth + 1);
        }
    }
}
