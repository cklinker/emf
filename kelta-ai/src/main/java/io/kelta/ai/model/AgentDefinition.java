package io.kelta.ai.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A stored, governed AI agent definition: a reusable system prompt plus the guardrails that bound
 * its autonomy — the subset of MCP tools it may call, optional model/token-budget overrides, and an
 * enabled flag. The orchestration runtime (slice 6b) loads one of these and runs a tool-use loop
 * within these bounds.
 *
 * <p>{@code model}, {@code maxTokens} and {@code monthlyTokenBudget} are nullable: null defers to the
 * tenant/system AI defaults. {@code allowedTools} is never null — an empty list means the agent may
 * call no tools (text-only).
 */
public record AgentDefinition(
        UUID id,
        String tenantId,
        String name,
        String description,
        String systemPrompt,
        String model,
        Integer maxTokens,
        List<String> allowedTools,
        Long monthlyTokenBudget,
        boolean enabled,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
    public AgentDefinition {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    /** Builds a new definition with a fresh id and create/update timestamps. */
    public static AgentDefinition create(String tenantId, String name, String description,
                                         String systemPrompt, String model, Integer maxTokens,
                                         List<String> allowedTools, Long monthlyTokenBudget,
                                         boolean enabled, String createdBy) {
        Instant now = Instant.now();
        return new AgentDefinition(UUID.randomUUID(), tenantId, name, description, systemPrompt,
                model, maxTokens, allowedTools, monthlyTokenBudget, enabled, createdBy, createdBy,
                now, now);
    }

    /** Returns a copy with mutable fields replaced from {@code source}, preserving id/createdAt/createdBy. */
    public AgentDefinition withUpdates(String name, String description, String systemPrompt,
                                       String model, Integer maxTokens, List<String> allowedTools,
                                       Long monthlyTokenBudget, boolean enabled, String updatedBy) {
        return new AgentDefinition(id, tenantId, name, description, systemPrompt, model, maxTokens,
                allowedTools, monthlyTokenBudget, enabled, createdBy, updatedBy, createdAt,
                Instant.now());
    }
}
