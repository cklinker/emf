package io.kelta.gateway.websocket;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionManager Tests")
class SubscriptionManagerTest {

    private SubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SubscriptionManager();
    }

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(id);
        return session;
    }

    @Nested
    @DisplayName("Subscribe/Unsubscribe")
    class SubscribeUnsubscribe {
        @Test
        void shouldSubscribeAndRouteCorrectly() {
            WebSocketSession session = mockSession("s1");
            manager.registerConnection(session, "t1");

            assertThat(manager.subscribe(session, "t1", "contacts")).isTrue();

            Set<WebSocketSession> subscribers = manager.getSubscribers("t1", "contacts");
            assertThat(subscribers).containsExactly(session);
        }

        @Test
        void shouldUnsubscribeCorrectly() {
            WebSocketSession session = mockSession("s1");
            manager.registerConnection(session, "t1");
            manager.subscribe(session, "t1", "contacts");

            manager.unsubscribe(session, "t1", "contacts");

            assertThat(manager.getSubscribers("t1", "contacts")).isEmpty();
        }

        @Test
        void shouldIsolateTenants() {
            WebSocketSession s1 = mockSession("s1");
            WebSocketSession s2 = mockSession("s2");
            manager.registerConnection(s1, "t1");
            manager.registerConnection(s2, "t2");
            manager.subscribe(s1, "t1", "contacts");
            manager.subscribe(s2, "t2", "contacts");

            assertThat(manager.getSubscribers("t1", "contacts")).containsExactly(s1);
            assertThat(manager.getSubscribers("t2", "contacts")).containsExactly(s2);
        }

        @Test
        void shouldReturnEmptyForNoSubscribers() {
            assertThat(manager.getSubscribers("t1", "contacts")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Session Cleanup")
    class SessionCleanup {
        @Test
        void shouldCleanAllSubscriptionsOnRemove() {
            WebSocketSession session = mockSession("s1");
            manager.registerConnection(session, "t1");
            manager.subscribe(session, "t1", "contacts");
            manager.subscribe(session, "t1", "accounts");

            manager.removeSession(session);

            assertThat(manager.getSubscribers("t1", "contacts")).isEmpty();
            assertThat(manager.getSubscribers("t1", "accounts")).isEmpty();
            assertThat(manager.getSubscriptionCount("s1")).isEqualTo(0);
            assertThat(manager.getConnectionCount("t1")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Limits")
    class Limits {
        @Test
        void shouldEnforceSubscriptionLimit() {
            WebSocketSession session = mockSession("s1");
            manager.registerConnection(session, "t1");

            for (int i = 0; i < SubscriptionManager.MAX_SUBSCRIPTIONS_PER_SESSION; i++) {
                assertThat(manager.subscribe(session, "t1", "col-" + i)).isTrue();
            }

            // 51st subscription should fail
            assertThat(manager.subscribe(session, "t1", "col-overflow")).isFalse();
        }

        @Test
        void shouldEnforceConnectionLimit() {
            for (int i = 0; i < SubscriptionManager.MAX_CONNECTIONS_PER_TENANT; i++) {
                WebSocketSession s = mockSession("s" + i);
                assertThat(manager.registerConnection(s, "t1")).isTrue();
            }

            // 101st connection should fail
            WebSocketSession overflow = mockSession("s-overflow");
            assertThat(manager.registerConnection(overflow, "t1")).isFalse();
        }
    }

    @Nested
    @DisplayName("Multiple Sessions")
    class MultipleSessions {
        @Test
        void shouldSupportMultipleSessionsPerCollection() {
            WebSocketSession s1 = mockSession("s1");
            WebSocketSession s2 = mockSession("s2");
            manager.registerConnection(s1, "t1");
            manager.registerConnection(s2, "t1");
            manager.subscribe(s1, "t1", "contacts");
            manager.subscribe(s2, "t1", "contacts");

            Set<WebSocketSession> subscribers = manager.getSubscribers("t1", "contacts");
            assertThat(subscribers).containsExactlyInAnyOrder(s1, s2);
        }

        @Test
        void shouldRemoveOnlyTargetSession() {
            WebSocketSession s1 = mockSession("s1");
            WebSocketSession s2 = mockSession("s2");
            manager.registerConnection(s1, "t1");
            manager.registerConnection(s2, "t1");
            manager.subscribe(s1, "t1", "contacts");
            manager.subscribe(s2, "t1", "contacts");

            manager.removeSession(s1);

            assertThat(manager.getSubscribers("t1", "contacts")).containsExactly(s2);
        }
    }
}
