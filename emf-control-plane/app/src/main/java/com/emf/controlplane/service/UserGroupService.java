package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateUserGroupRequest;
import com.emf.controlplane.dto.UserGroupDto;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.entity.UserGroup;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
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
 * Service for managing user groups.
 */
@Service
public class UserGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupService.class);

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;

    public UserGroupService(UserGroupRepository userGroupRepository, UserRepository userRepository) {
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
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

        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            Set<User> members = new HashSet<>(userRepository.findAllById(request.getMemberIds()));
            group.setMembers(members);
        }

        group = userGroupRepository.save(group);
        log.info("Created user group '{}' with {} members", group.getName(), group.getMembers().size());
        return UserGroupDto.fromEntity(group);
    }

    @Transactional
    public UserGroupDto updateGroupMembers(String groupId, List<String> memberIds) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", groupId));

        Set<User> members = new HashSet<>(userRepository.findAllById(memberIds));
        group.setMembers(members);

        group = userGroupRepository.save(group);
        log.info("Updated group '{}' members to {} users", group.getName(), members.size());
        return UserGroupDto.fromEntity(group);
    }

    @Transactional
    public void deleteGroup(String groupId) {
        if (!userGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("UserGroup", groupId);
        }
        userGroupRepository.deleteById(groupId);
        log.info("Deleted user group '{}'", groupId);
    }
}
