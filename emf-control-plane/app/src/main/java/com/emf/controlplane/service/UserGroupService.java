package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateUserGroupRequest;
import com.emf.controlplane.dto.UserGroupDto;
import com.emf.controlplane.entity.GroupMembership;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.entity.UserGroup;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.GroupMembershipRepository;
import com.emf.controlplane.repository.UserGroupRepository;
import com.emf.controlplane.repository.UserRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing user groups, including nested group membership.
 */
@Service
public class UserGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupService.class);

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final GroupMembershipRepository groupMembershipRepository;

    public UserGroupService(UserGroupRepository userGroupRepository,
                            UserRepository userRepository,
                            GroupMembershipRepository groupMembershipRepository) {
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.groupMembershipRepository = groupMembershipRepository;
    }

    @Transactional(readOnly = true)
    public List<UserGroupDto> listGroups() {
        String tenantId = TenantContextHolder.requireTenantId();
        return userGroupRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(UserGroupDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserGroupDto getGroup(String groupId) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", groupId));
        return UserGroupDto.fromEntity(group);
    }

    @Transactional
    public UserGroupDto createGroup(CreateUserGroupRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();

        if (userGroupRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateResourceException("UserGroup", "name", request.getName());
        }

        UserGroup group = new UserGroup(tenantId, request.getName(), request.getDescription());
        if (request.getGroupType() != null) {
            group.setGroupType(request.getGroupType());
        }

        // Save group first so we have an ID
        group = userGroupRepository.save(group);

        // Add members via both legacy join table and new group_membership table
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            Set<User> members = new HashSet<>(userRepository.findAllById(request.getMemberIds()));
            group.setMembers(members);

            for (String memberId : request.getMemberIds()) {
                if (!groupMembershipRepository.existsByGroupIdAndMemberTypeAndMemberId(
                        group.getId(), GroupMembership.MEMBER_TYPE_USER, memberId)) {
                    groupMembershipRepository.save(
                            new GroupMembership(group.getId(), GroupMembership.MEMBER_TYPE_USER, memberId));
                }
            }

            group = userGroupRepository.save(group);
        }

        log.info("Created user group '{}' with {} members", group.getName(), group.getMembers().size());
        return UserGroupDto.fromEntity(group);
    }

    @Transactional
    public UserGroupDto updateGroupMembers(String groupId, List<String> memberIds) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", groupId));

        // Update legacy join table
        Set<User> members = new HashSet<>(userRepository.findAllById(memberIds));
        group.setMembers(members);

        // Sync group_membership table: remove existing USER memberships, add new ones
        List<GroupMembership> existingUserMemberships = groupMembershipRepository.findByGroupId(groupId)
                .stream()
                .filter(GroupMembership::isUserMember)
                .toList();

        Set<String> newMemberIdSet = new HashSet<>(memberIds);
        Set<String> existingMemberIdSet = existingUserMemberships.stream()
                .map(GroupMembership::getMemberId)
                .collect(Collectors.toSet());

        // Remove members no longer in the list
        for (GroupMembership existing : existingUserMemberships) {
            if (!newMemberIdSet.contains(existing.getMemberId())) {
                groupMembershipRepository.delete(existing);
            }
        }

        // Add new members
        for (String memberId : memberIds) {
            if (!existingMemberIdSet.contains(memberId)) {
                groupMembershipRepository.save(
                        new GroupMembership(groupId, GroupMembership.MEMBER_TYPE_USER, memberId));
            }
        }

        group = userGroupRepository.save(group);
        log.info("Updated group '{}' members to {} users", group.getName(), members.size());
        return UserGroupDto.fromEntity(group);
    }

    /**
     * Adds a child group to a parent group (nesting).
     *
     * @param parentGroupId the parent group ID
     * @param childGroupId  the child group ID to nest inside the parent
     */
    @Transactional
    public void addChildGroup(String parentGroupId, String childGroupId) {
        if (!userGroupRepository.existsById(parentGroupId)) {
            throw new ResourceNotFoundException("UserGroup", parentGroupId);
        }
        if (!userGroupRepository.existsById(childGroupId)) {
            throw new ResourceNotFoundException("UserGroup", childGroupId);
        }
        if (parentGroupId.equals(childGroupId)) {
            throw new IllegalArgumentException("A group cannot be a member of itself");
        }

        if (!groupMembershipRepository.existsByGroupIdAndMemberTypeAndMemberId(
                parentGroupId, GroupMembership.MEMBER_TYPE_GROUP, childGroupId)) {
            groupMembershipRepository.save(
                    new GroupMembership(parentGroupId, GroupMembership.MEMBER_TYPE_GROUP, childGroupId));
            log.info("Added group '{}' as child of group '{}'", childGroupId, parentGroupId);
        }
    }

    /**
     * Removes a child group from a parent group.
     */
    @Transactional
    public void removeChildGroup(String parentGroupId, String childGroupId) {
        groupMembershipRepository.deleteByGroupIdAndMemberTypeAndMemberId(
                parentGroupId, GroupMembership.MEMBER_TYPE_GROUP, childGroupId);
        log.info("Removed group '{}' from parent group '{}'", childGroupId, parentGroupId);
    }

    /**
     * Adds a user to a group via the new group_membership table.
     */
    @Transactional
    public void addUserToGroup(String groupId, String userId) {
        if (!userGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("UserGroup", groupId);
        }

        if (!groupMembershipRepository.existsByGroupIdAndMemberTypeAndMemberId(
                groupId, GroupMembership.MEMBER_TYPE_USER, userId)) {
            groupMembershipRepository.save(
                    new GroupMembership(groupId, GroupMembership.MEMBER_TYPE_USER, userId));
            log.debug("Added user '{}' to group '{}'", userId, groupId);
        }
    }

    /**
     * Removes a user from a group.
     */
    @Transactional
    public void removeUserFromGroup(String groupId, String userId) {
        groupMembershipRepository.deleteByGroupIdAndMemberTypeAndMemberId(
                groupId, GroupMembership.MEMBER_TYPE_USER, userId);
        log.debug("Removed user '{}' from group '{}'", userId, groupId);
    }

    @Transactional
    public void deleteGroup(String groupId) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", groupId));

        if (group.isSystemGroup()) {
            throw new IllegalArgumentException("System groups cannot be deleted");
        }

        userGroupRepository.deleteById(groupId);
        log.info("Deleted user group '{}'", groupId);
    }
}
