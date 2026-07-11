package io.kelta.gateway.listener;

import tools.jackson.databind.ObjectMapper;
import io.kelta.gateway.websocket.RealtimeWebSocketHandler;
import io.kelta.gateway.websocket.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bridges chat NATS events to conversation-joined WebSocket sessions
 * (telehealth slice 2). Mirrors {@link RealtimeBridge}, but routes on the
 * CONVERSATION index — only sessions whose {@code chat.join} passed the
 * worker membership check receive events, never the whole tenant.
 *
 * <p>Payloads carry ids and state only (the worker hooks never publish
 * message bodies); clients treat these as invalidation signals and refetch
 * over the authorized /api/chat path.
 */
@Component
public class ChatMessageBridge {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageBridge.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SubscriptionManager subscriptionManager;
    private final RealtimeWebSocketHandler webSocketHandler;

    public ChatMessageBridge(SubscriptionManager subscriptionManager,
                             RealtimeWebSocketHandler webSocketHandler) {
        this.subscriptionManager = subscriptionManager;
        this.webSocketHandler = webSocketHandler;
    }

    /** Consumes {@code kelta.chat.message.<tenantId>.<conversationId>}. */
    @SuppressWarnings("unchecked")
    public void onChatMessage(String message) {
        try {
            Map<String, Object> event = MAPPER.readValue(message, Map.class);
            String tenantId = (String) event.get("tenantId");
            if (!(event.get("payload") instanceof Map<?, ?> payload) || tenantId == null) {
                return;
            }
            String conversationId = (String) payload.get("conversationId");
            if (conversationId == null) {
                return;
            }

            Map<String, Object> wsEvent = new LinkedHashMap<>();
            wsEvent.put("event", "chat.message");
            wsEvent.put("conversationId", conversationId);
            wsEvent.put("messageId", payload.get("messageId"));
            wsEvent.put("senderId", payload.get("senderId"));
            wsEvent.put("senderType", payload.get("senderType"));
            wsEvent.put("kind", payload.get("kind"));
            wsEvent.put("timestamp", event.get("timestamp"));

            fanOut(tenantId, conversationId, wsEvent);
        } catch (Exception e) {
            log.error("Failed to process chat message event: {}", e.getMessage());
        }
    }

    /** Consumes {@code kelta.chat.conversation.<tenantId>.<conversationId>}. */
    @SuppressWarnings("unchecked")
    public void onConversationChanged(String message) {
        try {
            Map<String, Object> event = MAPPER.readValue(message, Map.class);
            String tenantId = (String) event.get("tenantId");
            if (!(event.get("payload") instanceof Map<?, ?> payload) || tenantId == null) {
                return;
            }
            String conversationId = (String) payload.get("conversationId");
            if (conversationId == null) {
                return;
            }

            Map<String, Object> wsEvent = new LinkedHashMap<>();
            wsEvent.put("event", "chat.conversation");
            wsEvent.put("conversationId", conversationId);
            wsEvent.put("status", payload.get("status"));
            wsEvent.put("assignedTo", payload.get("assignedTo"));
            wsEvent.put("queueId", payload.get("queueId"));
            wsEvent.put("timestamp", event.get("timestamp"));

            fanOut(tenantId, conversationId, wsEvent);
        } catch (Exception e) {
            log.error("Failed to process chat conversation event: {}", e.getMessage());
        }
    }

    private void fanOut(String tenantId, String conversationId, Map<String, Object> wsEvent) {
        Set<WebSocketSession> subscribers =
                subscriptionManager.getConversationSubscribers(tenantId, conversationId);
        if (subscribers.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (WebSocketSession session : subscribers) {
            try {
                if (session.isOpen()) {
                    webSocketHandler.sendMessage(session, wsEvent);
                    delivered++;
                }
            } catch (Exception e) {
                log.debug("Failed to deliver chat event to session {}: {}", session.getId(), e.getMessage());
            }
        }
        if (delivered > 0) {
            log.debug("Routed {} for conversation {} to {} sessions",
                    wsEvent.get("event"), conversationId, delivered);
        }
    }
}
