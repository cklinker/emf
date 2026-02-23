package com.emf.controlplane.action.handlers;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.service.ValidationRuleService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Action handler for activating a validation rule.
 */
@Component
public class ActivateValidationRuleHandler implements CollectionActionHandler {

    private final ValidationRuleService validationRuleService;

    public ActivateValidationRuleHandler(ValidationRuleService validationRuleService) {
        this.validationRuleService = validationRuleService;
    }

    @Override
    public String getCollectionName() {
        return "validation-rules";
    }

    @Override
    public String getActionName() {
        return "activate";
    }

    @Override
    public boolean isInstanceAction() {
        return true;
    }

    @Override
    public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
        validationRuleService.activateRule(id);
        return Map.of("status", "activated", "id", id);
    }
}
