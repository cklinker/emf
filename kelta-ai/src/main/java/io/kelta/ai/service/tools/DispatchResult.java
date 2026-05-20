package io.kelta.ai.service.tools;

import io.kelta.ai.model.AiProposal;

public record DispatchResult(
        String toolUseId,
        String toolName,
        String resultJson,
        boolean isError,
        AiProposal proposal
) {
    public static DispatchResult readResult(String toolUseId, String toolName, String resultJson, boolean isError) {
        return new DispatchResult(toolUseId, toolName, resultJson, isError, null);
    }

    public static DispatchResult proposalQueued(String toolUseId, String toolName, String ackJson, AiProposal proposal) {
        return new DispatchResult(toolUseId, toolName, ackJson, false, proposal);
    }
}
