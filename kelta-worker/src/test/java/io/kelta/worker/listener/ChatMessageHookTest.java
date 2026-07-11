package io.kelta.worker.listener;

import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ChatMessageHook")
class ChatMessageHookTest {

    private PlatformEventPublisher eventPublisher;
    private JdbcTemplate jdbcTemplate;
    private ChatMessageHook hook;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(PlatformEventPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        hook = new ChatMessageHook(eventPublisher, jdbcTemplate);
    }

    private void stubConversationStatus(String status) {
        when(jdbcTemplate.query(contains("SELECT status FROM chat_conversation"),
                any(RowMapper.class), eq("conv-1")))
                .thenReturn(status == null ? List.of() : List.of(status));
    }

    private void stubParticipant(boolean member) {
        when(jdbcTemplate.queryForObject(contains("chat_participant"), eq(Integer.class),
                eq("conv-1"), eq("u1"))).thenReturn(member ? 1 : 0);
    }

    @Test
    @DisplayName("beforeCreate rejects messages into CLOSED conversations")
    void rejectsClosedConversation() {
        stubConversationStatus("CLOSED");
        BeforeSaveResult result = hook.beforeCreate(
                Map.of("conversationId", "conv-1", "senderId", "u1", "senderType", "INTERNAL"), "t1");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("beforeCreate rejects non-participant senders")
    void rejectsNonParticipant() {
        stubConversationStatus("OPEN");
        stubParticipant(false);
        BeforeSaveResult result = hook.beforeCreate(
                Map.of("conversationId", "conv-1", "senderId", "u1", "senderType", "INTERNAL"), "t1");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("beforeCreate accepts participant senders and SYSTEM messages")
    void acceptsParticipantAndSystem() {
        stubConversationStatus("OPEN");
        stubParticipant(true);
        assertThat(hook.beforeCreate(
                Map.of("conversationId", "conv-1", "senderId", "u1", "senderType", "INTERNAL"),
                "t1").isSuccess()).isTrue();
        assertThat(hook.beforeCreate(
                Map.of("conversationId", "conv-1", "senderType", "SYSTEM"),
                "t1").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("messages are immutable")
    void updatesRejected() {
        assertThat(hook.beforeUpdate("m1", Map.of("body", "edited"), Map.of(), "t1")
                .isSuccess()).isFalse();
    }

    @Test
    @DisplayName("afterCreate bumps last_message_at and publishes ids-only event on the tenant/conversation subject")
    void afterCreatePublishes() {
        hook.afterCreate(Map.of(
                "id", "msg-1", "conversationId", "conv-1",
                "senderId", "u1", "senderType", "PORTAL", "kind", "TEXT",
                "body", "SECRET BODY"), "t1");

        verify(jdbcTemplate).update(contains("last_message_at"), any(), eq("conv-1"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PlatformEvent> event = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(eventPublisher).publish(subject.capture(), event.capture());
        assertThat(subject.getValue()).isEqualTo("kelta.chat.message.t1.conv-1");
        // The payload carries ids/metadata only — the body field does not exist on it.
        io.kelta.runtime.event.ChatMessagePayload payload =
                (io.kelta.runtime.event.ChatMessagePayload) event.getValue().getPayload();
        assertThat(payload.getMessageId()).isEqualTo("msg-1");
        assertThat(payload.getConversationId()).isEqualTo("conv-1");
        assertThat(payload.getSenderType()).isEqualTo("PORTAL");
        assertThat(payload.getKind()).isEqualTo("TEXT");
    }
}
