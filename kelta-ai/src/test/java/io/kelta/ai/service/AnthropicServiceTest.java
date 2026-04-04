package io.kelta.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.anthropic.services.blocking.MessageService;
import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.repository.AiConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnthropicService")
class AnthropicServiceTest {

    @Mock
    private AnthropicClient client;

    @Mock
    private MessageService messageService;

    @Mock
    private AiConfigRepository aiConfigRepository;

    private AnthropicService service;

    @BeforeEach
    void setUp() {
        AiConfigProperties config = new AiConfigProperties(
                new AiConfigProperties.AnthropicProperties("test-key", "claude-sonnet-4-20250514", 4096, 0.7),
                "http://localhost:8080", 30000L);
        service = new AnthropicService(client, config, aiConfigRepository);
    }

    private MessageParam userMessage(String text) {
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(text)
                .build();
    }

    @Nested
    @DisplayName("buildRequest")
    class BuildRequest {

        @Test
        @DisplayName("builds request with tenant-specific model when configured")
        void usesTenantModel() {
            when(aiConfigRepository.getConfig("tenant-1", "anthropic.model"))
                    .thenReturn(Optional.of("claude-opus-4-20250514"));
            when(aiConfigRepository.getConfig("tenant-1", "anthropic.maxTokens"))
                    .thenReturn(Optional.of("8192"));

            List<MessageParam> messages = List.of(userMessage("Hello"));

            MessageCreateParams.Builder builder = service.buildRequest("tenant-1", "You are helpful.", messages);
            MessageCreateParams params = builder.build();

            assertThat(params.model().toString()).contains("claude-opus-4-20250514");
            assertThat(params.maxTokens()).isEqualTo(8192L);
        }

        @Test
        @DisplayName("falls back to global config then defaults")
        void fallsBackToDefaults() {
            when(aiConfigRepository.getConfig("tenant-1", "anthropic.model"))
                    .thenReturn(Optional.empty());
            when(aiConfigRepository.getConfig("0", "anthropic.model"))
                    .thenReturn(Optional.empty());
            when(aiConfigRepository.getConfig("tenant-1", "anthropic.maxTokens"))
                    .thenReturn(Optional.empty());
            when(aiConfigRepository.getConfig("0", "anthropic.maxTokens"))
                    .thenReturn(Optional.empty());

            List<MessageParam> messages = List.of(userMessage("Hello"));

            MessageCreateParams.Builder builder = service.buildRequest("tenant-1", "System", messages);
            MessageCreateParams params = builder.build();

            assertThat(params.model().toString()).contains("claude-sonnet-4-20250514");
            assertThat(params.maxTokens()).isEqualTo(4096L);
        }

        @Test
        @DisplayName("includes tool definitions in request")
        void includesToolDefinitions() {
            when(aiConfigRepository.getConfig("tenant-1", "anthropic.model"))
                    .thenReturn(Optional.empty());
            when(aiConfigRepository.getConfig("0", "anthropic.model"))
                    .thenReturn(Optional.empty());
            when(aiConfigRepository.getConfig("tenant-1", "anthropic.maxTokens"))
                    .thenReturn(Optional.empty());
            when(aiConfigRepository.getConfig("0", "anthropic.maxTokens"))
                    .thenReturn(Optional.empty());

            MessageCreateParams params = service.buildRequest(
                    "tenant-1", "System", List.of(userMessage("Hello"))
            ).build();

            assertThat(params.tools()).isPresent();
            assertThat(params.tools().get()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessage {

        @Test
        @DisplayName("delegates to Anthropic client")
        void delegatesToClient() {
            when(client.messages()).thenReturn(messageService);
            when(messageService.create(any(MessageCreateParams.class))).thenReturn(mock(Message.class));

            MessageCreateParams params = mock(MessageCreateParams.class);
            when(params.model()).thenReturn(Model.CLAUDE_SONNET_4_20250514);

            service.sendMessage(params);

            verify(messageService).create(params);
        }
    }

    @Nested
    @DisplayName("streamMessage")
    class StreamMessage {

        @Test
        @DisplayName("delegates to Anthropic client streaming")
        void delegatesToClientStreaming() {
            when(client.messages()).thenReturn(messageService);

            MessageCreateParams params = mock(MessageCreateParams.class);
            when(params.model()).thenReturn(Model.CLAUDE_SONNET_4_20250514);

            service.streamMessage(params);

            verify(messageService).createStreaming(params);
        }
    }
}
