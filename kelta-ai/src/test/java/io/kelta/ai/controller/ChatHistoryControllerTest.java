package io.kelta.ai.controller;

import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.model.Conversation;
import io.kelta.ai.repository.ChatMessageRepository;
import io.kelta.ai.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatHistoryController")
class ChatHistoryControllerTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    private ChatHistoryController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatHistoryController(conversationRepository, messageRepository);
    }

    @Nested
    @DisplayName("listConversations")
    class ListConversations {

        @Test
        @DisplayName("returns list of conversations for user")
        void returnsConversationList() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            Conversation conv = new Conversation(id, "tenant-1", "user-1", "Test Chat", now, now);

            when(conversationRepository.findByUser("tenant-1", "user-1", 50))
                    .thenReturn(List.of(conv));

            ResponseEntity<Map<String, Object>> response =
                    controller.listConversations("tenant-1", "user-1", 50);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            assertThat(data).hasSize(1);
            assertThat(data.getFirst().get("id")).isEqualTo(id.toString());
            assertThat(data.getFirst().get("title")).isEqualTo("Test Chat");
        }

        @Test
        @DisplayName("returns empty list when no conversations exist")
        void returnsEmptyList() {
            when(conversationRepository.findByUser("tenant-1", "user-1", 50))
                    .thenReturn(List.of());

            ResponseEntity<Map<String, Object>> response =
                    controller.listConversations("tenant-1", "user-1", 50);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            assertThat(data).isEmpty();
        }
    }

    @Nested
    @DisplayName("getConversation")
    class GetConversation {

        @Test
        @DisplayName("returns conversation with messages")
        void returnsConversationWithMessages() {
            UUID convId = UUID.randomUUID();
            Instant now = Instant.now();
            Conversation conv = new Conversation(convId, "tenant-1", "user-1", "Test", now, now);

            ChatMessage msg = new ChatMessage(UUID.randomUUID(), "tenant-1", convId,
                    "user", "Hello", null, 0, 0, now);

            when(conversationRepository.findById(convId, "tenant-1")).thenReturn(Optional.of(conv));
            when(messageRepository.findByConversation(convId, "tenant-1")).thenReturn(List.of(msg));

            ResponseEntity<Map<String, Object>> response =
                    controller.getConversation("tenant-1", convId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("title")).isEqualTo("Test");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
            assertThat(messages).hasSize(1);
            assertThat(messages.getFirst().get("role")).isEqualTo("user");
            assertThat(messages.getFirst().get("content")).isEqualTo("Hello");
        }

        @Test
        @DisplayName("returns 404 when conversation not found")
        void returns404WhenNotFound() {
            UUID id = UUID.randomUUID();
            when(conversationRepository.findById(id, "tenant-1")).thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> response =
                    controller.getConversation("tenant-1", id);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
