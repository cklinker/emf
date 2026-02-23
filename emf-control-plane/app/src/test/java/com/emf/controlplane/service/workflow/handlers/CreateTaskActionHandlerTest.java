package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreateTaskActionHandlerTest {

    private CreateTaskActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new CreateTaskActionHandler(objectMapper);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("CREATE_TASK", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should create task with all fields")
    void shouldCreateTaskWithAllFields() {
        ActionContext ctx = createContext("""
            {
                "subject": "Follow up on order",
                "description": "Review and contact customer",
                "assignTo": "user-2",
                "dueDate": "2024-12-31",
                "priority": "High",
                "status": "Open"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("Follow up on order", result.outputData().get("subject"));
        assertEquals("Open", result.outputData().get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> taskData = (Map<String, Object>) result.outputData().get("taskData");
        assertEquals("Follow up on order", taskData.get("subject"));
        assertEquals("user-2", taskData.get("assignTo"));
        assertEquals("High", taskData.get("priority"));
        assertEquals("2024-12-31", taskData.get("dueDate"));
        assertEquals("rec-1", taskData.get("relatedRecordId"));
        assertEquals("col-1", taskData.get("relatedCollectionId"));
    }

    @Test
    @DisplayName("Should create task with minimal config")
    void shouldCreateTaskMinimal() {
        ActionContext ctx = createContext("""
            {"subject": "Simple task"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("Simple task", result.outputData().get("subject"));
        assertEquals("Open", result.outputData().get("status")); // default status
    }

    @Test
    @DisplayName("Should fail when subject is missing")
    void shouldFailWhenSubjectMissing() {
        ActionContext ctx = createContext("""
            {"assignTo": "user-2"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should fail when subject is blank")
    void shouldFailWhenSubjectBlank() {
        ActionContext ctx = createContext("""
            {"subject": ""}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Validate should reject missing subject")
    void validateShouldReject() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAccept() {
        assertDoesNotThrow(() -> handler.validate("{\"subject\": \"Task\"}"));
    }

    private ActionContext createContext(String configJson) {
        return ActionContext.builder()
            .tenantId("tenant-1")
            .collectionId("col-1")
            .collectionName("orders")
            .recordId("rec-1")
            .data(Map.of("id", "rec-1"))
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
