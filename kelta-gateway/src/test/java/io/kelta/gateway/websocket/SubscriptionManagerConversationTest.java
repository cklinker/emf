package io.kelta.gateway.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SubscriptionManager conversation routing (chat)")
class SubscriptionManagerConversationTest {

    private SubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SubscriptionManager();
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        manager.registerConnection(session, "t1");
        return session;
    }

    @Test
    @DisplayName("join routes the session; other conversations and tenants stay isolated")
    void joinRoutes() {
        WebSocketSession a = session("a");
        WebSocketSession b = session("b");

        assertThat(manager.joinConversation(a, "t1", "conv-1")).isTrue();
        assertThat(manager.joinConversation(b, "t1", "conv-2")).isTrue();

        assertThat(manager.getConversationSubscribers("t1", "conv-1")).containsExactly(a);
        assertThat(manager.getConversationSubscribers("t1", "conv-2")).containsExactly(b);
        assertThat(manager.getConversationSubscribers("t2", "conv-1")).isEmpty();
    }

    @Test
    @DisplayName("leave and disconnect both clean the conversation index")
    void leaveAndDisconnectClean() {
        WebSocketSession a = session("a");
        manager.joinConversation(a, "t1", "conv-1");
        manager.joinConversation(a, "t1", "conv-2");

        manager.leaveConversation(a, "t1", "conv-1");
        assertThat(manager.getConversationSubscribers("t1", "conv-1")).isEmpty();
        assertThat(manager.getConversationSubscribers("t1", "conv-2")).containsExactly(a);

        manager.removeSession(a);
        assertThat(manager.getConversationSubscribers("t1", "conv-2")).isEmpty();
    }

    @Test
    @DisplayName("per-session conversation cap is enforced independently of collection subs")
    void capEnforced() {
        WebSocketSession a = session("a");
        for (int i = 0; i < SubscriptionManager.MAX_CONVERSATIONS_PER_SESSION; i++) {
            assertThat(manager.joinConversation(a, "t1", "conv-" + i)).isTrue();
        }
        assertThat(manager.joinConversation(a, "t1", "conv-overflow")).isFalse();
        // Collection subscriptions still work — separate budget.
        assertThat(manager.subscribe(a, "t1", "contacts")).isTrue();
    }

    @Test
    @DisplayName("unregistered sessions cannot join")
    void unregisteredDenied() {
        WebSocketSession ghost = mock(WebSocketSession.class);
        when(ghost.getId()).thenReturn("ghost");
        assertThat(manager.joinConversation(ghost, "t1", "conv-1")).isFalse();
    }
}
