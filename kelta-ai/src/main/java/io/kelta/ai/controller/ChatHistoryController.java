package io.kelta.ai.controller;

import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.model.Conversation;
import io.kelta.ai.repository.ChatMessageRepository;
import io.kelta.ai.repository.ConversationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

        List<Conversation> conversations = conversationRepository.findByUser(
                Long.parseLong(tenantId), userId, limit);

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

        long tid = Long.parseLong(tenantId);

        Conversation conversation = conversationRepository.findById(id, tid)
                .orElse(null);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        List<ChatMessage> messages = messageRepository.findByConversation(id, tid);

        List<Map<String, Object>> messageData = messages.stream()
                .map(m -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", m.id().toString());
                    item.put("role", m.role());
                    item.put("content", m.content());
                    item.put("proposalJson", m.proposalJson());
                    item.put("tokensInput", m.tokensInput());
                    item.put("tokensOutput", m.tokensOutput());
                    item.put("createdAt", m.createdAt().toString());
                    return item;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", conversation.id().toString());
        data.put("title", conversation.title());
        data.put("createdAt", conversation.createdAt().toString());
        data.put("updatedAt", conversation.updatedAt().toString());
        data.put("messages", messageData);

        return ResponseEntity.ok(Map.of("data", data));
    }
}
