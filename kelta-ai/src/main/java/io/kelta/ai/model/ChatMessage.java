package io.kelta.ai.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record ChatMessage(
        UUID id,
        String tenantId,
        UUID conversationId,
        String role,
        List<Map<String, Object>> contentBlocks,
        String legacyContent,
        String legacyProposalJson,
        int tokensInput,
        int tokensOutput,
        Instant createdAt
) {

    public static ChatMessage user(String tenantId, UUID conversationId, String content) {
        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", content);
        return new ChatMessage(UUID.randomUUID(), tenantId, conversationId, "user",
                List.of(textBlock), content, null, 0, 0, Instant.now());
    }

    public static ChatMessage user(String tenantId, UUID conversationId, List<Map<String, Object>> blocks) {
        return new ChatMessage(UUID.randomUUID(), tenantId, conversationId, "user",
                List.copyOf(blocks), null, null, 0, 0, Instant.now());
    }

    public static ChatMessage assistant(String tenantId, UUID conversationId,
                                         List<Map<String, Object>> blocks,
                                         int tokensInput, int tokensOutput) {
        return new ChatMessage(UUID.randomUUID(), tenantId, conversationId, "assistant",
                List.copyOf(blocks), null, null, tokensInput, tokensOutput, Instant.now());
    }

    /** Plain text for UI summary views. Concatenates all text blocks. */
    public String displayText() {
        if (contentBlocks == null) return legacyContent != null ? legacyContent : "";
        return contentBlocks.stream()
                .filter(b -> "text".equals(b.get("type")))
                .map(b -> String.valueOf(b.getOrDefault("text", "")))
                .collect(Collectors.joining("\n"));
    }
}
