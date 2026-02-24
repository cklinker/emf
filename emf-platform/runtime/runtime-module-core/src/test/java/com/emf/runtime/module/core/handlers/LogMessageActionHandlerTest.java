package com.emf.runtime.module.core.handlers;

import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogMessageActionHandler")
class LogMessageActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LogMessageActionHandler handler = new LogMessageActionHandler(objectMapper);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("LOG_MESSAGE", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should log message at default INFO level")
    void shouldLogAtInfoLevel() {
        String config = """
            {"message": "Order processed"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("Order processed", result.outputData().get("message"));
        assertEquals("INFO", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should log message at specified level")
    void shouldLogAtSpecifiedLevel() {
        String config = """
            {"message": "Something went wrong", "level": "ERROR"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("ERROR", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should default to INFO for invalid level")
    void shouldDefaultToInfoForInvalidLevel() {
        String config = """
            {"message": "Test", "level": "INVALID"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("INFO", result.outputData().get("level"));
    }

    @Test
    @DisplayName("Should fail when message is missing")
    void shouldFailWhenMessageMissing() {
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
