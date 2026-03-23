package io.kelta.gateway.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.gateway.auth.DynamicReactiveJwtDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * WebSocket handler for realtime record change subscriptions.
 *
 * <p>Authenticates connections via JWT query parameter, processes subscribe/unsubscribe
 * messages, and delivers Kafka-bridged events to subscribed sessions.
 *
 * @since 1.0.0
 */
@Component
public class RealtimeWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "Token expired");

    private final SubscriptionManager subscriptionManager;
    private final DynamicReactiveJwtDecoder jwtDecoder;

    public RealtimeWebSocketHandler(SubscriptionManager subscriptionManager,
                                     DynamicReactiveJwtDecoder jwtDecoder) {
        this.subscriptionManager = subscriptionManager;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Extract JWT from query parameter
        String token = session.getHandshakeInfo().getUri().getQuery();
        if (token == null || !token.startsWith("token=")) {
            return session.close(CloseStatus.POLICY_VIOLATION);
        }
        String jwtToken = token.substring("token=".length());

        // Validate JWT (use default tenant for WebSocket — tenant from claims)
        return jwtDecoder.decode(jwtToken)
                .flatMap(jwt -> handleAuthenticated(session, jwt))
                .onErrorResume(e -> {
                    log.warn("WebSocket auth failed: {}", e.getMessage());
                    return session.close(CloseStatus.POLICY_VIOLATION);
                });
    }

    private Mono<Void> handleAuthenticated(WebSocketSession session, Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        String userId = jwt.getSubject();
        Instant expiresAt = jwt.getExpiresAt();

        if (tenantId == null || tenantId.isBlank()) {
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        // Check tenant connection limit
        if (!subscriptionManager.registerConnection(session, tenantId)) {
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        // Store context in session attributes
        session.getAttributes().put("tenantId", tenantId);
        session.getAttributes().put("userId", userId);

        log.info("WebSocket connected: session={} user={} tenant={}", session.getId(), userId, tenantId);
        securityLog.info("security_event=WEBSOCKET_CONNECTED user={} tenant={}", userId, tenantId);

        // Create outbound sink for sending messages
        Sinks.Many<WebSocketMessage> outbound = Sinks.many().multicast().onBackpressureBuffer();
        session.getAttributes().put("outbound", outbound);

        // Schedule token expiration check
        Mono<Void> expirationCheck = Mono.empty();
        if (expiresAt != null) {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (ttl.isPositive()) {
                expirationCheck = Mono.delay(ttl)
                        .then(session.close(TOKEN_EXPIRED))
                        .then();
            }
        }

        // Process inbound messages
        Mono<Void> inbound = session.receive()
                .doOnNext(msg -> handleMessage(session, msg, tenantId))
                .doOnComplete(() -> cleanup(session))
                .doOnError(e -> cleanup(session))
                .then();

        // Send outbound messages
        Mono<Void> outboundMono = session.send(outbound.asFlux().map(m -> m));

        return Mono.zip(inbound, outboundMono, expirationCheck.then(Mono.empty()))
                .then()
                .doFinally(signal -> cleanup(session));
    }

    private void handleMessage(WebSocketSession session, WebSocketMessage msg, String tenantId) {
        if (msg.getType() != WebSocketMessage.Type.TEXT) return;

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> payload = MAPPER.readValue(msg.getPayloadAsText(), Map.class);
            String action = payload.get("action");
            String collection = payload.get("collection");

            if (action == null || collection == null) {
                sendMessage(session, Map.of("action", "error", "message", "Missing action or collection"));
                return;
            }

            switch (action) {
                case "subscribe" -> {
                    if (subscriptionManager.subscribe(session, tenantId, collection)) {
                        sendMessage(session, Map.of("action", "subscribed", "collection", collection));
                        log.debug("Session {} subscribed to {}", session.getId(), collection);
                    } else {
                        sendMessage(session, Map.of("action", "error", "message", "Subscription limit reached"));
                    }
                }
                case "unsubscribe" -> {
                    subscriptionManager.unsubscribe(session, tenantId, collection);
                    sendMessage(session, Map.of("action", "unsubscribed", "collection", collection));
                }
                default -> sendMessage(session, Map.of("action", "error", "message", "Unknown action: " + action));
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message: {}", e.getMessage());
            sendMessage(session, Map.of("action", "error", "message", "Invalid message format"));
        }
    }

    /**
     * Send a JSON message to a session via its outbound sink.
     */
    @SuppressWarnings("unchecked")
    public void sendMessage(WebSocketSession session, Map<String, ?> payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            Sinks.Many<WebSocketMessage> outbound =
                    (Sinks.Many<WebSocketMessage>) session.getAttributes().get("outbound");
            if (outbound != null) {
                outbound.tryEmitNext(session.textMessage(json));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket message", e);
        }
    }

    private void cleanup(WebSocketSession session) {
        subscriptionManager.removeSession(session);
        String userId = (String) session.getAttributes().get("userId");
        log.info("WebSocket disconnected: session={} user={}", session.getId(), userId);
    }
}
