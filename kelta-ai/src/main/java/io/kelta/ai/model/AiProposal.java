package io.kelta.ai.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiProposal(
        UUID id,
        String type,
        String status,
        Map<String, Object> data,
        Instant createdAt
) {
    public static AiProposal pending(String type, Map<String, Object> data) {
        return new AiProposal(UUID.randomUUID(), type, "pending", data, Instant.now());
    }

    public AiProposal withStatus(String newStatus) {
        return new AiProposal(id, type, newStatus, data, createdAt);
    }
}
