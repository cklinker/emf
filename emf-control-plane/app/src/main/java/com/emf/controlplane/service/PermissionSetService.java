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
 * Service for managing permission sets and their user assignments.
 * Permission sets provide additive permissions on top of a user's profile.
 */
@Service
public class PermissionSetService {

    private static final Logger log = LoggerFactory.getLogger(PermissionSetService.class);

    private final PermissionSetRepository permissionSetRepository;
    private final PermsetObjectPermissionRepository permsetObjectPermissionRepository;
    private final PermsetFieldPermissionRepository permsetFieldPermissionRepository;
    private final PermsetSystemPermissionRepository permsetSystemPermissionRepository;
    private final UserPermissionSetRepository userPermissionSetRepository;
    private final UserRepository userRepository;

    public PermissionSetService(
            PermissionSetRepository permissionSetRepository,
            PermsetObjectPermissionRepository permsetObjectPermissionRepository,
            PermsetFieldPermissionRepository permsetFieldPermissionRepository,
            PermsetSystemPermissionRepository permsetSystemPermissionRepository,
            UserPermissionSetRepository userPermissionSetRepository,
            UserRepository userRepository) {
        this.permissionSetRepository = permissionSetRepository;
        this.permsetObjectPermissionRepository = permsetObjectPermissionRepository;
        this.permsetFieldPermissionRepository = permsetFieldPermissionRepository;
        this.permsetSystemPermissionRepository = permsetSystemPermissionRepository;
        this.userPermissionSetRepository = userPermissionSetRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionSet> listPermissionSets() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing permission sets for tenant: {}", tenantId);
        if (tenantId != null) {
            return permissionSetRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return permissionSetRepository.findAll();
    }

    @Transactional
    public PermissionSet createPermissionSet(CreatePermissionSetRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating permission set '{}' for tenant: {}", request.getName(), tenantId);

        if (tenantId != null && permissionSetRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateResourceException("PermissionSet", "name", request.getName());
        }

        PermissionSet ps = new PermissionSet(request.getName(), request.getDescription());
        if (tenantId != null) {
            ps.setTenantId(tenantId);
        }

        ps = permissionSetRepository.save(ps);
        log.info("Created permission set {} ('{}')", ps.getId(), ps.getName());
        return ps;
    }

    @Transactional(readOnly = true)
    public PermissionSet getPermissionSet(String id) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return permissionSetRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("PermissionSet", id));
        }
        return permissionSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermissionSet", id));
    }

    @Transactional
    public PermissionSet updatePermissionSet(String id, UpdatePermissionSetRequest request) {
        PermissionSet ps = getPermissionSet(id);

        if (request.getName() != null && !request.getName().equals(ps.getName())) {
            String tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null && permissionSetRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new DuplicateResourceException("PermissionSet", "name", request.getName());
            }
            ps.setName(request.getName());
        }
        if (request.getDescription() != null) {
            ps.setDescription(request.getDescription());
        }

        ps = permissionSetRepository.save(ps);
        log.info("Updated permission set {}", ps.getId());
        return ps;
    }

    @Transactional
    public void deletePermissionSet(String id) {
        PermissionSet ps = getPermissionSet(id);

        if (ps.isSystem()) {
            throw new ValidationException("Cannot delete system permission set: " + ps.getName());
        }

        long assignedUsers = userPermissionSetRepository.countByPermissionSetId(id);
        if (assignedUsers > 0) {
            throw new ValidationException(
                    "Cannot delete permission set with " + assignedUsers + " assigned users. Remove assignments first.");
        }

        permissionSetRepository.delete(ps);
        log.info("Deleted permission set {}", id);
    }

    @Transactional
    public void setObjectPermissions(String permSetId, String collectionId, ObjectPermissionRequest request) {
        PermissionSet ps = getPermissionSet(permSetId);

        PermsetObjectPermission perm = permsetObjectPermissionRepository
                .findByPermissionSetIdAndCollectionId(permSetId, collectionId)
                .orElseGet(() -> {
                    PermsetObjectPermission p = new PermsetObjectPermission();
                    p.setPermissionSet(ps);
                    p.setCollectionId(collectionId);
                    return p;
                });

        perm.setCanCreate(request.isCanCreate());
        perm.setCanRead(request.isCanRead());
        perm.setCanEdit(request.isCanEdit());
        perm.setCanDelete(request.isCanDelete());
        perm.setCanViewAll(request.isCanViewAll());
        perm.setCanModifyAll(request.isCanModifyAll());

        permsetObjectPermissionRepository.save(perm);
        log.info("Set object permissions for permission set {} on collection {}", permSetId, collectionId);
    }

    @Transactional
    public void setFieldPermissions(String permSetId, List<FieldPermissionRequest> requests) {
        PermissionSet ps = getPermissionSet(permSetId);

        for (FieldPermissionRequest req : requests) {
            String visibility = req.getVisibility();
            if (!"VISIBLE".equals(visibility) && !"READ_ONLY".equals(visibility) && !"HIDDEN".equals(visibility)) {
                throw new ValidationException("Invalid visibility: " + visibility);
            }

            PermsetFieldPermission perm = permsetFieldPermissionRepository
                    .findByPermissionSetIdAndFieldId(permSetId, req.getFieldId())
                    .orElseGet(() -> {
                        PermsetFieldPermission p = new PermsetFieldPermission();
                        p.setPermissionSet(ps);
                        p.setFieldId(req.getFieldId());
                        return p;
                    });

            perm.setVisibility(visibility);
            permsetFieldPermissionRepository.save(perm);
        }

        log.info("Set {} field permissions for permission set {}", requests.size(), permSetId);
    }

    @Transactional
    public void setSystemPermissions(String permSetId, List<SystemPermissionRequest> requests) {
        PermissionSet ps = getPermissionSet(permSetId);

        for (SystemPermissionRequest req : requests) {
            if (!SystemPermission.VALID_KEYS.contains(req.getPermissionKey())) {
                throw new ValidationException("Invalid system permission key: " + req.getPermissionKey());
            }

            PermsetSystemPermission perm = permsetSystemPermissionRepository
                    .findByPermissionSetIdAndPermissionKey(permSetId, req.getPermissionKey())
                    .orElseGet(() -> {
                        PermsetSystemPermission p = new PermsetSystemPermission();
                        p.setPermissionSet(ps);
                        p.setPermissionKey(req.getPermissionKey());
                        return p;
                    });

            perm.setGranted(req.isGranted());
            permsetSystemPermissionRepository.save(perm);
        }

        log.info("Set {} system permissions for permission set {}", requests.size(), permSetId);
    }

    @Transactional
    public void assignToUser(String permSetId, String userId) {
        getPermissionSet(permSetId); // verify exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (userPermissionSetRepository.existsByUserIdAndPermissionSetId(userId, permSetId)) {
            throw new DuplicateResourceException("UserPermissionSet", "assignment",
                    userId + ":" + permSetId);
        }

        UserPermissionSet ups = new UserPermissionSet(userId, permSetId);
        userPermissionSetRepository.save(ups);
        log.info("Assigned permission set {} to user {}", permSetId, userId);
    }

    @Transactional
    public void removeFromUser(String permSetId, String userId) {
        if (!userPermissionSetRepository.existsByUserIdAndPermissionSetId(userId, permSetId)) {
            throw new ResourceNotFoundException("UserPermissionSet",
                    userId + ":" + permSetId);
        }

        userPermissionSetRepository.deleteByUserIdAndPermissionSetId(userId, permSetId);
        log.info("Removed permission set {} from user {}", permSetId, userId);
    }

    @Transactional(readOnly = true)
    public List<User> getAssignedUsers(String permSetId) {
        getPermissionSet(permSetId); // verify exists
        List<String> userIds = userPermissionSetRepository.findByPermissionSetId(permSetId).stream()
                .map(UserPermissionSet::getUserId)
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(userIds);
    }

    @Transactional(readOnly = true)
    public List<PermissionSet> getUserPermissionSets(String userId) {
        List<String> permSetIds = userPermissionSetRepository.findByUserId(userId).stream()
                .map(UserPermissionSet::getPermissionSetId)
                .toList();
        return permissionSetRepository.findAllById(permSetIds);
    }
}
