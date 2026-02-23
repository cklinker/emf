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

import java.util.Map;

/**
 * Action handler that deletes a record from a collection.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "targetCollectionId": "uuid-of-collection",
 *   "recordIdField": "order_id"
 * }
 * </pre>
 * or
 * <pre>
 * {
 *   "targetCollectionId": "uuid-of-collection",
 *   "recordId": "static-uuid"
 * }
 * </pre>
 * <p>
 * The {@code recordIdField} references a field in the triggering record that contains
 * the ID of the record to delete. Alternatively, {@code recordId} provides a static ID.
 * If neither is specified, the triggering record's own ID is used.
 */
@Component
public class DeleteRecordActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(DeleteRecordActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionService collectionService;

    public DeleteRecordActionHandler(ObjectMapper objectMapper,
                                      CollectionService collectionService) {
        this.objectMapper = objectMapper;
        this.collectionService = collectionService;
    }

    @Override
    public String getActionTypeKey() {
        return "DELETE_RECORD";
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

            log.info("Delete record action: collection={}, recordId={}",
                targetCollectionName, targetRecordId);

            return ActionResult.success(Map.of(
                "targetCollectionId", targetCollectionId,
                "targetCollectionName", targetCollectionName,
                "targetRecordId", targetRecordId,
                "action", "DELETE"
            ));
        } catch (Exception e) {
            log.error("Failed to execute delete record action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
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

    @Override
    public void validate(String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                configJson, new TypeReference<>() {});

            if (config.get("targetCollectionId") == null) {
                throw new IllegalArgumentException("Config must contain 'targetCollectionId'");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage(), e);
        }
    }
}
