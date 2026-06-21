package io.kelta.ai.model;

import io.kelta.ai.service.agent.AgentToolTrace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit record of a single governed agent run (or refusal): who ran which agent with what input,
 * the outcome status, the tool-call trace, token usage, and the final text. Persisted to
 * {@code ai_agent_execution} by {@link io.kelta.ai.service.agent.AgentExecutionService}.
 */
public record AgentExecution(
        UUID id,
        String tenantId,
        UUID agentId,
        String userId,
        String input,
        String status,
        List<AgentToolTrace> toolCalls,
        int inputTokens,
        int outputTokens,
        int iterations,
        String finalText,
        String error,
        Instant createdAt
) {
    public AgentExecution {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
