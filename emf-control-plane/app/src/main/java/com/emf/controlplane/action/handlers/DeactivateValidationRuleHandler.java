package com.emf.controlplane.action.handlers;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.service.ValidationRuleService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Action handler for deactivating a validation rule.
 */
@Component
public class DeactivateValidationRuleHandler implements CollectionActionHandler {

    private final ValidationRuleService validationRuleService;

    public DeactivateValidationRuleHandler(ValidationRuleService validationRuleService) {
        this.validationRuleService = validationRuleService;
    }

    @Override
    public String getCollectionName() {
        return "validation-rules";
    }

    @Override
    public String getActionName() {
        return "deactivate";
    }

    @Override
    public boolean isInstanceAction() {
        return true;
    }

    @Override
    public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
        validationRuleService.deactivateRule(id);
        return Map.of("status", "deactivated", "id", id);
    }
}
