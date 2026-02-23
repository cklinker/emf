package com.emf.controlplane.service.workflow.handlers;

import com.emf.controlplane.entity.Script;
import com.emf.controlplane.repository.ScriptExecutionLogRepository;
import com.emf.controlplane.service.ScriptService;
import com.emf.controlplane.service.workflow.ActionContext;
import com.emf.controlplane.service.workflow.ActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvokeScriptActionHandlerTest {

    private InvokeScriptActionHandler handler;
    private ScriptService scriptService;
    private ScriptExecutionLogRepository logRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        scriptService = mock(ScriptService.class);
        logRepository = mock(ScriptExecutionLogRepository.class);
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler = new InvokeScriptActionHandler(objectMapper, scriptService, logRepository);

        Script activeScript = new Script();
        activeScript.setName("Test Script");
        activeScript.setActive(true);
        when(scriptService.getScript("script-1")).thenReturn(activeScript);
    }

    @Test
    @DisplayName("Should return correct action type key")
    void shouldReturnCorrectKey() {
        assertEquals("INVOKE_SCRIPT", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should invoke active script successfully")
    void shouldInvokeScript() {
        ActionContext ctx = createContext("""
            {"scriptId": "script-1"}""");

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        assertEquals("script-1", result.outputData().get("scriptId"));
        assertEquals("Test Script", result.outputData().get("scriptName"));
        assertEquals("QUEUED", result.outputData().get("status"));
        verify(logRepository).save(any());
    }

    @Test
    @DisplayName("Should fail when script ID is missing")
    void shouldFailWhenScriptIdMissing() {
        ActionContext ctx = createContext("{}");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should fail when script not found")
    void shouldFailWhenScriptNotFound() {
        when(scriptService.getScript("nonexistent"))
            .thenThrow(new RuntimeException("Not found"));

        ActionContext ctx = createContext("""
            {"scriptId": "nonexistent"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
    }

    @Test
    @DisplayName("Should fail when script is inactive")
    void shouldFailWhenScriptInactive() {
        Script inactiveScript = new Script();
        inactiveScript.setName("Inactive Script");
        inactiveScript.setActive(false);
        when(scriptService.getScript("inactive-1")).thenReturn(inactiveScript);

        ActionContext ctx = createContext("""
            {"scriptId": "inactive-1"}""");

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("inactive"));
    }

    @Test
    @DisplayName("Validate should reject missing scriptId")
    void validateShouldReject() {
        assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("Validate should accept valid config")
    void validateShouldAccept() {
        assertDoesNotThrow(() -> handler.validate("{\"scriptId\": \"script-1\"}"));
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
