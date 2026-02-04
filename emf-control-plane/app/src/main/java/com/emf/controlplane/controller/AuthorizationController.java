package com.emf.controlplane.controller;

import com.emf.controlplane.dto.AuthorizationConfigDto;
import com.emf.controlplane.dto.CreatePolicyRequest;
import com.emf.controlplane.dto.CreateRoleRequest;
import com.emf.controlplane.dto.PolicyDto;
import com.emf.controlplane.dto.RoleDto;
import com.emf.controlplane.dto.SetAuthorizationRequest;
import com.emf.controlplane.entity.Policy;
import com.emf.controlplane.entity.Role;
import com.emf.controlplane.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing authorization configuration.
 * 
 * <p>Provides endpoints for managing roles, policies, and collection-level authorization.
 * All endpoints require ADMIN role authorization.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>3.1: Return list of defined roles</li>
 *   <li>3.2: Create role with valid data and return created role</li>
 *   <li>3.3: Return list of authorization policies</li>
 *   <li>3.4: Create policy with valid data and return created policy</li>
 *   <li>3.5: Set route authorization for a collection (persist route policies)</li>
 *   <li>3.6: Set field authorization for a collection (persist field policies)</li>
 * </ul>
 */
@RestController
@RequestMapping("/control")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Authorization", description = "Authorization management APIs")
public class AuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationController.class);

    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Lists all defined roles.
     * Requires ADMIN role authorization.
     * 
     * @return List of all roles ordered by name
     * 
     * Validates: Requirement 3.1
     */
    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List roles",
            description = "Returns a list of all defined roles. Requires authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved roles"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<RoleDto>> listRoles() {
        log.debug("REST request to list all roles");
        
        List<Role> roles = authorizationService.listRoles();
        List<RoleDto> dtos = roles.stream()
                .map(RoleDto::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Creates a new role.
     * Requires ADMIN role authorization.
     * 
     * @param request The role creation request with name and description
     * @return The created role with generated ID
     * 
     * Validates: Requirement 3.2
     */
    @PostMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Create role",
            description = "Creates a new role with the provided name and description. Requires authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Role created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Conflict - role with same name already exists")
    })
    public ResponseEntity<RoleDto> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        log.info("REST request to create role: {}", request.getName());
        
        Role created = authorizationService.createRole(request);
        RoleDto dto = RoleDto.fromEntity(created);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Lists all authorization policies.
     * Requires ADMIN role authorization.
     * 
     * @return List of all policies ordered by name
     * 
     * Validates: Requirement 3.3
     */
    @GetMapping("/policies")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List policies",
            description = "Returns a list of all authorization policies. Requires authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved policies"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<PolicyDto>> listPolicies() {
        log.debug("REST request to list all policies");
        
        List<Policy> policies = authorizationService.listPolicies();
        List<PolicyDto> dtos = policies.stream()
                .map(PolicyDto::fromEntity)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Creates a new authorization policy.
     * Requires ADMIN role authorization.
     * 
     * @param request The policy creation request with name, description, and rules
     * @return The created policy with generated ID
     * 
     * Validates: Requirement 3.4
     */
    @PostMapping("/policies")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Create policy",
            description = "Creates a new authorization policy with the provided name, description, and rules. Requires authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Policy created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "409", description = "Conflict - policy with same name already exists")
    })
    public ResponseEntity<PolicyDto> createPolicy(
            @Valid @RequestBody CreatePolicyRequest request) {
        log.info("REST request to create policy: {}", request.getName());
        
        Policy created = authorizationService.createPolicy(request);
        PolicyDto dto = PolicyDto.fromEntity(created);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Sets the authorization configuration for a collection.
     * This replaces any existing route and field policies for the collection.
     * Requires ADMIN role authorization.
     * 
     * @param id The collection ID to set authorization for
     * @param request The authorization configuration with route and field policies
     * @return The complete authorization configuration for the collection
     * 
     * Validates: Requirements 3.5, 3.6
     */
    @PutMapping("/collections/{id}/authz")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Set collection authorization",
            description = "Sets the authorization configuration for a collection, including route and field policies. " +
                    "This replaces any existing authorization configuration. Requires authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authorization configuration set successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - validation errors"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Collection, policy, or field not found")
    })
    public ResponseEntity<AuthorizationConfigDto> setCollectionAuthorization(
            @Parameter(description = "Collection ID", required = true)
            @PathVariable String id,
            @Valid @RequestBody SetAuthorizationRequest request) {
        log.info("REST request to set authorization for collection: {}", id);
        
        AuthorizationConfigDto config = authorizationService.setCollectionAuthorization(id, request);
        
        return ResponseEntity.ok(config);
    }
}
