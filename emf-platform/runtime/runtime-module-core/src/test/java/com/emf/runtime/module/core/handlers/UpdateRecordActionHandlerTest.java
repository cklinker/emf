package com.emf.runtime.module.core.handlers;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UpdateRecordActionHandler")
class UpdateRecordActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollectionRegistry registry = mock(CollectionRegistry.class);
    private final UpdateRecordActionHandler handler = new UpdateRecordActionHandler(objectMapper, registry);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("UPDATE_RECORD", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should update with static values")
    void shouldUpdateWithStaticValues() {
        when(registry.get("orders")).thenReturn(
            mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders", "updates": [{"field": "status", "value": "Approved"}]}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals("Approved", updatedFields.get("status"));
    }

    @Test
    @DisplayName("Should resolve record ID from source field")
    void shouldResolveRecordIdFromField() {
        when(registry.get("tasks")).thenReturn(
            mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "tasks", "recordIdField": "task_id",
             "updates": [{"field": "status", "value": "Done"}]}
            """;
        ActionContext ctx = ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("task_id", "task-456"))
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

        ActionResult result = handler.execute(ctx);
        assertTrue(result.successful());
        assertEquals("task-456", result.outputData().get("targetRecordId"));
    }

    @Test
    @DisplayName("Should use source field for update values")
    void shouldUseSourceFieldValues() {
        when(registry.get("orders")).thenReturn(
            mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders", "updates": [{"field": "reviewer", "sourceField": "userId"}]}
            """;
        ActionContext ctx = ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("userId", "user-123"))
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

        ActionResult result = handler.execute(ctx);
        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> updatedFields = (Map<String, Object>) result.outputData().get("updatedFields");
        assertEquals("user-123", updatedFields.get("reviewer"));
    }

    @Test
    @DisplayName("Should fail when no updates defined")
    void shouldFailWhenNoUpdates() {
        when(registry.get("orders")).thenReturn(
            mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("name", "Test"))
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
