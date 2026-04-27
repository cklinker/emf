package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CreateFlowTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public CreateFlowTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Schemas.string("Flow name (unique within tenant)."));
        properties.put("description", Schemas.string("Optional description."));
        properties.put("triggerType", Schemas.string(
                "Trigger type: manual, recordCreated, recordUpdated, scheduled, webhook, etc."));
        properties.put("triggerConfig", Schemas.freeObject(
                "Trigger-specific config (e.g. {\"collectionName\":\"orders\"} for recordCreated)."));
        properties.put("definition", Schemas.freeObject(
                "Flow JSON definition: nodes, edges, conditions, actions. Shape is flow-engine specific."));
        properties.put("active", Schemas.bool("Whether the flow is active on creation (default false).", false));

        Tool tool = Tool.builder()
                .name("create_flow")
                .description("Create a new automation flow. Wraps POST /api/flows. The definition payload is opaque to this tool — pass the flow-engine JSON directly.")
                .inputSchema(Schemas.object(properties, List.of("name", "triggerType", "definition")))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object name = args.get("name");
                    Object trigger = args.get("triggerType");
                    Object def = args.get("definition");
                    if (name == null || name.toString().isBlank()
                            || trigger == null || trigger.toString().isBlank()
                            || !(def instanceof Map<?, ?> definition) || definition.isEmpty()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Arguments \"name\", \"triggerType\", and a non-empty \"definition\" are required.")))
                                .build();
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("name", name.toString());
                    attrs.put("triggerType", trigger.toString());
                    attrs.put("definition", definition);
                    if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);
                    if (args.get("triggerConfig") instanceof Map<?, ?> tc) attrs.put("triggerConfig", tc);
                    if (args.get("active") instanceof Boolean b) attrs.put("active", b);
                    else attrs.put("active", false);

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "flows",
                            "attributes", attrs));
                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/flows", body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
