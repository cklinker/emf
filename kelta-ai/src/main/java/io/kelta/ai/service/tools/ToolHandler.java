package io.kelta.ai.service.tools;

import java.util.Map;

public sealed interface ToolHandler permits ReadToolHandler, ProposeToolHandler {

    String name();

    String description();

    Map<String, Object> inputSchema();

    ToolKind kind();
}
