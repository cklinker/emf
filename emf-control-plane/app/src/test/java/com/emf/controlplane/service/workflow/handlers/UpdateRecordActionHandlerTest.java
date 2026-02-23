package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.service.CollectionService;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateRecordActionHandlerTest {

    private UpdateRecordActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);
        Collection mockCollection = mock(Collection.class);
        when(mockCollection.getName()).thenReturn("orders");
        when(collectionService.getCollection(anyString())).thenReturn(mockCollection);
        handler = new UpdateRecordActionHandler(objectMapper, collectionService);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("UPDATE_RECORD", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should update record with static values")
    void shouldUpdateWithStaticValues() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "col-2",
                "recordIdField": "order_id",
                "updates": [
                    {"field": "status", "value": "Approved"},
                    {"field": "priority", "value": "High"}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("col-2", result.outputData().get("targetCollectionId"));
        assertEquals("order-123", result.outputData().get("targetRecordId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals("Approved", updatedFields.get("status"));
        assertEquals("High", updatedFields.get("priority"));
    }

    @Test
    @DisplayName("Should update with source field values")
    void shouldUpdateWithSourceFieldValues() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "col-2",
                "updates": [
                    {"field": "reviewer", "sourceField": "userId"}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals("user-1", updatedFields.get("reviewer"));
    }

    @Test
    @DisplayName("Should use triggering record ID when no recordIdField specified")
    void shouldUseTriggeringRecordId() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "col-2",
                "updates": [{"field": "status", "value": "Done"}]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("rec-1", result.outputData().get("targetRecordId"));
    }

    @Test
    @DisplayName("Should fail when target collection ID missing")
    void shouldFailWhenTargetCollectionMissing() {
        ActionContext ctx = createContext("{\"updates\": [{\"field\": \"status\", \"value\": \"Done\"}]}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Target collection ID"));
    }

    @Test
    @DisplayName("Should fail when updates missing")
    void shouldFailWhenUpdatesMissing() {
        ActionContext ctx = createContext("{\"targetCollectionId\": \"col-2\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("No updates defined"));
    }

    @Test
    @DisplayName("Should fail when target collection not found")
    void shouldFailWhenTargetCollectionNotFound() {
        when(collectionService.getCollection("invalid-col")).thenThrow(new RuntimeException("Not found"));

        ActionContext ctx = ActionContext.builder()
            .tenantId("tenant-1").collectionId("col-1").collectionName("orders")
            .recordId("rec-1").data(Map.of("id", "rec-1"))
            .previousData(Map.of()).changedFields(List.of())
            .userId("user-1")
            .actionConfigJson("{\"targetCollectionId\": \"invalid-col\", \"updates\": [{\"field\": \"x\", \"value\": \"y\"}]}")
            .workflowRuleId("rule-1").executionLogId("exec-1").resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Target collection not found"));
    }

    @Test
    @DisplayName("Validate should reject missing targetCollectionId")
    void validateShouldRejectMissingTargetCollection() {
        assertThrows(IllegalArgumentException.class, () ->
            handler.validate("{\"updates\": [{\"field\": \"x\", \"value\": \"y\"}]}"));
    }

    @Test
    @DisplayName("Validate should reject missing updates")
    void validateShouldRejectMissingUpdates() {
        assertThrows(IllegalArgumentException.class, () ->
            handler.validate("{\"targetCollectionId\": \"col-1\"}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate(
            "{\"targetCollectionId\": \"col-1\", \"updates\": [{\"field\": \"status\", \"value\": \"Done\"}]}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "status", "Pending", "order_id", "order-123", "userId", "user-1"))
            .previousData(Map.of())
            .changedFields(List.of())
            .userId("user-1")
            .actionConfigJson(configJson)
            .workflowRuleId("rule-1")
            .executionLogId("exec-1")
            .resolvedData(Map.of())
            .build();
    }
}
