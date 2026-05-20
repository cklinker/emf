package io.kelta.ai.controller;

import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.model.Conversation;
import io.kelta.ai.repository.ChatMessageRepository;
import io.kelta.ai.repository.ConversationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Read endpoints for AI conversation history. The {@code /messages} endpoint
 * returns full content_blocks so the React panel can rehydrate a conversation
 * after a page refresh.
 */
@RestController
@RequestMapping("/api/ai/conversations")
public class ChatHistoryController {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ChatHistoryController(ConversationRepository conversationRepository,
                                  ChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listConversations(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "50") int limit) {

        List<Conversation> conversations = conversationRepository.findByUser(tenantId, userId, limit);

        List<Map<String, Object>> data = conversations.stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", c.id().toString());
                    item.put("title", c.title());
                    item.put("createdAt", c.createdAt().toString());
                    item.put("updatedAt", c.updatedAt().toString());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getConversation(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {

        Conversation conversation = conversationRepository.findById(id, tenantId).orElse(null);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> messageData = messageRepository.findByConversation(id, tenantId)
                .stream().map(this::toMessageDto).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", conversation.id().toString());
        data.put("title", conversation.title());
        data.put("createdAt", conversation.createdAt().toString());
        data.put("updatedAt", conversation.updatedAt().toString());
        data.put("messages", messageData);

        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {

        Conversation conversation = conversationRepository.findById(id, tenantId).orElse(null);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> messageData = messageRepository.findByConversation(id, tenantId)
                .stream().map(this::toMessageDto).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversation.id().toString());
        result.put("messages", messageData);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toMessageDto(ChatMessage m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", m.id().toString());
        item.put("role", m.role());
        item.put("contentBlocks", m.contentBlocks() != null ? m.contentBlocks() : List.of());
        item.put("content", m.displayText());
        item.put("proposalJson", m.legacyProposalJson());
        item.put("tokensInput", m.tokensInput());
        item.put("tokensOutput", m.tokensOutput());
        item.put("createdAt", m.createdAt().toString());
        return item;
    }
}
