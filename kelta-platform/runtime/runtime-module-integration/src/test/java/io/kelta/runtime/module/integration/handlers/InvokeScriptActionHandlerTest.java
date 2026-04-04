package io.kelta.runtime.module.integration.handlers;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionRequest;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("Inline script execution")
    class InlineExecution {

        @Test
        @DisplayName("Should execute inline script successfully")
        void shouldExecuteInlineScript() {
            when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of("result", 42), 15));

            String config = """
                {"scriptSource": "2 + 2", "inputPayload": {"key": "val"}}
                """;
            ActionResult result = handler.execute(makeContext(config));

            assertTrue(result.successful());
            assertEquals("COMPLETED", result.outputData().get("status"));
            assertEquals(15L, result.outputData().get("executionTimeMs"));
            assertEquals(42, result.outputData().get("result"));

            verify(scriptExecutor).execute(argThat(req -> {
                assertEquals("2 + 2", req.scriptSource());
                assertNotNull(req.bindings().get("record"));
                assertNotNull(req.bindings().get("input"));
                assertNotNull(req.bindings().get("context"));
                return true;
            }));
        }

        @Test
        @DisplayName("Should fail when inline script execution fails")
        void shouldFailWhenInlineScriptFails() {
            when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.failure("ReferenceError: x is not defined", 5));

            String config = """
                {"scriptSource": "x.foo"}
                """;
            ActionResult result = handler.execute(makeContext(config));

            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("ReferenceError"));
        }

        @Test
        @DisplayName("Should pass custom timeout to executor")
        void shouldPassCustomTimeout() {
            when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of("result", "ok"), 10));

            String config = """
                {"scriptSource": "'ok'", "timeoutSeconds": 10}
                """;
            handler.execute(makeContext(config));

            verify(scriptExecutor).execute(argThat(req -> {
                assertEquals(10, req.timeoutSeconds());
                return true;
            }));
        }

        @Test
        @DisplayName("Should provide record data in bindings")
        void shouldProvideRecordDataInBindings() {
            when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of("result", "Active"), 5));

            String config = """
                {"scriptSource": "record.status"}
                """;
            ActionContext ctx = ActionContext.builder()
                .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
                .data(Map.of("status", "Active", "amount", 100))
                .previousData(Map.of("status", "Draft"))
                .userId("user-1")
                .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();

            handler.execute(ctx);

            verify(scriptExecutor).execute(argThat(req -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) req.bindings().get("record");
                assertEquals("Active", record.get("status"));
                assertEquals(100, record.get("amount"));

                @SuppressWarnings("unchecked")
                Map<String, Object> prev = (Map<String, Object>) req.bindings().get("previousRecord");
                assertEquals("Draft", prev.get("status"));

                @SuppressWarnings("unchecked")
                Map<String, Object> context = (Map<String, Object>) req.bindings().get("context");
                assertEquals("t1", context.get("tenantId"));
                assertEquals("orders", context.get("collectionName"));
                return true;
            }));
        }
    }

    @Nested
    @DisplayName("Script ID execution")
    class ScriptIdExecution {

        @Test
        @DisplayName("Should execute script by ID when source is available")
        void shouldExecuteByIdWithSource() {
            when(scriptExecutor.getScript("script-1"))
                .thenReturn(Optional.of(new ScriptExecutor.ScriptInfo("script-1", "My Script", true, "1+1")));
            when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of("result", 2), 10));

            String config = """
                {"scriptId": "script-1"}
                """;
            ActionResult result = handler.execute(makeContext(config));

            assertTrue(result.successful());
            assertEquals("COMPLETED", result.outputData().get("status"));
            assertEquals("script-1", result.outputData().get("scriptId"));
            assertEquals("My Script", result.outputData().get("scriptName"));
            verify(scriptExecutor).execute(any(ScriptExecutionRequest.class));
            verify(scriptExecutor, never()).queueExecution(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should queue execution when script has no source")
        void shouldQueueWhenNoSource() {
            when(scriptExecutor.getScript("script-1"))
                .thenReturn(Optional.of(new ScriptExecutor.ScriptInfo("script-1", "My Script", true)));
            when(scriptExecutor.queueExecution(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("exec-123");

            String config = """
                {"scriptId": "script-1"}
                """;
            ActionResult result = handler.execute(makeContext(config));

            assertTrue(result.successful());
            assertEquals("QUEUED", result.outputData().get("status"));
            assertEquals("exec-123", result.outputData().get("executionLogId"));
            verify(scriptExecutor, never()).execute(any(ScriptExecutionRequest.class));
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
        @DisplayName("Should fail when script ID execution returns error")
        void shouldFailWhenScriptIdExecutionFails() {
            when(scriptExecutor.getScript("script-1"))
                .thenReturn(Optional.of(new ScriptExecutor.ScriptInfo("script-1", "My Script", true, "bad()")));
            when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.failure("TypeError: bad is not a function", 3));

            String config = """
                {"scriptId": "script-1"}
                """;
            ActionResult result = handler.execute(makeContext(config));
            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("TypeError"));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Should fail when neither scriptId nor scriptSource provided")
        void shouldFailWhenNoScriptIdOrSource() {
            String config = "{}";
            ActionResult result = handler.execute(makeContext(config));
            assertFalse(result.successful());
            assertTrue(result.errorMessage().contains("scriptSource") || result.errorMessage().contains("scriptId"));
        }

        @Test
        @DisplayName("Should validate config with scriptId")
        void shouldValidateWithScriptId() {
            assertDoesNotThrow(() -> handler.validate("{\"scriptId\": \"abc\"}"));
        }

        @Test
        @DisplayName("Should validate config with scriptSource")
        void shouldValidateWithScriptSource() {
            assertDoesNotThrow(() -> handler.validate("{\"scriptSource\": \"1+1\"}"));
        }

        @Test
        @DisplayName("Should reject config without scriptId or scriptSource")
        void shouldRejectEmptyConfig() {
            assertThrows(IllegalArgumentException.class, () -> handler.validate("{}"));
        }
    }

    private ActionContext makeContext(String config) {
        return ActionContext.builder()
            .tenantId("t1").collectionId("c1").collectionName("orders").recordId("r1")
            .data(Map.of()).userId("user-1")
            .actionConfigJson(config).workflowRuleId("wf1").executionLogId("log1").build();
    }
}
