package io.kelta.ai.service.agent;

import java.util.List;

/**
 * The result of one model round-trip: any assistant text, the tool calls requested, the stop reason
 * (lower-cased, e.g. {@code "tool_use"}, {@code "end_turn"}), and token usage. SDK-free so the
 * orchestration loop can be unit-tested without Anthropic types.
 */
public record AgentTurn(
        String text,
        List<AgentToolCall> toolCalls,
        String stopReason,
        int inputTokens,
        int outputTokens
) {
    public AgentTurn {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean requestedTools() {
        return !toolCalls.isEmpty();
    }
}
