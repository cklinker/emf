package com.emf.controlplane.controller;

import com.emf.controlplane.dto.WorkflowActionTypeDto;
import com.emf.controlplane.service.WorkflowActionTypeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/control/workflow-action-types")
@PreAuthorize("@securityService.hasPermission(#root, 'MANAGE_WORKFLOWS')")
public class WorkflowActionTypeController {

    private final WorkflowActionTypeService service;

    public WorkflowActionTypeController(WorkflowActionTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkflowActionTypeDto> listActionTypes(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        if (category != null) {
            return service.listByCategory(category);
        }
        return activeOnly ? service.listActiveActionTypes() : service.listActionTypes();
    }

    @GetMapping("/{key}")
    public WorkflowActionTypeDto getActionType(@PathVariable String key) {
        return service.getByKey(key);
    }

    @PatchMapping("/{id}/toggle-active")
    public WorkflowActionTypeDto toggleActive(@PathVariable String id) {
        return service.toggleActive(id);
    }
}
