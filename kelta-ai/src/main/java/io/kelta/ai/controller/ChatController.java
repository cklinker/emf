package io.kelta.ai.controller;

import io.kelta.ai.config.AiConfigProperties;
import io.kelta.ai.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final AiConfigProperties config;

    public ChatController(ChatService chatService, AiConfigProperties config) {
        this.chatService = chatService;
        this.config = config;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> body) {

        String message = (String) body.get("message");
        String conversationIdStr = (String) body.get("conversationId");
        String contextType = (String) body.get("contextType");
        String contextId = (String) body.get("contextId");

        UUID conversationId = conversationIdStr != null ? UUID.fromString(conversationIdStr) : null;

        log.info("Chat request from tenant {} user {}", tenantId, userId);

        Map<String, Object> result = chatService.chat(
                Long.parseLong(tenantId), userId, conversationId,
                message, contextType, contextId);

        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> body) {

        String message = (String) body.get("message");
        String conversationIdStr = (String) body.get("conversationId");
        String contextType = (String) body.get("contextType");
        String contextId = (String) body.get("contextId");

        UUID conversationId = conversationIdStr != null ? UUID.fromString(conversationIdStr) : null;

        log.info("Stream chat request from tenant {} user {}", tenantId, userId);

        SseEmitter emitter = new SseEmitter(config.sseTimeoutMs());

        // Run the streaming in a separate thread to not block
        Thread.startVirtualThread(() -> chatService.chatStream(
                Long.parseLong(tenantId), userId, conversationId,
                message, contextType, contextId, emitter));

        return emitter;
    }
}
