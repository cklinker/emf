package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExecuteFlowTool implements UserTool {

    private final GatewayHttpClient gateway;

    public ExecuteFlowTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("flowId", Schemas.string("Flow id (UUID) or name. Wraps POST /api/flows/{flowId}/execute."));
        properties.put("input", Schemas.freeObject(
                "Optional HTTP request body. Must double-wrap the flow's input under an \"input\" key: "
                + "pass {\"input\": {\"slug\": \"x\"}} so the engine's state envelope sets $.input = {\"slug\": \"x\"} "
                + "and the flow can read $.input.slug. Single-wrapping (e.g. {\"slug\": \"x\"}) leaves $.input empty "
                + "and downstream tasks fail with confusing errors."));

        Tool tool = Tool.builder()
                .name("execute_flow")
                .title("Execute Flow")
                .description("Trigger a flow execution. Returns the execution id; use get_flow_run to poll status. "
                        + "Inputs must be double-wrapped: execute_flow(flowId, input={\"input\": {\"slug\": \"x\"}}) "
                        + "so the flow can read $.input.slug. See integrations.md \"Flows\" for the state envelope.")
                .inputSchema(Schemas.object(properties, List.of("flowId")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object f = args.get("flowId");
                    if (f == null || f.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"flowId\" is required.")))
                                .build();
                    }
                    Object input = args.get("input");
                    Object body = (input instanceof Map<?, ?>) ? input : Map.of();

                    String path = "/api/flows/"
                            + URLEncoder.encode(f.toString(), StandardCharsets.UTF_8)
                            + "/execute";
                    try {
                        return McpErrorMapper.toResult(gateway.post(path, body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
