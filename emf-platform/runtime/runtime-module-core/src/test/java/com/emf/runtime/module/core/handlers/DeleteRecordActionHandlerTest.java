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

@DisplayName("DeleteRecordActionHandler")
class DeleteRecordActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollectionRegistry registry = mock(CollectionRegistry.class);
    private final DeleteRecordActionHandler handler = new DeleteRecordActionHandler(objectMapper, registry);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("DELETE_RECORD", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should delete record by default (triggering record)")
    void shouldDeleteByDefaultRecordId() {
        when(registry.get("orders")).thenReturn(
            mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "orders"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("r1", result.outputData().get("targetRecordId"));
        assertEquals("DELETE", result.outputData().get("action"));
    }

    @Test
    @DisplayName("Should resolve record ID from field")
    void shouldResolveRecordIdFromField() {
        when(registry.get("tasks")).thenReturn(
            mock(CollectionDefinition.class));

        String config = """
            {"targetCollectionName": "tasks", "recordIdField": "task_id"}
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
    @DisplayName("Should fail when collection not found")
    void shouldFailWhenNotFound() {
        when(registry.get("nonexistent")).thenReturn(null);

        String config = """
            {"targetCollectionName": "nonexistent"}
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
