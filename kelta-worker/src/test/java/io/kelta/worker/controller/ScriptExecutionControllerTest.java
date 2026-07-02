package io.kelta.worker.controller;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionRequest;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import io.kelta.worker.repository.ScriptRepository;
import io.kelta.worker.repository.ScriptRepository.Script;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ScriptExecutionController")
class ScriptExecutionControllerTest {

    private ScriptRepository scriptRepository;
    private ScriptExecutor scriptExecutor;
    private ScriptExecutionController controller;

    @BeforeEach
    void setUp() {
        scriptRepository = mock(ScriptRepository.class);
        scriptExecutor = mock(ScriptExecutor.class);
        controller = new ScriptExecutionController(scriptRepository, scriptExecutor);
    }

    private Script apiScript(String source, boolean active) {
        return new Script("s1", "My Script", "API_ENDPOINT", "javascript", source, active);
    }

    @Test
    @DisplayName("returns 404 when the script is not found")
    void notFound() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.empty());

        var response = controller.execute("s1", "t1", "u1", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(scriptExecutor);
    }

    @Test
    @DisplayName("returns 422 when the script is inactive")
    void inactive() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(apiScript("return {}", false)));

        var response = controller.execute("s1", "t1", "u1", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        verifyNoInteractions(scriptExecutor);
    }

    @Test
    @DisplayName("returns 422 when the script type is not HTTP-invocable")
    void nonInvocableType() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(
                new Script("s1", "Validator", "VALIDATION", "javascript", "return true", true)));

        var response = controller.execute("s1", "t1", "u1", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).containsEntry("success", false);
        verifyNoInteractions(scriptExecutor);
    }

    @Test
    @DisplayName("returns 422 when the script has no source")
    void blankSource() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(apiScript("   ", true)));

        var response = controller.execute("s1", "t1", "u1", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        verifyNoInteractions(scriptExecutor);
    }

    @Test
    @DisplayName("executes an active API_ENDPOINT script and returns the output")
    void executesSuccessfully() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(apiScript("return {ok:true}", true)));
        when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of("ok", true), 12L));

        var body = new HashMap<String, Object>();
        body.put("input", Map.of("x", 1));
        body.put("context", Map.of("collectionName", "orders", "recordId", "r1"));

        var response = controller.execute("s1", "t1", "u1", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("output", Map.of("ok", true));
        assertThat(response.getBody()).containsEntry("executionTimeMs", 12L);
        verify(scriptRepository).insertExecutionLog("t1", "s1", "SUCCESS", 12L, "r1", null);
    }

    @Test
    @DisplayName("passes input and context bindings to the executor")
    @SuppressWarnings("unchecked")
    void passesBindings() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(apiScript("return {}", true)));
        when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of(), 1L));

        var body = new HashMap<String, Object>();
        body.put("input", Map.of("amount", 42));
        body.put("context", Map.of("collectionName", "orders", "recordId", "r1"));

        controller.execute("s1", "t1", "u1", body);

        var captor = org.mockito.ArgumentCaptor.forClass(ScriptExecutionRequest.class);
        verify(scriptExecutor).execute(captor.capture());
        Map<String, Object> bindings = captor.getValue().bindings();
        assertThat(bindings).containsEntry("input", Map.of("amount", 42));
        assertThat((Map<String, Object>) bindings.get("context"))
                .containsEntry("tenantId", "t1")
                .containsEntry("userId", "u1")
                .containsEntry("scriptId", "s1")
                .containsEntry("collectionName", "orders")
                .containsEntry("recordId", "r1");
    }

    @Test
    @DisplayName("returns 200 with success=false when the script fails at runtime")
    void scriptRuntimeFailure() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(apiScript("throw new Error('boom')", true)));
        when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.failure("boom", 3L));

        var response = controller.execute("s1", "t1", "u1", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "boom");
        verify(scriptRepository).insertExecutionLog("t1", "s1", "FAILURE", 3L, null, "boom");
    }

    @Test
    @DisplayName("does not fail the request when audit logging throws")
    void auditLogFailureIsSwallowed() {
        when(scriptRepository.findById("s1")).thenReturn(Optional.of(apiScript("return {}", true)));
        when(scriptExecutor.execute(any(ScriptExecutionRequest.class)))
                .thenReturn(ScriptExecutionResult.success(Map.of(), 1L));
        doThrow(new RuntimeException("db down")).when(scriptRepository)
                .insertExecutionLog(anyString(), anyString(), anyString(), anyLong(), any(), any());

        var response = controller.execute("s1", "t1", "u1", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
    }
}
