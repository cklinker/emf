package com.emf.controlplane.controller;

import com.emf.controlplane.dto.*;
import com.emf.controlplane.entity.Role;
import com.emf.controlplane.service.AuthorizationService;
import com.emf.controlplane.service.SharingService;
import com.emf.controlplane.service.UserGroupService;
import com.emf.controlplane.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing record-level sharing configuration.
 * Handles OWD settings, sharing rules, record shares, and user groups.
 */
@RestController
@RequestMapping("/control/sharing")
@Tag(name = "Sharing", description = "Record-level sharing management")
public class SharingController {

    private static final Logger log = LoggerFactory.getLogger(SharingController.class);

    private final SharingService sharingService;
    private final UserGroupService userGroupService;
    private final AuthorizationService authorizationService;

    public SharingController(SharingService sharingService,
                              UserGroupService userGroupService,
                              AuthorizationService authorizationService) {
        this.sharingService = sharingService;
        this.userGroupService = userGroupService;
        this.authorizationService = authorizationService;
    }

    // ---- OWD Endpoints ----

    @GetMapping("/owd")
    @Operation(summary = "List all org-wide defaults")
    public ResponseEntity<List<OrgWideDefaultDto>> listOwds() {
        return ResponseEntity.ok(sharingService.listOwds());
    }

    @GetMapping("/owd/{collectionId}")
    @Operation(summary = "Get org-wide default for a collection")
    public ResponseEntity<OrgWideDefaultDto> getOwd(@PathVariable String collectionId) {
        return ResponseEntity.ok(sharingService.getOwd(collectionId));
    }

    @PutMapping("/owd/{collectionId}")
    @Operation(summary = "Set org-wide default for a collection")
    public ResponseEntity<OrgWideDefaultDto> setOwd(
            @PathVariable String collectionId,
            @Valid @RequestBody SetOwdRequest request) {
        return ResponseEntity.ok(sharingService.setOwd(collectionId, request));
    }

    // ---- Sharing Rule Endpoints ----

    @GetMapping("/rules/{collectionId}")
    @Operation(summary = "List sharing rules for a collection")
    public ResponseEntity<List<SharingRuleDto>> listRules(@PathVariable String collectionId) {
        return ResponseEntity.ok(sharingService.listRules(collectionId));
    }

    @PostMapping("/rules/{collectionId}")
    @Operation(summary = "Create a sharing rule")
    public ResponseEntity<SharingRuleDto> createRule(
            @PathVariable String collectionId,
            @Valid @RequestBody CreateSharingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sharingService.createRule(collectionId, request));
    }

    @PutMapping("/rules/{ruleId}")
    @Operation(summary = "Update a sharing rule")
    public ResponseEntity<SharingRuleDto> updateRule(
            @PathVariable String ruleId,
            @Valid @RequestBody UpdateSharingRuleRequest request) {
        return ResponseEntity.ok(sharingService.updateRule(ruleId, request));
    }

    @DeleteMapping("/rules/{ruleId}")
    @Operation(summary = "Delete a sharing rule")
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleId) {
        sharingService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    // ---- Record Share Endpoints ----

    @GetMapping("/records/{collectionId}/{recordId}")
    @Operation(summary = "List shares for a specific record")
    public ResponseEntity<List<RecordShareDto>> listRecordShares(
            @PathVariable String collectionId,
            @PathVariable String recordId) {
        return ResponseEntity.ok(sharingService.listRecordShares(collectionId, recordId));
    }

    @PostMapping("/records/{collectionId}")
    @Operation(summary = "Share a record with a user or group")
    public ResponseEntity<RecordShareDto> createRecordShare(
            @PathVariable String collectionId,
            @Valid @RequestBody CreateRecordShareRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String createdBy = userId != null ? userId : "system";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sharingService.createRecordShare(collectionId, request, createdBy));
    }

    @DeleteMapping("/records/shares/{shareId}")
    @Operation(summary = "Remove a record share")
    public ResponseEntity<Void> deleteRecordShare(@PathVariable String shareId) {
        sharingService.deleteRecordShare(shareId);
        return ResponseEntity.noContent().build();
    }

    // ---- User Group Endpoints ----

    @GetMapping("/groups")
    @Operation(summary = "List user groups")
    public ResponseEntity<List<UserGroupDto>> listGroups() {
        return ResponseEntity.ok(userGroupService.listGroups());
    }

    @GetMapping("/groups/{groupId}")
    @Operation(summary = "Get a user group")
    public ResponseEntity<UserGroupDto> getGroup(@PathVariable String groupId) {
        return ResponseEntity.ok(userGroupService.getGroup(groupId));
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a user group")
    public ResponseEntity<UserGroupDto> createGroup(@Valid @RequestBody CreateUserGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userGroupService.createGroup(request));
    }

    @PutMapping("/groups/{groupId}/members")
    @Operation(summary = "Update group members")
    public ResponseEntity<UserGroupDto> updateGroupMembers(
            @PathVariable String groupId,
            @RequestBody List<String> memberIds) {
        return ResponseEntity.ok(userGroupService.updateGroupMembers(groupId, memberIds));
    }

    @DeleteMapping("/groups/{groupId}")
    @Operation(summary = "Delete a user group")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupId) {
        userGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    // ---- Role Hierarchy Endpoints ----

    @GetMapping("/roles/hierarchy")
    @Operation(summary = "Get role hierarchy tree")
    public ResponseEntity<List<RoleHierarchyDto>> getRoleHierarchy() {
        String tenantId = TenantContextHolder.requireTenantId();
        List<Role> rootRoles = authorizationService.getRootRoles(tenantId);
        List<RoleHierarchyDto> hierarchy = rootRoles.stream()
                .map(RoleHierarchyDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(hierarchy);
    }

    @PutMapping("/roles/{roleId}/parent")
    @Operation(summary = "Set role parent in hierarchy")
    public ResponseEntity<RoleHierarchyDto> setRoleParent(
            @PathVariable String roleId,
            @RequestBody(required = false) String parentRoleId) {
        Role updated = authorizationService.updateRoleParent(roleId, parentRoleId);
        return ResponseEntity.ok(RoleHierarchyDto.fromEntity(updated));
    }
}
