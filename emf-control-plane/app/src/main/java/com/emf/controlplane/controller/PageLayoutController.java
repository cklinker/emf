package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreatePageLayoutRequest;
import com.emf.controlplane.dto.LayoutAssignmentDto;
import com.emf.controlplane.dto.PageLayoutDto;
import com.emf.controlplane.service.PageLayoutService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/layouts")
public class PageLayoutController {

    private final PageLayoutService layoutService;

    public PageLayoutController(PageLayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @GetMapping
    public List<PageLayoutDto> listLayouts(
            @RequestParam(required = false) String collectionId) {
        String tenantId = TenantContextHolder.requireTenantId();
        return layoutService.listLayoutDtos(tenantId, collectionId);
    }

    @GetMapping("/{id}")
    public PageLayoutDto getLayout(@PathVariable String id) {
        return layoutService.getLayoutDto(id);
    }

    @PostMapping
    public ResponseEntity<PageLayoutDto> createLayout(
            @RequestParam String collectionId,
            @RequestBody CreatePageLayoutRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var dto = layoutService.createLayout(tenantId, collectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public PageLayoutDto updateLayout(
            @PathVariable String id,
            @RequestBody CreatePageLayoutRequest request) {
        return layoutService.updateLayout(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLayout(@PathVariable String id) {
        layoutService.deleteLayout(id);
        return ResponseEntity.noContent().build();
    }

    // --- Layout Assignments ---

    @GetMapping("/assignments")
    public List<LayoutAssignmentDto> listAssignments(
            @RequestParam String collectionId) {
        String tenantId = TenantContextHolder.requireTenantId();
        return layoutService.listAssignments(tenantId, collectionId).stream()
                .map(LayoutAssignmentDto::fromEntity).toList();
    }

    @PutMapping("/assignments")
    public LayoutAssignmentDto assignLayout(
            @RequestParam String collectionId,
            @RequestParam String profileId,
            @RequestParam(required = false) String recordTypeId,
            @RequestParam String layoutId) {
        String tenantId = TenantContextHolder.requireTenantId();
        var assignment = layoutService.assignLayout(tenantId, collectionId, profileId, recordTypeId, layoutId);
        return LayoutAssignmentDto.fromEntity(assignment);
    }

    @GetMapping("/resolve")
    public ResponseEntity<PageLayoutDto> resolveLayout(
            @RequestParam String collectionId,
            @RequestParam(required = false) String recordTypeId,
            @RequestParam String profileId) {
        String tenantId = TenantContextHolder.requireTenantId();
        var dto = layoutService.getLayoutForUser(tenantId, collectionId, recordTypeId, profileId);
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dto);
    }
}
