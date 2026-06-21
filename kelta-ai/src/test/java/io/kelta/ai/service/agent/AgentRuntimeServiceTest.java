package io.kelta.ai.service.agent;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.model.AgentExecution;
import io.kelta.ai.service.TokenTrackingService;
import io.kelta.ai.service.tools.DispatchResult;
import io.kelta.ai.service.tools.ToolDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRuntimeService")
class AgentRuntimeServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "user-1";

    @Mock
    private AgentModelClient modelClient;

    @Mock
    private ToolDispatcher toolDispatcher;

    @Mock
    private TokenTrackingService tokenTrackingService;

    @Mock
    private AgentExecutionService executionService;

    private AgentRuntimeService service() {
        return new AgentRuntimeService(modelClient, toolDispatcher, tokenTrackingService,
                executionService, new PiiMaskingService(), JsonMapper.builder().build());
    }

    private static AgentDefinition agent(List<String> tools) {
        return AgentDefinition.create(TENANT, "Bot", "d", "You are a bot.", "claude-sonnet-4-6",
                2048, tools, null, true, USER);
    }

    private static AgentTurn finalTurn(String text, int in, int out) {
        return new AgentTurn(text, List.of(), "end_turn", in, out);
    }

    private static AgentTurn toolTurn(String tool, int in, int out) {
        return new AgentTurn("", List.of(new AgentToolCall("t1", tool, Map.of("q", "x"))),
                "tool_use", in, out);
    }

    private AgentExecution recordedExecution() {
        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(executionService).record(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("single turn, no tools: returns text, records usage once, dispatches nothing")
    void singleTurnNoTools() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(false);
        when(modelClient.nextTurn(any())).thenReturn(finalTurn("Hello", 5, 7));

        AgentRunResult result = service().run(TENANT, USER, agent(List.of("search")), "hi");

        assertThat(result.finalText()).isEqualTo("Hello");
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.inputTokens()).isEqualTo(5);
        assertThat(result.outputTokens()).isEqualTo(7);
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.budgetExceeded()).isFalse();
        assertThat(result.maxIterationsReached()).isFalse();
        verify(tokenTrackingService).recordUsage(TENANT, 5, 7);
        verifyNoInteractions(toolDispatcher);

        AgentExecution audit = recordedExecution();
        assertThat(audit.status()).isEqualTo("completed");
        assertThat(audit.inputTokens()).isEqualTo(5);
        assertThat(audit.finalText()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("tool turn then final: dispatches the tool, feeds result back, returns final text")
    void toolThenFinal() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(false);
        when(modelClient.nextTurn(any()))
                .thenReturn(toolTurn("search", 10, 5))
                .thenReturn(finalTurn("Done", 8, 4));
        when(toolDispatcher.dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any()))
                .thenReturn(DispatchResult.readResult("t1", "search", "{\"r\":1}", false));

        AgentRunResult result = service().run(TENANT, USER, agent(List.of("search")), "go");

        assertThat(result.finalText()).isEqualTo("Done");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.inputTokens()).isEqualTo(18);
        assertThat(result.outputTokens()).isEqualTo(9);
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).name()).isEqualTo("search");
        assertThat(result.toolCalls().get(0).isError()).isFalse();
        assertThat(result.toolCalls().get(0).permitted()).isTrue();
        verify(toolDispatcher, times(1)).dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any());
        verify(tokenTrackingService).recordUsage(TENANT, 10, 5);
        verify(tokenTrackingService).recordUsage(TENANT, 8, 4);
    }

    @Test
    @DisplayName("PII in a tool result is masked in the trace (and what is fed back to the model)")
    void masksPiiInToolResult() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(false);
        when(modelClient.nextTurn(any()))
                .thenReturn(toolTurn("search", 10, 5))
                .thenReturn(finalTurn("ok", 2, 1));
        when(toolDispatcher.dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any()))
                .thenReturn(DispatchResult.readResult("t1", "search",
                        "{\"email\":\"jane.doe@example.com\"}", false));

        AgentRunResult result = service().run(TENANT, USER, agent(List.of("search")), "go");

        String traced = result.toolCalls().get(0).resultJson();
        assertThat(traced).contains("[REDACTED_EMAIL]").doesNotContain("jane.doe@example.com");
    }

    @Test
    @DisplayName("non-permitted tool is never dispatched; an error result is fed back instead")
    void disallowedToolDefense() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(false);
        when(modelClient.nextTurn(any()))
                .thenReturn(toolTurn("delete_record", 10, 5))   // not in allowed set
                .thenReturn(finalTurn("Stopped", 4, 2));

        AgentRunResult result = service().run(TENANT, USER, agent(List.of("search")), "go");

        assertThat(result.finalText()).isEqualTo("Stopped");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).permitted()).isFalse();
        assertThat(result.toolCalls().get(0).isError()).isTrue();
        assertThat(result.toolCalls().get(0).resultJson()).contains("tool_not_permitted");
        verifyNoInteractions(toolDispatcher);
    }

    @Test
    @DisplayName("loops to the iteration cap when the model never stops requesting tools")
    void maxIterations() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(false);
        when(modelClient.nextTurn(any())).thenReturn(toolTurn("search", 1, 1));
        when(toolDispatcher.dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any()))
                .thenReturn(DispatchResult.readResult("t1", "search", "{}", false));

        AgentRunResult result = service().run(TENANT, USER, agent(List.of("search")), "go");

        assertThat(result.iterations()).isEqualTo(AgentRuntimeService.MAX_ITERATIONS);
        assertThat(result.maxIterationsReached()).isTrue();
        assertThat(result.budgetExceeded()).isFalse();
        verify(toolDispatcher, times(AgentRuntimeService.MAX_ITERATIONS))
                .dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any());
        assertThat(recordedExecution().status()).isEqualTo("max_iterations");
    }

    @Test
    @DisplayName("halts when the per-run token budget is exceeded")
    void budgetExceeded() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(false);
        when(modelClient.nextTurn(any())).thenReturn(toolTurn("search", 60_000, 60_000));
        when(toolDispatcher.dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any()))
                .thenReturn(DispatchResult.readResult("t1", "search", "{}", false));

        AgentRunResult result = service().run(TENANT, USER, agent(List.of("search")), "go");

        assertThat(result.budgetExceeded()).isTrue();
        assertThat(result.maxIterationsReached()).isFalse();
        assertThat(result.iterations()).isEqualTo(1);
        verify(toolDispatcher, times(1)).dispatch(eq(TENANT), eq(USER), eq("t1"), eq("search"), any());
        assertThat(recordedExecution().status()).isEqualTo("budget_exceeded");
    }

    @Test
    @DisplayName("disabled agent is refused before any model call")
    void disabledAgent() {
        AgentDefinition disabled = AgentDefinition.create(TENANT, "Off", "d", "p", null, null,
                List.of(), null, false, USER);

        assertThatThrownBy(() -> service().run(TENANT, USER, disabled, "go"))
                .isInstanceOf(AgentExecutionException.class)
                .satisfies(e -> assertThat(((AgentExecutionException) e).reason())
                        .isEqualTo(AgentExecutionException.Reason.AGENT_DISABLED));
        verifyNoInteractions(modelClient);
        assertThat(recordedExecution().status()).isEqualTo("refused_disabled");
    }

    @Test
    @DisplayName("exhausted monthly token limit is refused before any model call")
    void tokenLimitExceeded() {
        when(tokenTrackingService.isTokenLimitExceeded(TENANT)).thenReturn(true);

        assertThatThrownBy(() -> service().run(TENANT, USER, agent(List.of("search")), "go"))
                .isInstanceOf(AgentExecutionException.class)
                .satisfies(e -> assertThat(((AgentExecutionException) e).reason())
                        .isEqualTo(AgentExecutionException.Reason.TOKEN_LIMIT_EXCEEDED));
        verifyNoInteractions(modelClient);
        verify(tokenTrackingService, never()).recordUsage(anyString(), anyInt(), anyInt());
        assertThat(recordedExecution().status()).isEqualTo("refused_token_limit");
    }
}
