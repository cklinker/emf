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

class DeleteRecordActionHandlerTest {

    private DeleteRecordActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);
        Collection mockCollection = mock(Collection.class);
        when(mockCollection.getName()).thenReturn("orders");
        when(collectionService.getCollection(anyString())).thenReturn(mockCollection);
        handler = new DeleteRecordActionHandler(objectMapper, collectionService);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("DELETE_RECORD", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should delete record using recordIdField")
    void shouldDeleteUsingRecordIdField() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "col-2",
                "recordIdField": "order_id"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("col-2", result.outputData().get("targetCollectionId"));
        assertEquals("order-123", result.outputData().get("targetRecordId"));
        assertEquals("DELETE", result.outputData().get("action"));
    }

    @Test
    @DisplayName("Should delete record using static recordId")
    void shouldDeleteUsingStaticRecordId() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "col-2",
                "recordId": "static-uuid-123"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("static-uuid-123", result.outputData().get("targetRecordId"));
    }

    @Test
    @DisplayName("Should default to triggering record ID")
    void shouldDefaultToTriggeringRecordId() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "col-2"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("rec-1", result.outputData().get("targetRecordId"));
    }

    @Test
    @DisplayName("Should fail when target collection ID missing")
    void shouldFailWhenTargetCollectionMissing() {
        ActionContext ctx = createContext("{}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Target collection ID"));
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
            .actionConfigJson("{\"targetCollectionId\": \"invalid-col\"}")
            .workflowRuleId("rule-1").executionLogId("exec-1").resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Target collection not found"));
    }

    @Test
    @DisplayName("Should fail when recordIdField references null field")
    void shouldFailWhenRecordIdFieldNull() {
        ActionContext ctx = ActionContext.builder()
            .tenantId("tenant-1").collectionId("col-1").collectionName("orders")
            .recordId(null).data(Map.of("id", "rec-1"))
            .previousData(Map.of()).changedFields(List.of())
            .userId("user-1")
            .actionConfigJson("{\"targetCollectionId\": \"col-2\", \"recordIdField\": \"missing_field\"}")
            .workflowRuleId("rule-1").executionLogId("exec-1").resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Could not resolve"));
    }

    @Test
    @DisplayName("Validate should reject missing targetCollectionId")
    void validateShouldRejectMissingTargetCollection() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate("{\"targetCollectionId\": \"col-1\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "order_id", "order-123"))
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
