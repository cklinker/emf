package com.emf.controlplane.controller;

import com.emf.controlplane.dto.CreateListViewRequest;
import com.emf.controlplane.dto.ListViewDto;
import com.emf.controlplane.service.ListViewService;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/listviews")
public class ListViewController {

    private final ListViewService listViewService;

    public ListViewController(ListViewService listViewService) {
        this.listViewService = listViewService;
    }

    @GetMapping
    public List<ListViewDto> listViews(
            @RequestParam(required = false) String collectionId,
            @RequestParam(required = false) String userId) {
        String tenantId = TenantContextHolder.requireTenantId();
        if (userId != null) {
            return listViewService.listViews(tenantId, collectionId, userId).stream()
                    .map(ListViewDto::fromEntity).toList();
        }
        return listViewService.listAllViews(tenantId, collectionId).stream()
                .map(ListViewDto::fromEntity).toList();
    }

    @GetMapping("/{id}")
    public ListViewDto getView(@PathVariable String id) {
        return ListViewDto.fromEntity(listViewService.getView(id));
    }

    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_LISTVIEWS')")
    @PostMapping
    public ResponseEntity<ListViewDto> createView(
            @RequestParam String collectionId,
            @RequestParam String userId,
            @RequestBody CreateListViewRequest request) {
        String tenantId = TenantContextHolder.requireTenantId();
        var view = listViewService.createView(tenantId, collectionId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ListViewDto.fromEntity(view));
    }

    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_LISTVIEWS')")
    @PutMapping("/{id}")
    public ListViewDto updateView(
            @PathVariable String id,
            @RequestBody CreateListViewRequest request) {
        return ListViewDto.fromEntity(listViewService.updateView(id, request));
    }

    @PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_LISTVIEWS')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteView(@PathVariable String id) {
        listViewService.deleteView(id);
        return ResponseEntity.noContent().build();
    }
}
