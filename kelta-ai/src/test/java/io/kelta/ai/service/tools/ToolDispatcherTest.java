package io.kelta.ai.service.tools;

import io.kelta.ai.model.AiProposal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolDispatcher")
class ToolDispatcherTest {

    @Mock
    private ToolRegistry registry;

    @Mock
    private ReadToolHandler readHandler;

    @Mock
    private ProposeToolHandler proposeHandler;

    private ToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ToolDispatcher(registry, new ObjectMapper());
    }

    @Test
    @DisplayName("returns JSON tool_result for a successful read tool")
    void readToolSuccess() {
        when(registry.handler("get_x")).thenReturn(Optional.of(readHandler));
        when(readHandler.execute("t", "u", Map.of("a", 1))).thenReturn(Map.of("ok", true));

        DispatchResult result = dispatcher.dispatch("t", "u", "toolu_1", "get_x", Map.of("a", 1));

        assertThat(result.proposal()).isNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.resultJson()).contains("\"ok\":true");
        assertThat(result.toolUseId()).isEqualTo("toolu_1");
        assertThat(result.toolName()).isEqualTo("get_x");
    }

    @Test
    @DisplayName("read tool exception becomes isError tool_result")
    void readToolFailure() {
        when(registry.handler("get_x")).thenReturn(Optional.of(readHandler));
        when(readHandler.execute("t", "u", Map.of())).thenThrow(new RuntimeException("boom"));

        DispatchResult result = dispatcher.dispatch("t", "u", "toolu_1", "get_x", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.resultJson()).contains("\"message\":\"boom\"");
    }

    @Test
    @DisplayName("invalid input becomes invalid_input error")
    void invalidInput() {
        when(registry.handler("get_x")).thenReturn(Optional.of(readHandler));
        when(readHandler.execute("t", "u", Map.of()))
                .thenThrow(new IllegalArgumentException("collectionName is required"));

        DispatchResult result = dispatcher.dispatch("t", "u", "toolu_1", "get_x", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.resultJson()).contains("invalid_input");
    }

    @Test
    @DisplayName("propose tool returns proposal queued ack with proposal attached")
    void proposeToolSuccess() {
        when(registry.handler("propose_x")).thenReturn(Optional.of(proposeHandler));
        AiProposal proposal = AiProposal.pending("fake", Map.of("a", 1));
        when(proposeHandler.buildProposal(Map.of("a", 1))).thenReturn(proposal);

        DispatchResult result = dispatcher.dispatch("t", "u", "toolu_2", "propose_x", Map.of("a", 1));

        assertThat(result.proposal()).isEqualTo(proposal);
        assertThat(result.isError()).isFalse();
        assertThat(result.resultJson()).contains("queued");
        assertThat(result.resultJson()).contains(proposal.id().toString());
    }

    @Test
    @DisplayName("propose tool rejection becomes invalid_input error without proposal")
    void proposeToolRejection() {
        when(registry.handler("propose_x")).thenReturn(Optional.of(proposeHandler));
        when(proposeHandler.buildProposal(Map.of()))
                .thenThrow(new IllegalArgumentException("Type changes are not supported"));

        DispatchResult result = dispatcher.dispatch("t", "u", "toolu_2", "propose_x", Map.of());

        assertThat(result.proposal()).isNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.resultJson()).contains("Type changes are not supported");
    }

    @Test
    @DisplayName("unknown tool throws IllegalArgumentException")
    void unknownTool() {
        when(registry.handler("missing")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                dispatcher.dispatch("t", "u", "toolu_3", "missing", Map.of()));
    }

    @SuppressWarnings("unused")
    private static List<String> reserved() { return List.of(); }
}
