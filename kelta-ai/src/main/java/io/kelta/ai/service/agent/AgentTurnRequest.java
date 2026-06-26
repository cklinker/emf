package io.kelta.ai.service.agent;

import java.util.List;

/**
 * Everything the model client needs for one agent turn: tenant, the agent's fixed system prompt,
 * optional model / max-token overrides, the names of the tools the agent may call, and the
 * conversation so far.
 */
public record AgentTurnRequest(
        String tenantId,
        String systemPrompt,
        String model,
        Integer maxTokens,
        List<String> allowedTools,
        List<AgentMessage> history
) {
}
