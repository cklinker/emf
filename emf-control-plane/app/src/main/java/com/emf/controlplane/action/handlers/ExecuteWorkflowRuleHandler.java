package com.emf.controlplane.action.handlers;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.dto.ExecuteWorkflowRequest;
import com.emf.controlplane.service.WorkflowRuleService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Action handler for manually executing a workflow rule.
 */
@Component
public class ExecuteWorkflowRuleHandler implements CollectionActionHandler {

    private final WorkflowRuleService workflowRuleService;

    public ExecuteWorkflowRuleHandler(WorkflowRuleService workflowRuleService) {
        this.workflowRuleService = workflowRuleService;
    }

    @Override
    public String getCollectionName() {
        return "workflow-rules";
    }

    @Override
    public String getActionName() {
        return "execute";
    }

    @Override
    public boolean isInstanceAction() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
        ExecuteWorkflowRequest request = new ExecuteWorkflowRequest();
        if (body != null && body.containsKey("recordIds")) {
            Object recordIds = body.get("recordIds");
            if (recordIds instanceof List) {
                request.setRecordIds((List<String>) recordIds);
            }
        }
        List<String> results = workflowRuleService.executeManual(id, request, userId);
        return Map.of("status", "executed", "id", id, "results", results);
    }
}
