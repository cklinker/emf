package io.kelta.worker.controller;

import io.kelta.worker.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Service-to-service membership check for the gateway's conversation-scoped
 * WebSocket routing (telehealth slice 2). {@code /internal/**} is not
 * gateway-routed; when the internal-auth rollout flag is on, the standard
 * internal JWT chain guards it (same as the other /internal endpoints).
 *
 * <p>No tenant context is bound on internal calls, so the tenant travels as an
 * explicit parameter and the query filters on it explicitly (the admin_bypass
 * RLS policy applies under the platform connection).
 */
@RestController
@RequestMapping("/internal/chat")
public class InternalChatController {

    private final ChatService chatService;

    public InternalChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * @param user platform_user id (UUID) or email — the gateway sends whatever
     *             identity the WS JWT carries; the query matches either.
     */
    @GetMapping("/conversations/{conversationId}/members")
    public ResponseEntity<Map<String, Boolean>> isMember(@PathVariable String conversationId,
                                                         @RequestParam String tenantId,
                                                         @RequestParam String user) {
        boolean member = chatService.isMember(tenantId, conversationId, user);
        return ResponseEntity.ok(Map.of("member", member));
    }
}
