package com.emf.controlplane.service;

import com.emf.controlplane.dto.AuthorizationConfigDto;
import com.emf.controlplane.dto.CreatePolicyRequest;
import com.emf.controlplane.dto.CreateRoleRequest;
import com.emf.controlplane.dto.FieldPolicyDto;
import com.emf.controlplane.dto.RoutePolicyDto;
import com.emf.controlplane.dto.SetAuthorizationRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.FieldPolicy;
import com.emf.controlplane.entity.Policy;
import com.emf.controlplane.entity.Role;
import com.emf.controlplane.entity.RoutePolicy;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldPolicyRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.PolicyRepository;
import com.emf.controlplane.repository.RoleRepository;
import com.emf.controlplane.repository.RoutePolicyRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing authorization configuration.
 * Handles roles, policies, route policies, and field policies.
 *
 * Requirements satisfied:
 * - 3.1: Return list of defined roles
 * - 3.2: Create role with valid data and return created role
 * - 3.3: Return list of authorization policies
 * - 3.4: Create policy with valid data and return created policy
 * - 3.5: Set route authorization for a collection (persist route policies)
 * - 3.6: Set field authorization for a collection (persist field policies)
 */
@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final RoleRepository roleRepository;
    private final PolicyRepository policyRepository;
    private final RoutePolicyRepository routePolicyRepository;
    private final FieldPolicyRepository fieldPolicyRepository;
    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final ConfigEventPublisher eventPublisher;

    public AuthorizationService(
            RoleRepository roleRepository,
            PolicyRepository policyRepository,
            RoutePolicyRepository routePolicyRepository,
            FieldPolicyRepository fieldPolicyRepository,
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            @Nullable ConfigEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.policyRepository = policyRepository;
        this.routePolicyRepository = routePolicyRepository;
        this.fieldPolicyRepository = fieldPolicyRepository;
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Lists all defined roles.
     *
     * @return List of all roles ordered by name
     *
     * Validates: Requirement 3.1
     */
    @Transactional(readOnly = true)
    public List<Role> listRoles() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing all roles for tenant: {}", tenantId);
        if (tenantId != null) {
            return roleRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return roleRepository.findAllByOrderByNameAsc();
    }

    /**
     * Creates a new role with the given request data.
     *
     * @param request The role creation request
     * @return The created role with generated ID
     * @throws DuplicateResourceException if a role with the same name already exists
     *
     * Validates: Requirement 3.2
     */
    @Transactional
    public Role createRole(CreateRoleRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating role with name: {} for tenant: {}", request.getName(), tenantId);

        // Check for duplicate name
        if (tenantId != null) {
            if (roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new DuplicateResourceException("Role", "name", request.getName());
            }
        } else {
            if (roleRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("Role", "name", request.getName());
            }
        }

        // Create the role entity
        Role role = new Role(request.getName(), request.getDescription());
        if (tenantId != null) {
            role.setTenantId(tenantId);
        }

        // Save and return
        role = roleRepository.save(role);

        log.info("Created role with id: {}", role.getId());
        return role;
    }

    /**
     * Lists all authorization policies.
     *
     * @return List of all policies ordered by name
     *
     * Validates: Requirement 3.3
     */
    @Transactional(readOnly = true)
    public List<Policy> listPolicies() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing all policies for tenant: {}", tenantId);
        if (tenantId != null) {
            return policyRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return policyRepository.findAllByOrderByNameAsc();
    }

    /**
     * Creates a new policy with the given request data.
     *
     * @param request The policy creation request
     * @return The created policy with generated ID
     * @throws DuplicateResourceException if a policy with the same name already exists
     *
     * Validates: Requirement 3.4
     */
    @Transactional
    public Policy createPolicy(CreatePolicyRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating policy with name: {} for tenant: {}", request.getName(), tenantId);

        // Check for duplicate name
        if (tenantId != null) {
            if (policyRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new DuplicateResourceException("Policy", "name", request.getName());
            }
        } else {
            if (policyRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("Policy", "name", request.getName());
            }
        }

        // Create the policy entity
        Policy policy = new Policy(request.getName(), request.getDescription(), request.getExpression(), request.getRules());
        if (tenantId != null) {
            policy.setTenantId(tenantId);
        }

        // Save and return
        policy = policyRepository.save(policy);

        log.info("Created policy with id: {}", policy.getId());
        return policy;
    }

    /**
     * Sets the authorization configuration for a collection.
     * This replaces any existing route and field policies for the collection.
     *
     * @param collectionId The collection ID to set authorization for
     * @param request The authorization configuration request
     * @return The complete authorization configuration for the collection
     * @throws ResourceNotFoundException if the collection or any referenced policy/field does not exist
     *
     * Validates: Requirements 3.5, 3.6
     */
    @Transactional
    public AuthorizationConfigDto setCollectionAuthorization(String collectionId, SetAuthorizationRequest request) {
        log.info("Setting authorization for collection: {}", collectionId);

        // Verify collection exists and is active
        Collection collection = collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));

        // Delete existing route policies for this collection
        // Uses @Modifying(flushAutomatically=true) to ensure DELETE is flushed before INSERT
        routePolicyRepository.deleteByCollectionId(collectionId);

        // Delete existing field policies for fields in this collection
        // Uses @Modifying(flushAutomatically=true) to ensure DELETE is flushed before INSERT
        fieldPolicyRepository.deleteByCollectionId(collectionId);

        // Create new route policies
        List<RoutePolicy> newRoutePolicies = new ArrayList<>();
        for (SetAuthorizationRequest.RoutePolicyRequest routePolicyRequest : request.getRoutePolicies()) {
            Policy policy = policyRepository.findById(routePolicyRequest.getPolicyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Policy", routePolicyRequest.getPolicyId()));

            RoutePolicy routePolicy = new RoutePolicy(collection, routePolicyRequest.getOperation(), policy);
            newRoutePolicies.add(routePolicy);
        }
        List<RoutePolicy> savedRoutePolicies = routePolicyRepository.saveAll(newRoutePolicies);

        // Create new field policies
        List<FieldPolicy> newFieldPolicies = new ArrayList<>();
        for (SetAuthorizationRequest.FieldPolicyRequest fieldPolicyRequest : request.getFieldPolicies()) {
            Field field = fieldRepository.findByIdAndCollectionIdAndActiveTrue(
                    fieldPolicyRequest.getFieldId(), collectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Field", fieldPolicyRequest.getFieldId()));

            Policy policy = policyRepository.findById(fieldPolicyRequest.getPolicyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Policy", fieldPolicyRequest.getPolicyId()));

            FieldPolicy fieldPolicy = new FieldPolicy(field, fieldPolicyRequest.getOperation(), policy);
            newFieldPolicies.add(fieldPolicy);
        }
        List<FieldPolicy> savedFieldPolicies = fieldPolicyRepository.saveAll(newFieldPolicies);

        // Publish authorization changed event
        publishAuthzChangedEvent(collection, savedRoutePolicies, savedFieldPolicies);

        log.info("Set authorization for collection: {} with {} route policies and {} field policies",
                collectionId, savedRoutePolicies.size(), savedFieldPolicies.size());

        // Build and return the authorization config DTO
        return buildAuthorizationConfigDto(collection, savedRoutePolicies, savedFieldPolicies);
    }

    /**
     * Gets the current authorization configuration for a collection.
     *
     * @param collectionId The collection ID
     * @return The authorization configuration for the collection
     * @throws ResourceNotFoundException if the collection does not exist
     */
    @Transactional(readOnly = true)
    public AuthorizationConfigDto getCollectionAuthorization(String collectionId) {
        log.debug("Getting authorization for collection: {}", collectionId);

        // Verify collection exists and is active
        Collection collection = collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));

        // Get route policies
        List<RoutePolicy> routePolicies = routePolicyRepository.findByCollectionId(collectionId);

        // Get field policies
        List<FieldPolicy> fieldPolicies = fieldPolicyRepository.findByCollectionId(collectionId);

        return buildAuthorizationConfigDto(collection, routePolicies, fieldPolicies);
    }

    /**
     * Builds an AuthorizationConfigDto from the collection and its policies.
     */
    private AuthorizationConfigDto buildAuthorizationConfigDto(
            Collection collection,
            List<RoutePolicy> routePolicies,
            List<FieldPolicy> fieldPolicies) {

        List<RoutePolicyDto> routePolicyDtos = routePolicies.stream()
                .map(RoutePolicyDto::fromEntity)
                .collect(Collectors.toList());

        List<FieldPolicyDto> fieldPolicyDtos = fieldPolicies.stream()
                .map(FieldPolicyDto::fromEntity)
                .collect(Collectors.toList());

        return new AuthorizationConfigDto(
                collection.getId(),
                collection.getName(),
                routePolicyDtos,
                fieldPolicyDtos
        );
    }

    // ---- Role Hierarchy Methods ----

    /**
     * Returns all roles for a tenant in hierarchy order.
     */
    @Transactional(readOnly = true)
    public List<Role> getRoleHierarchy(String tenantId) {
        return roleRepository.findByTenantIdOrderByHierarchyLevelAscNameAsc(tenantId);
    }

    /**
     * Returns all role IDs below the given role in the hierarchy (recursive).
     */
    @Transactional(readOnly = true)
    public Set<String> getSubordinateRoleIds(String roleId) {
        Set<String> subordinates = new java.util.HashSet<>();
        collectSubordinateRoleIds(roleId, subordinates);
        return subordinates;
    }

    private void collectSubordinateRoleIds(String roleId, Set<String> collected) {
        List<Role> children = roleRepository.findByParentRoleId(roleId);
        for (Role child : children) {
            if (collected.add(child.getId())) {
                collectSubordinateRoleIds(child.getId(), collected);
            }
        }
    }

    /**
     * Returns the root roles (no parent) for a tenant.
     */
    @Transactional(readOnly = true)
    public List<Role> getRootRoles(String tenantId) {
        return roleRepository.findByTenantIdAndParentRoleIsNull(tenantId);
    }

    /**
     * Updates a role's parent in the hierarchy.
     */
    @Transactional
    public Role updateRoleParent(String roleId, String parentRoleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (parentRoleId != null) {
            Role parent = roleRepository.findById(parentRoleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", parentRoleId));
            role.setParentRole(parent);
            role.setHierarchyLevel(parent.getHierarchyLevel() + 1);
        } else {
            role.setParentRole(null);
            role.setHierarchyLevel(0);
        }

        role = roleRepository.save(role);

        // Update child hierarchy levels recursively
        updateChildHierarchyLevels(role);

        log.info("Updated role '{}' parent to '{}'", roleId, parentRoleId);
        return role;
    }

    private void updateChildHierarchyLevels(Role parent) {
        List<Role> children = roleRepository.findByParentRoleId(parent.getId());
        for (Role child : children) {
            child.setHierarchyLevel(parent.getHierarchyLevel() + 1);
            roleRepository.save(child);
            updateChildHierarchyLevels(child);
        }
    }

    /**
     * Publishes an authorization changed event to Kafka.
     * Only publishes if the event publisher is available (Kafka is enabled).
     *
     * @param collection The collection that had authorization changes
     * @param routePolicies The route policies for the collection
     * @param fieldPolicies The field policies for the collection
     *
     * Validates: Requirement 3.7
     */
    private void publishAuthzChangedEvent(Collection collection, List<RoutePolicy> routePolicies, List<FieldPolicy> fieldPolicies) {
        if (eventPublisher != null) {
            eventPublisher.publishAuthzChanged(
                    collection.getId(),
                    collection.getName(),
                    routePolicies,
                    fieldPolicies);
        } else {
            log.debug("Event publishing disabled - authz changed for collection: {}", collection.getId());
        }
    }
}
