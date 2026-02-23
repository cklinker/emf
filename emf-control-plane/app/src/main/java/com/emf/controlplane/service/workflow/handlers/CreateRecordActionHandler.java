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
 * Action handler that creates a new record in a target collection.
 * <p>
 * Config format:
 * <pre>
 * {
 *   "targetCollectionId": "uuid-of-collection",
 *   "fieldMappings": [
 *     {"targetField": "name", "value": "New Record"},
 *     {"targetField": "status", "value": "Open"},
 *     {"targetField": "source_id", "sourceField": "id"}
 *   ]
 * }
 * </pre>
 * <p>
 * Each field mapping can specify either a static {@code value} or a
 * {@code sourceField} that references a field from the triggering record.
 * <p>
 * The created record data is returned in the {@link ActionResult#outputData()}.
 */
@Component
public class CreateRecordActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(CreateRecordActionHandler.class);

    private final ObjectMapper objectMapper;
    private final CollectionService collectionService;

    public CreateRecordActionHandler(ObjectMapper objectMapper,
                                     CollectionService collectionService) {
        this.objectMapper = objectMapper;
        this.collectionService = collectionService;
    }

    @Override
    public String getActionTypeKey() {
        return "CREATE_RECORD";
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

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fieldMappings = (List<Map<String, Object>>) config.get("fieldMappings");

            Map<String, Object> recordData = new HashMap<>();

            if (fieldMappings != null) {
                for (Map<String, Object> mapping : fieldMappings) {
                    String targetField = (String) mapping.get("targetField");
                    if (targetField == null || targetField.isBlank()) {
                        continue;
                    }

                    // Check for sourceField (copy from triggering record)
                    String sourceField = (String) mapping.get("sourceField");
                    if (sourceField != null && !sourceField.isBlank()) {
                        Object value = context.data() != null ? context.data().get(sourceField) : null;
                        recordData.put(targetField, value);
                    } else {
                        // Use static value
                        recordData.put(targetField, mapping.get("value"));
                    }
                }
            }

            log.info("Create record action: targetCollection={}, fields={}",
                targetCollectionName, recordData.keySet());

            return ActionResult.success(Map.of(
                "targetCollectionId", targetCollectionId,
                "targetCollectionName", targetCollectionName,
                "recordData", recordData
            ));
        } catch (Exception e) {
            log.error("Failed to execute create record action: {}", e.getMessage(), e);
            return ActionResult.failure(e);
        }
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
