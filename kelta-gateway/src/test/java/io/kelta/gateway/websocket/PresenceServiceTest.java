package io.kelta.gateway.websocket;

import io.kelta.runtime.messaging.nats.NatsConnectionManager;
import io.nats.client.JetStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

@DisplayName("PresenceService")
class PresenceServiceTest {

    private JetStream jetStream;
    private PresenceService service;

    @BeforeEach
    void setUp() throws Exception {
        NatsConnectionManager connectionManager = mock(NatsConnectionManager.class);
        jetStream = mock(JetStream.class);
        lenient().when(connectionManager.jetStream()).thenReturn(jetStream);
        service = new PresenceService(connectionManager);
    }

    /** A session whose outbound messages are captured into `sent`. */
    private WebSocketSession mockSession(String id, String userId, String email, List<String> sent) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put("tenantId", "t1");
        attributes.put("userId", userId);
        if (email != null) attributes.put("userEmail", email);
        Sinks.Many<WebSocketMessage> outbound = Sinks.many().unicast().onBackpressureBuffer();
        attributes.put("outbound", outbound);
        outbound.asFlux().subscribe(msg -> sent.add(msg.getPayloadAsText()));
        lenient().when(session.getId()).thenReturn(id);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(session.textMessage(anyString())).thenAnswer(inv -> {
            WebSocketMessage message = mock(WebSocketMessage.class);
            lenient().when(message.getPayloadAsText()).thenReturn(inv.getArgument(0));
            return message;
        });
        return session;
    }

    @Test
    void joinBroadcastsASnapshotIncludingTheJoiner() {
        List<String> sent = new ArrayList<>();
        WebSocketSession alice = mockSession("s1", "u-alice", "alice@example.com", sent);

        assertThat(service.join(alice, "t1", "record:orders/1")).isTrue();

        verify(jetStream, atLeastOnce()).publishAsync(anyString(), any(byte[].class));
        assertThat(sent).anySatisfy(msg -> {
            assertThat(msg).contains("presence.changed");
            assertThat(msg).contains("record:orders/1");
            assertThat(msg).contains("alice@example.com");
        });
    }

    @Test
    void remoteDeltasMergeIntoLocalSnapshots() {
        List<String> sent = new ArrayList<>();
        WebSocketSession alice = mockSession("s1", "u-alice", "alice@example.com", sent);
        service.join(alice, "t1", "record:orders/1");
        sent.clear();

        // A join from another pod arrives over NATS.
        service.onPresenceEvent(
                "{\"tenantId\":\"t1\",\"type\":\"join\",\"resource\":\"record:orders/1\"," +
                "\"user\":{\"id\":\"u-bob\",\"email\":\"bob@example.com\"}}");

        assertThat(sent).anySatisfy(msg -> {
            assertThat(msg).contains("bob@example.com");
            assertThat(msg).contains("alice@example.com");
        });

        sent.clear();
        // Bob leaves remotely.
        service.onPresenceEvent(
                "{\"tenantId\":\"t1\",\"type\":\"leave\",\"resource\":\"record:orders/1\"," +
                "\"user\":{\"id\":\"u-bob\"}}");
        assertThat(sent).anySatisfy(msg -> assertThat(msg).doesNotContain("bob@example.com"));
    }

    @Test
    void heartbeatOfAKnownUserDoesNotRebroadcast() {
        List<String> sent = new ArrayList<>();
        WebSocketSession alice = mockSession("s1", "u-alice", null, sent);
        service.join(alice, "t1", "record:orders/1");
        sent.clear();

        service.onPresenceEvent(
                "{\"tenantId\":\"t1\",\"type\":\"heartbeat\",\"resource\":\"record:orders/1\"," +
                "\"user\":{\"id\":\"u-alice\"}}");

        assertThat(sent).isEmpty();
    }

    @Test
    void disconnectLeavesEverythingAndNotifiesCoPresentSessions() {
        List<String> aliceSent = new ArrayList<>();
        List<String> bobSent = new ArrayList<>();
        WebSocketSession alice = mockSession("s1", "u-alice", "alice@example.com", aliceSent);
        WebSocketSession bob = mockSession("s2", "u-bob", "bob@example.com", bobSent);
        service.join(alice, "t1", "record:orders/1");
        service.join(bob, "t1", "record:orders/1");
        bobSent.clear();

        service.removeSession(alice);

        assertThat(bobSent).anySatisfy(msg -> {
            assertThat(msg).contains("presence.changed");
            assertThat(msg).doesNotContain("alice@example.com");
        });
    }

    @Test
    void perSessionResourceCapIsEnforced() {
        List<String> sent = new ArrayList<>();
        WebSocketSession alice = mockSession("s1", "u-alice", null, sent);
        for (int i = 0; i < PresenceService.MAX_PRESENCE_PER_SESSION; i++) {
            assertThat(service.join(alice, "t1", "record:orders/" + i)).isTrue();
        }
        assertThat(service.join(alice, "t1", "record:orders/overflow")).isFalse();
        // Re-joining an already-joined resource is not capped.
        assertThat(service.join(alice, "t1", "record:orders/0")).isTrue();
    }

    @Test
    void malformedNatsEventIsIgnored() {
        service.onPresenceEvent("not-json");
        service.onPresenceEvent("{\"tenantId\":\"t1\"}");
        // no exception, nothing published beyond joins
    }
}
