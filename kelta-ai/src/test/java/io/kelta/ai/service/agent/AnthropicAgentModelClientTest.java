package io.kelta.ai.service.agent;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.Usage;
import io.kelta.ai.service.AnthropicService;
import io.kelta.ai.service.tools.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnthropicAgentModelClient")
class AnthropicAgentModelClientTest {

    private static final String TENANT = "tenant-1";
    private static final String PROMPT = "You are a bot.";

    @Mock
    private AnthropicService anthropicService;

    @Mock
    private ToolRegistry toolRegistry;

    private AnthropicAgentModelClient client() {
        return new AnthropicAgentModelClient(anthropicService, toolRegistry, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("attaches the allowed tool subset + overrides, sends, and extracts usage/stop")
    void nextTurnBuildsRestrictedRequestAndExtracts() {
        List<String> allowed = List.of("search", "get_record");
        List<ToolUnion> tools = List.of();
        when(toolRegistry.toolDefinitions(allowed)).thenReturn(tools);

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model("claude-sonnet-4-6").maxTokens(2048L).system(PROMPT).addUserMessage("hi");
        when(anthropicService.buildRequest(eq(TENANT), eq(PROMPT), anyList(), eq(tools),
                eq("claude-sonnet-4-6"), eq(2048))).thenReturn(builder);

        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(5L);
        when(usage.outputTokens()).thenReturn(7L);
        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of());
        when(message.usage()).thenReturn(usage);
        when(message.stopReason()).thenReturn(Optional.empty());
        when(anthropicService.sendMessage(any())).thenReturn(message);

        AgentTurnRequest request = new AgentTurnRequest(TENANT, PROMPT, "claude-sonnet-4-6", 2048,
                allowed, List.of(AgentMessage.userText("hi")));

        AgentTurn turn = client().nextTurn(request);

        assertThat(turn.text()).isEmpty();
        assertThat(turn.toolCalls()).isEmpty();
        assertThat(turn.stopReason()).isNull();
        assertThat(turn.inputTokens()).isEqualTo(5);
        assertThat(turn.outputTokens()).isEqualTo(7);

        // The restricted tool subset was requested, and the single user message was converted.
        verify(toolRegistry).toolDefinitions(allowed);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MessageParam>> messages = ArgumentCaptor.forClass(List.class);
        verify(anthropicService).buildRequest(eq(TENANT), eq(PROMPT), messages.capture(), eq(tools),
                eq("claude-sonnet-4-6"), eq(2048));
        assertThat(messages.getValue()).hasSize(1);
    }
}
