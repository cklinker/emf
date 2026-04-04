package io.kelta.ai.controller;

import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController")
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private AiConfigProperties config;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        config = new AiConfigProperties(
                new AiConfigProperties.AnthropicProperties("test-key", "claude-sonnet-4-20250514", 4096, 0.7),
                "http://localhost:8080",
                30000L
        );
        controller = new ChatController(chatService, config);
    }

    @Nested
    @DisplayName("chat")
    class Chat {

        @Test
        @DisplayName("sends chat message and returns result")
        void sendsChatMessage() {
            UUID convId = UUID.randomUUID();
            Map<String, Object> body = Map.of(
                    "message", "Create a customers collection",
                    "conversationId", convId.toString()
            );

            Map<String, Object> expected = Map.of(
                    "conversationId", convId.toString(),
                    "content", "I'll create a customers collection for you.",
                    "tokensUsed", 150
            );

            when(chatService.chat(eq("tenant-1"), eq("user-1"), eq(convId),
                    eq("Create a customers collection"), isNull(), isNull()))
                    .thenReturn(expected);

            ResponseEntity<Map<String, Object>> response =
                    controller.chat("tenant-1", "user-1", body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("content")).isEqualTo("I'll create a customers collection for you.");
        }

        @Test
        @DisplayName("handles chat without conversationId")
        void handlesNewConversation() {
            Map<String, Object> body = Map.of("message", "Hello");

            when(chatService.chat(eq("tenant-1"), eq("user-1"), isNull(),
                    eq("Hello"), isNull(), isNull()))
                    .thenReturn(Map.of("content", "Hi there!"));

            ResponseEntity<Map<String, Object>> response =
                    controller.chat("tenant-1", "user-1", body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("passes context type and id when provided")
        void passesContextInfo() {
            Map<String, Object> body = Map.of(
                    "message", "Add a field",
                    "contextType", "collection",
                    "contextId", "accounts"
            );

            when(chatService.chat(eq("tenant-1"), eq("user-1"), isNull(),
                    eq("Add a field"), eq("collection"), eq("accounts")))
                    .thenReturn(Map.of("content", "Done"));

            controller.chat("tenant-1", "user-1", body);

            verify(chatService).chat("tenant-1", "user-1", null,
                    "Add a field", "collection", "accounts");
        }
    }

    @Nested
    @DisplayName("chatStream")
    class ChatStream {

        @Test
        @DisplayName("returns SSE emitter with configured timeout")
        void returnsSseEmitter() {
            Map<String, Object> body = Map.of("message", "Hello");

            SseEmitter emitter = controller.chatStream("tenant-1", "user-1", body);

            assertThat(emitter).isNotNull();
            assertThat(emitter.getTimeout()).isEqualTo(30000L);
        }
    }
}
