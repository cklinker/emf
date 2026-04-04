package io.kelta.runtime.module.integration.spi.graalvm;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionRequest;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GraalVmScriptExecutor")
class GraalVmScriptExecutorTest {

    private static GraalVmScriptExecutor executor;

    @BeforeAll
    static void setUp() {
        executor = new GraalVmScriptExecutor(5);
    }

    @AfterAll
    static void tearDown() {
        executor.close();
    }

    @Test
    @DisplayName("Should execute simple arithmetic script")
    void shouldExecuteSimpleArithmetic() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("2 + 3", Map.of()));

        assertTrue(result.success());
        assertEquals(5, result.output().get("result"));
        assertTrue(result.executionTimeMs() >= 0);
    }

    @Test
    @DisplayName("Should execute script with string result")
    void shouldExecuteStringResult() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("'hello' + ' ' + 'world'", Map.of()));

        assertTrue(result.success());
        assertEquals("hello world", result.output().get("result"));
    }

    @Test
    @DisplayName("Should execute script with boolean result")
    void shouldExecuteBooleanResult() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("true", Map.of()));

        assertTrue(result.success());
        assertEquals(true, result.output().get("result"));
    }

    @Test
    @DisplayName("Should access bindings from script")
    void shouldAccessBindings() {
        Map<String, Object> bindings = Map.of(
            "record", Map.of("status", "Active", "amount", 100)
        );

        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("record.status", bindings));

        assertTrue(result.success());
        assertEquals("Active", result.output().get("result"));
    }

    @Test
    @DisplayName("Should execute script with object result")
    void shouldExecuteObjectResult() {
        String script = "({ name: 'test', value: 42 })";

        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest(script, Map.of()));

        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) result.output().get("result");
        assertEquals("test", obj.get("name"));
        assertEquals(42, obj.get("value"));
    }

    @Test
    @DisplayName("Should execute script with array result")
    void shouldExecuteArrayResult() {
        String script = "[1, 2, 3]";

        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest(script, Map.of()));

        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) result.output().get("result");
        assertEquals(List.of(1, 2, 3), arr);
    }

    @Test
    @DisplayName("Should handle null result")
    void shouldHandleNullResult() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("null", Map.of()));

        assertTrue(result.success());
        assertNull(result.output().get("result"));
    }

    @Test
    @DisplayName("Should execute multi-line script with input bindings")
    void shouldExecuteMultiLineScript() {
        String script = """
            var total = 0;
            for (var i = 0; i < input.items.length; i++) {
                total += input.items[i];
            }
            total;
            """;
        Map<String, Object> bindings = Map.of(
            "input", Map.of("items", List.of(10, 20, 30))
        );

        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest(script, bindings));

        assertTrue(result.success());
        assertEquals(60, result.output().get("result"));
    }

    @Test
    @DisplayName("Should fail on syntax error")
    void shouldFailOnSyntaxError() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("var x = {", Map.of()));

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("Script error"));
    }

    @Test
    @DisplayName("Should fail on runtime error")
    void shouldFailOnRuntimeError() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("undefinedVariable.property", Map.of()));

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should fail on empty script source")
    void shouldFailOnEmptySource() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("", Map.of()));

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Script source is required"));
    }

    @Test
    @DisplayName("Should fail on null script source")
    void shouldFailOnNullSource() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest(null, Map.of()));

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Script source is required"));
    }

    @Test
    @DisplayName("Should timeout on infinite loop")
    void shouldTimeoutOnInfiniteLoop() {
        GraalVmScriptExecutor shortTimeoutExecutor = new GraalVmScriptExecutor(1);
        try {
            ScriptExecutionResult result = shortTimeoutExecutor.execute(
                new ScriptExecutionRequest("while(true) {}", Map.of()));

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("timed out"));
        } finally {
            shortTimeoutExecutor.close();
        }
    }

    @Test
    @DisplayName("Should execute conditional logic with record data")
    void shouldExecuteConditionalLogic() {
        String script = """
            var status = record.status;
            var amount = record.amount;
            var approved = status === 'Active' && amount > 50;
            ({ approved: approved, message: approved ? 'Approved' : 'Rejected' });
            """;
        Map<String, Object> bindings = Map.of(
            "record", Map.of("status", "Active", "amount", 100)
        );

        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest(script, bindings));

        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) result.output().get("result");
        assertEquals(true, obj.get("approved"));
        assertEquals("Approved", obj.get("message"));
    }

    @Test
    @DisplayName("Should register and retrieve scripts")
    void shouldRegisterAndRetrieveScripts() {
        executor.registerScript(new ScriptExecutor.ScriptInfo("s1", "Test Script", true, "1 + 1"));

        Optional<ScriptExecutor.ScriptInfo> info = executor.getScript("s1");
        assertTrue(info.isPresent());
        assertEquals("Test Script", info.get().name());
        assertTrue(info.get().active());
        assertEquals("1 + 1", info.get().source());
    }

    @Test
    @DisplayName("Should return empty for unknown script ID")
    void shouldReturnEmptyForUnknownScript() {
        Optional<ScriptExecutor.ScriptInfo> info = executor.getScript("unknown-id");
        assertTrue(info.isEmpty());
    }

    @Test
    @DisplayName("Should execute with null bindings")
    void shouldExecuteWithNullBindings() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("42", null));

        assertTrue(result.success());
        assertEquals(42, result.output().get("result"));
    }

    @Test
    @DisplayName("Should handle custom timeout in request")
    void shouldHandleCustomTimeout() {
        ScriptExecutionResult result = executor.execute(
            new ScriptExecutionRequest("'done'", Map.of(), 2));

        assertTrue(result.success());
        assertEquals("done", result.output().get("result"));
    }
}
