package com.emf.controlplane.service.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates workflow action configurations at save time.
 * <p>
 * When a workflow rule is created or updated, this validator checks each action's
 * config JSON against the handler's {@link ActionHandler#validate(String)} method.
 * Returns descriptive errors for any invalid configurations.
 */
@Component
public class WorkflowActionValidator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowActionValidator.class);

    private final ActionHandlerRegistry handlerRegistry;

    public WorkflowActionValidator(ActionHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * Validates a single action's configuration.
     *
     * @param actionType the action type key
     * @param configJson the action configuration JSON
     * @return validation error message, or null if valid
     */
    public String validateAction(String actionType, String configJson) {
        Optional<ActionHandler> handlerOpt = handlerRegistry.getHandler(actionType);
        if (handlerOpt.isEmpty()) {
            return "Unknown action type: " + actionType;
        }

        try {
            handlerOpt.get().validate(configJson);
            return null; // Valid
        } catch (IllegalArgumentException e) {
            return String.format("Action '%s' config error: %s", actionType, e.getMessage());
        } catch (Exception e) {
            return String.format("Action '%s' config validation failed: %s", actionType, e.getMessage());
        }
    }

    /**
     * Validates all actions in a workflow rule.
     *
     * @param actions list of action type/config pairs
     * @return list of validation errors (empty if all valid)
     */
    public List<String> validateActions(List<ActionDefinition> actions) {
        List<String> errors = new ArrayList<>();

        if (actions == null || actions.isEmpty()) {
            return errors;
        }

        for (int i = 0; i < actions.size(); i++) {
            ActionDefinition action = actions.get(i);
            String error = validateAction(action.actionType(), action.configJson());
            if (error != null) {
                errors.add(String.format("Action %d: %s", i + 1, error));
            }
        }

        return errors;
    }

    /**
     * Simple record for an action type + config pair for validation.
     */
    public record ActionDefinition(String actionType, String configJson) {}
}
