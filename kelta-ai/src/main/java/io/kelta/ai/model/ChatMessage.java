package io.kelta.ai.model;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        String tenantId,
        UUID conversationId,
        String role,
        String content,
        String proposalJson,
        int tokensInput,
        int tokensOutput,
        Instant createdAt
) {
    public static ChatMessage user(String tenantId, UUID conversationId, String content) {
        return new ChatMessage(UUID.randomUUID(), tenantId, conversationId, "user", content, null, 0, 0, Instant.now());
    }

    public static ChatMessage assistant(String tenantId, UUID conversationId, String content,
                                         String proposalJson, int tokensInput, int tokensOutput) {
        return new ChatMessage(UUID.randomUUID(), tenantId, conversationId, "assistant", content,
                proposalJson, tokensInput, tokensOutput, Instant.now());
    }
}
