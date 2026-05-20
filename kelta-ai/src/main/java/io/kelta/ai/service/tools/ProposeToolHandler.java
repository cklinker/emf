package io.kelta.ai.service.tools;

import io.kelta.ai.model.AiProposal;

import java.util.Map;

public non-sealed interface ProposeToolHandler extends ToolHandler {

    AiProposal buildProposal(Map<String, Object> input);

    @Override
    default ToolKind kind() {
        return ToolKind.PROPOSE;
    }
}
