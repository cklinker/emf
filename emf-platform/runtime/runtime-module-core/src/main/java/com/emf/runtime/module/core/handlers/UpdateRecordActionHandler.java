package com.emf.runtime.module.core.handlers;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.registry.CollectionRegistry;
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
 * Action handler that updates a record in any collection (cross-collection).
 *
 * <p>Config format:
 * <pre>
 * {
 *   "targetCollectionName": "orders",
 *   "recordIdField": "order_id",
 *   "updates": [
 *     {"field": "status", "value": "Approved"},
 *     {"field": "reviewer", "sourceField": "userId"}
 *   ]
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class UpdateRecordActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateRecordActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionRegistry collectionRegistry;

    public UpdateRecordActionHandler(ObjectMapper objectMapper, CollectionRegistry collectionRegistry) {
        this.objectMapper = objectMapper;
        this.collectionRegistry = collectionRegistry;
    }

    @Override
    public String getActionTypeKey() {
        return "UPDATE_RECORD";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                context.actionConfigJson(), new TypeReference<>() {});

            String targetCollectionName = resolveCollectionName(config);
            if (targetCollectionName == null) {
                return ActionResult.failure("Target collection name or ID is required");
            }

            CollectionDefinition targetCollection = collectionRegistry.get(targetCollectionName);
            if (targetCollection == null) {
                return ActionResult.failure("Target collection not found: " + targetCollectionName);
            }

            String targetRecordId = resolveRecordId(config, context);
            if (targetRecordId == null || targetRecordId.isBlank()) {
                return ActionResult.failure("Could not resolve target record ID");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> updates = (List<Map<String, Object>>) config.get("updates");
            if (updates == null || updates.isEmpty()) {
                return ActionResult.failure("No updates defined");
            }

            Map<String, Object> updateData = new HashMap<>();
            for (Map<String, Object> update : updates) {
                String field = (String) update.get("field");
                if (field == null || field.isBlank()) {
                    continue;
                }

                String sourceField = (String) update.get("sourceField");
                if (sourceField != null && !sourceField.isBlank()) {
                    Object value = context.data() != null ? context.data().get(sourceField) : null;
                    updateData.put(field, value);
                } else {
                    updateData.put(field, update.get("value"));
                }
            }

            log.info("Update record action: collection={}, recordId={}, fields={}",
                targetCollectionName, targetRecordId, updateData.keySet());

            return ActionResult.success(Map.of(
                "targetCollectionName", targetCollectionName,
                "targetRecordId", targetRecordId,
                "updatedFields", updateData
            ));
        } catch (Exception e) {
            log.error("Failed to execute update record action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("targetCollectionName") == null && config.get("targetCollectionId") == null) {
                throw new IllegalArgumentException("Config must contain 'targetCollectionName' or 'targetCollectionId'");
            }
            if (config.get("updates") == null) {
                throw new IllegalArgumentException("Config must contain 'updates' array");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }

    private String resolveCollectionName(Map<String, Object> config) {
        String name = (String) config.get("targetCollectionName");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String id = (String) config.get("targetCollectionId");
        if (id != null && !id.isBlank()) {
            return id;
        }
        return null;
    }

    private String resolveRecordId(Map<String, Object> config, ActionContext context) {
        String recordIdField = (String) config.get("recordIdField");
        if (recordIdField != null && !recordIdField.isBlank()) {
            Object value = context.data() != null ? context.data().get(recordIdField) : null;
            return value != null ? value.toString() : null;
        }

        String recordId = (String) config.get("recordId");
        if (recordId != null && !recordId.isBlank()) {
            return recordId;
        }

        return context.recordId();
    }
}
