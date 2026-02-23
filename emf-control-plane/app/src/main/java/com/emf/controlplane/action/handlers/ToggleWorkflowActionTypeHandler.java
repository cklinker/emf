package com.emf.controlplane.action.handlers;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.service.WorkflowActionTypeService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Action handler for toggling a workflow action type's active state.
 */
@Component
public class ToggleWorkflowActionTypeHandler implements CollectionActionHandler {

    private final WorkflowActionTypeService workflowActionTypeService;

    public ToggleWorkflowActionTypeHandler(WorkflowActionTypeService workflowActionTypeService) {
        this.workflowActionTypeService = workflowActionTypeService;
    }

    @Override
    public String getCollectionName() {
        return "workflow-action-types";
    }

    @Override
    public String getActionName() {
        return "toggle-active";
    }

    @Override
    public boolean isInstanceAction() {
        return true;
    }

    @Override
    public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
        return workflowActionTypeService.toggleActive(id);
    }
}
