package io.kelta.ai.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
        UUID id,
        long tenantId,
        String userId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
    public static Conversation create(long tenantId, String userId, String title) {
        Instant now = Instant.now();
        return new Conversation(UUID.randomUUID(), tenantId, userId, title, now, now);
    }
}
