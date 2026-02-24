package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreateTaskActionHandler")
class CreateTaskActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CreateTaskActionHandler handler = new CreateTaskActionHandler(objectMapper);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("CREATE_TASK", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should create task with subject")
    void shouldCreateTaskWithSubject() {
        String config = """
            {"subject": "Follow up on order", "priority": "High"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("Follow up on order", result.outputData().get("subject"));
    }

    @Test
    @DisplayName("Should set default status to Open")
    void shouldSetDefaultStatusToOpen() {
        String config = """
            {"subject": "Review"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("Open", result.outputData().get("status"));
    }

    @Test
    @DisplayName("Should include related record info")
    void shouldIncludeRelatedRecordInfo() {
        String config = """
            {"subject": "Task"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        @SuppressWarnings("unchecked")
        Map<String, Object> taskData = (Map<String, Object>) result.outputData().get("taskData");
        assertEquals("r1", taskData.get("relatedRecordId"));
        assertEquals("c1", taskData.get("relatedCollectionId"));
        assertEquals("orders", taskData.get("relatedCollectionName"));
    }

    @Test
    @DisplayName("Should fail when subject is missing")
    void shouldFailWhenSubjectMissing() {
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .userId("user-1").data(Map.of())
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
