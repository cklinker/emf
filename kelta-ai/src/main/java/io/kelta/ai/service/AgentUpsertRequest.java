package io.kelta.ai.service;

import java.util.List;

/**
 * Create/update payload for an agent definition. {@code enabled} is nullable so an omitted value
 * defaults to {@code true}; {@code model}, {@code maxTokens} and {@code monthlyTokenBudget} are
 * optional overrides that fall back to tenant/system AI defaults when null.
 */
public record AgentUpsertRequest(
        String name,
        String description,
        String systemPrompt,
        String model,
        Integer maxTokens,
        List<String> allowedTools,
        Long monthlyTokenBudget,
        Boolean enabled
) {
}
