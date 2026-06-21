package io.kelta.ai.service.agent;

import java.util.List;

/**
 * Outcome of a governed agent run: the final assistant text, the ordered tool-call trace, total
 * token usage, the number of model turns, the last stop reason, and whether the per-run token
 * budget or iteration cap halted the loop before the model finished on its own.
 */
public record AgentRunResult(
        String finalText,
        List<AgentToolTrace> toolCalls,
        int inputTokens,
        int outputTokens,
        int iterations,
        String stopReason,
        boolean budgetExceeded,
        boolean maxIterationsReached
) {
}
