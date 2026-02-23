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
import static org.mockito.Mockito.*;

class CreateRecordActionHandlerTest {

    private CreateRecordActionHandler handler;
    private CollectionService collectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);
        handler = new CreateRecordActionHandler(objectMapper, collectionService);

        Collection targetCollection = new Collection();
        targetCollection.setId("target-col-1");
        targetCollection.setName("tasks");
        when(collectionService.getCollection("target-col-1")).thenReturn(targetCollection);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("CREATE_RECORD", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should create record with static values")
    void shouldCreateWithStaticValues() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "target-col-1",
                "fieldMappings": [
                    {"targetField": "name", "value": "New Task"},
                    {"targetField": "status", "value": "Open"}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("target-col-1", result.outputData().get("targetCollectionId"));
        assertEquals("tasks", result.outputData().get("targetCollectionName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) result.outputData().get("recordData");
        assertEquals("New Task", recordData.get("name"));
        assertEquals("Open", recordData.get("status"));
    }

    @Test
    @DisplayName("Should create record with source field mappings")
    void shouldCreateWithSourceFields() {
        ActionContext ctx = createContext("""
            {
                "targetCollectionId": "target-col-1",
                "fieldMappings": [
                    {"targetField": "source_id", "sourceField": "id"},
                    {"targetField": "source_name", "sourceField": "name"}
                ]
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> recordData = (Map<String, Object>) result.outputData().get("recordData");
        assertEquals("rec-1", recordData.get("source_id"));
        assertEquals("Order #1", recordData.get("source_name"));
    }

    @Test
    @DisplayName("Should fail when target collection ID is missing")
    void shouldFailWhenCollectionMissing() {
        ActionContext ctx = createContext("{}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should fail when target collection not found")
    void shouldFailWhenCollectionNotFound() {
        when(collectionService.getCollection("nonexistent"))
            .thenThrow(new RuntimeException("Not found"));

        ActionContext ctx = createContext("""
            {"targetCollectionId": "nonexistent", "fieldMappings": []}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should handle null field mappings gracefully")
    void shouldHandleNullFieldMappings() {
        ActionContext ctx = createContext("""
            {"targetCollectionId": "target-col-1"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
    }

    @Test
    @DisplayName("Validate should reject missing targetCollectionId")
    void validateShouldReject() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAccept() {
        assertDoesNotThrow(() -> handler.validate("{\"targetCollectionId\": \"col-1\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1", "name", "Order #1", "total", 150.0))
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
