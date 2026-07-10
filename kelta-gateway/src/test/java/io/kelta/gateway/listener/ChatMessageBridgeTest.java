package io.kelta.gateway.listener;

import io.kelta.gateway.websocket.RealtimeWebSocketHandler;
import io.kelta.gateway.websocket.SubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ChatMessageBridge")
class ChatMessageBridgeTest {

    private SubscriptionManager subscriptionManager;
    private RealtimeWebSocketHandler handler;
    private ChatMessageBridge bridge;

    @BeforeEach
    void setUp() {
        subscriptionManager = mock(SubscriptionManager.class);
        handler = mock(RealtimeWebSocketHandler.class);
        bridge = new ChatMessageBridge(subscriptionManager, handler);
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    @DisplayName("fans chat.message events (ids only) to conversation-joined sessions")
    void fansMessageToJoinedSessions() {
        WebSocketSession joined = openSession();
        when(subscriptionManager.getConversationSubscribers("t1", "conv-1"))
                .thenReturn(Set.of(joined));

        bridge.onChatMessage("""
                {"tenantId":"t1","timestamp":"2026-07-10T12:00:00Z",
                 "payload":{"conversationId":"conv-1","messageId":"m1",
                            "senderId":"u1","senderType":"PORTAL","kind":"TEXT"}}
                """);

        ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
        verify(handler).sendMessage(eq(joined), event.capture());
        assertThat(event.getValue())
                .containsEntry("event", "chat.message")
                .containsEntry("conversationId", "conv-1")
                .containsEntry("messageId", "m1")
                .doesNotContainKey("body")
                .doesNotContainKey("data");
    }

    @Test
    @DisplayName("no joined sessions → no fanout; tenant subscribers of collections are never used")
    void noJoinedSessionsNoFanout() {
        when(subscriptionManager.getConversationSubscribers("t1", "conv-1")).thenReturn(Set.of());

        bridge.onChatMessage("""
                {"tenantId":"t1","payload":{"conversationId":"conv-1","messageId":"m1"}}
                """);

        verify(handler, never()).sendMessage(any(), any());
        verify(subscriptionManager, never()).getSubscribers(any(), any());
    }

    @Test
    @DisplayName("conversation lifecycle events fan out with status")
    void conversationEvents() {
        WebSocketSession joined = openSession();
        when(subscriptionManager.getConversationSubscribers("t1", "conv-1"))
                .thenReturn(Set.of(joined));

        bridge.onConversationChanged("""
                {"tenantId":"t1","payload":{"conversationId":"conv-1","status":"ASSIGNED",
                 "assignedTo":"u-agent"}}
                """);

        ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
        verify(handler).sendMessage(eq(joined), event.capture());
        assertThat(event.getValue())
                .containsEntry("event", "chat.conversation")
                .containsEntry("status", "ASSIGNED");
    }

    @Test
    @DisplayName("malformed events are swallowed without fanout")
    void malformedSwallowed() {
        bridge.onChatMessage("not json");
        bridge.onConversationChanged("{\"tenantId\":\"t1\"}");
        verify(handler, never()).sendMessage(any(), any());
    }
}
