package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.module.integration.spi.PendingActionStore;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DelayActionHandler")
class DelayActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PendingActionStore store = mock(PendingActionStore.class);
    private final DelayActionHandler handler = new DelayActionHandler(objectMapper, store);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("DELAY", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should delay by minutes")
    void shouldDelayByMinutes() {
        when(store.save(anyString(), anyString(), anyString(), anyInt(), anyString(), any(Instant.class), any()))
            .thenReturn("pending-123");

        String config = """
            {"delayMinutes": 30}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("pending-123", result.outputData().get("pendingActionId"));
        assertEquals("PENDING", result.outputData().get("status"));
    }

    @Test
    @DisplayName("Should delay until field value")
    void shouldDelayUntilFieldValue() {
        when(store.save(anyString(), anyString(), anyString(), anyInt(), anyString(), any(Instant.class), any()))
            .thenReturn("pending-456");

        String config = """
            {"delayUntilField": "dueDate"}
            """;
        ActionContext ctx = ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("dueDate", "2026-12-31T23:59:59Z")).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

        ActionResult result = handler.execute(ctx);
        assertTrue(result.successful());
        assertEquals("pending-456", result.outputData().get("pendingActionId"));
    }

    @Test
    @DisplayName("Should delay until fixed time")
    void shouldDelayUntilFixedTime() {
        when(store.save(anyString(), anyString(), anyString(), anyInt(), anyString(), any(Instant.class), any()))
            .thenReturn("pending-789");

        String config = """
            {"delayUntilTime": "2026-12-31T23:59:59Z"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
    }

    @Test
    @DisplayName("Should fail when no delay option specified")
    void shouldFailWhenNoDelayOption() {
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("delay time"));
    }

    @Test
    @DisplayName("Should fail when delay field not found in data")
    void shouldFailWhenDelayFieldNotFound() {
        String config = """
            {"delayUntilField": "nonexistent"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of("status", "Active")).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
