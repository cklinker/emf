package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.CollectionService;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionHandler;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action handler that updates a record in any collection (cross-collection).
 * <p>
 * Config format:
 * <pre>
 * {
 *   "targetCollectionId": "uuid-of-collection",
 *   "recordIdField": "order_id",
 *   "updates": [
 *     {"field": "status", "value": "Approved"},
 *     {"field": "reviewer", "sourceField": "userId"}
 *   ]
 * }
 * </pre>
 * <p>
 * The {@code recordIdField} references a field in the triggering record that contains
 * the ID of the record to update. Each update can specify either a static {@code value}
 * or a {@code sourceField} that references a field from the triggering record.
 */
@Component
public class UpdateRecordActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateRecordActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionService collectionService;

    public UpdateRecordActionHandler(ObjectMapper objectMapper,
                                      CollectionService collectionService) {
        this.objectMapper = objectMapper;
        this.collectionService = collectionService;
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

            String targetCollectionId = (String) config.get("targetCollectionId");
            if (targetCollectionId == null || targetCollectionId.isBlank()) {
                return ActionResult.failure("Target collection ID is required");
            }

            // Validate target collection exists
            String targetCollectionName;
            try {
                var targetCollection = collectionService.getCollection(targetCollectionId);
                targetCollectionName = targetCollection.getName();
            } catch (Exception e) {
                return ActionResult.failure("Target collection not found: " + targetCollectionId);
            }

            // Resolve the target record ID
            String targetRecordId = resolveRecordId(config, context);
            if (targetRecordId == null || targetRecordId.isBlank()) {
                return ActionResult.failure("Could not resolve target record ID");
            }

            // Build update map
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
                "targetCollectionId", targetCollectionId,
                "targetCollectionName", targetCollectionName,
                "targetRecordId", targetRecordId,
                "updatedFields", updateData
            ));
        } catch (Exception e) {
            log.error("Failed to execute update record action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
    }

    private String resolveRecordId(Map<String, Object> config, ActionContext context) {
        // Option 1: recordIdField — read from triggering record data
        String recordIdField = (String) config.get("recordIdField");
        if (recordIdField != null && !recordIdField.isBlank()) {
            Object value = context.data() != null ? context.data().get(recordIdField) : null;
            return value != null ? value.toString() : null;
        }

        // Option 2: recordId — static value
        String recordId = (String) config.get("recordId");
        if (recordId != null && !recordId.isBlank()) {
            return recordId;
        }

        // Default: use the triggering record's own ID
        return context.recordId();
    }

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("targetCollectionId") == null) {
                throw new IllegalArgumentException("Config must contain 'targetCollectionId'");
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
}
