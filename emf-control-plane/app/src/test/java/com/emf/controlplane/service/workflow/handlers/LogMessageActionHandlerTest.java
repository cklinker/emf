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

class LogMessageActionHandlerTest {

    private LogMessageActionHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new LogMessageActionHandler(objectMapper);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("LOG_MESSAGE", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should log message with INFO level")
    void shouldLogMessageWithInfoLevel() {
        ActionContext ctx = createContext("""
            {
                "message": "Order status changed",
                "level": "INFO"
            }""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("Order status changed", result.outputData().get("message"));
        assertEquals("INFO", result.outputData().get("level"));
        assertEquals("rule-1", result.outputData().get("workflowRuleId"));
        assertEquals("rec-1", result.outputData().get("recordId"));
    }

    @Test
    @DisplayName("Should default level to INFO")
    void shouldDefaultLevel() {
        ActionContext ctx = createContext("{\"message\": \"test\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("INFO", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should handle WARNING level")
    void shouldHandleWarningLevel() {
        ActionContext ctx = createContext("{\"message\": \"warning\", \"level\": \"WARNING\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("WARNING", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should handle ERROR level")
    void shouldHandleErrorLevel() {
        ActionContext ctx = createContext("{\"message\": \"error occurred\", \"level\": \"ERROR\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("ERROR", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should handle DEBUG level")
    void shouldHandleDebugLevel() {
        ActionContext ctx = createContext("{\"message\": \"debug info\", \"level\": \"DEBUG\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("DEBUG", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should normalize invalid level to INFO")
    void shouldNormalizeInvalidLevel() {
        ActionContext ctx = createContext("{\"message\": \"test\", \"level\": \"CRITICAL\"}");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("INFO", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should fail when message missing")
    void shouldFailWhenMessageMissing() {
        ActionContext ctx = createContext("{\"level\": \"INFO\"}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("message is required"));
    }

    @Test
    @DisplayName("Should handle null recordId")
    void shouldHandleNullRecordId() {
        ActionContext ctx = ActionContext.builder()
            .tenantId("tenant-1").collectionId("col-1").collectionName("orders")
            .recordId(null).data(Map.of())
            .previousData(Map.of()).changedFields(List.of())
            .userId("user-1")
            .actionConfigJson("{\"message\": \"test\"}")
            .workflowRuleId("rule-1").executionLogId("exec-1").resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("", result.outputData().get("recordId"));
    }

    @Test
    @DisplayName("Validate should reject missing message")
    void validateShouldRejectMissingMessage() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAcceptValid() {
        assertDoesNotThrow(() -> handler.validate("{\"message\": \"test log\"}"));
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
