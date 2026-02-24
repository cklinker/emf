package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action handler that updates fields on the triggering record.
 *
 * <p>Config format:
 * <pre>
 * {
 *   "updates": [
 *     {"field": "status", "value": "Approved"},
 *     {"field": "priority", "value": "High"}
 *   ]
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class FieldUpdateActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(FieldUpdateActionHandler.class);

    private final ObjectMapper objectMapper;

    public FieldUpdateActionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getActionTypeKey() {
        return "FIELD_UPDATE";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> updates = (List<Map<String, Object>>) config.get("updates");

            if (updates == null || updates.isEmpty()) {
                return ActionResult.failure("No field updates defined in config");
            }

            Map<String, Object> updatedFields = new HashMap<>();
            for (Map<String, Object> update : updates) {
                String field = (String) update.get("field");
                Object value = update.get("value");

                if (field == null || field.isBlank()) {
                    log.warn("Skipping field update with null/blank field name in workflow rule {}",
                        context.workflowRuleId());
                    continue;
                }

                updatedFields.put(field, value);
            }

            if (updatedFields.isEmpty()) {
                return ActionResult.failure("No valid field updates found in config");
            }

            log.info("Field update action: collection={}, recordId={}, fields={}",
                context.collectionName(), context.recordId(), updatedFields.keySet());

            return ActionResult.success(Map.of(
                "updatedFields", updatedFields,
                "recordId", context.recordId(),
                "collectionName", context.collectionName()
            ));
        } catch (Exception e) {
            log.error("Failed to execute field update action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (!config.containsKey("updates")) {
                throw new IllegalArgumentException("Config must contain 'updates' array");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> updates = (List<Map<String, Object>>) config.get("updates");
            if (updates == null || updates.isEmpty()) {
                throw new IllegalArgumentException("'updates' array must not be empty");
            }

            for (Map<String, Object> update : updates) {
                if (update.get("field") == null) {
                    throw new IllegalArgumentException("Each update must have a 'field' property");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
