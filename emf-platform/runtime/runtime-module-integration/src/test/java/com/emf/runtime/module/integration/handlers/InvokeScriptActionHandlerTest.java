package com.emf.runtime.module.integration.handlers;

import com.emf.runtime.module.integration.spi.ScriptExecutor;
import com.emf.runtime.workflow.ActionContext;
import com.emf.runtime.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("InvokeScriptActionHandler")
class InvokeScriptActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScriptExecutor scriptExecutor = mock(ScriptExecutor.class);
    private final InvokeScriptActionHandler handler = new InvokeScriptActionHandler(objectMapper, scriptExecutor);

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("INVOKE_SCRIPT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should invoke active script")
    void shouldInvokeActiveScript() {
        when(scriptExecutor.getScript("script-1"))
            .thenReturn(Optional.of(new ScriptExecutor.ScriptInfo("script-1", "My Script", true)));
        when(scriptExecutor.queueExecution(anyString(), anyString(), anyString(), anyString()))
            .thenReturn("exec-123");

        String config = """
            {"scriptId": "script-1"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertTrue(result.successful());
        assertEquals("script-1", result.outputData().get("scriptId"));
        assertEquals("My Script", result.outputData().get("scriptName"));
        assertEquals("QUEUED", result.outputData().get("status"));
    }

    @Test
    @DisplayName("Should fail when script not found")
    void shouldFailWhenScriptNotFound() {
        when(scriptExecutor.getScript("missing"))
            .thenReturn(Optional.empty());

        String config = """
            {"scriptId": "missing"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should fail when script is inactive")
    void shouldFailWhenScriptInactive() {
        when(scriptExecutor.getScript("script-2"))
            .thenReturn(Optional.of(new ScriptExecutor.ScriptInfo("script-2", "Disabled Script", false)));

        String config = """
            {"scriptId": "script-2"}
            """;
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("inactive"));
    }

    @Test
    @DisplayName("Should fail when scriptId is missing")
    void shouldFailWhenScriptIdMissing() {
        String config = "{}";
        ActionResult result = handler.execute(makeContext(config));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Script ID"));
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of()).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
