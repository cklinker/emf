package io.kelta.gateway.config;

import io.kelta.gateway.websocket.RealtimeWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * Configures WebSocket endpoint for realtime subscriptions.
 *
 * @since 1.0.0
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(RealtimeWebSocketHandler handler) {
        return new SimpleUrlHandlerMapping(Map.of("/ws/realtime", handler), -1);
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
