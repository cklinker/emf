package io.kelta.ai.service.tools;

import java.util.Map;

public non-sealed interface ReadToolHandler extends ToolHandler {

    Object execute(String tenantId, String userId, Map<String, Object> input);

    @Override
    default ToolKind kind() {
        return ToolKind.READ;
    }
}
