package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
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
public class UpdateCollectionTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public UpdateCollectionTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Schemas.string("Collection id (UUID) or name."));
        properties.put("displayFieldName", Schemas.string("New display field name."));
        properties.put("description", Schemas.string("New description."));
        properties.put("tenantScoped", Schemas.bool("New tenant-scoped flag.", true));

        Tool tool = Tool.builder()
                .name("update_collection")
                .title("Update Collection")
                .description("Update a collection's metadata (display field, description, tenant-scoping). PATCHes /api/collections/{id} — only supplied fields change.")
                .inputSchema(Schemas.object(properties, List.of("id")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object id = args.get("id");
                    if (id == null || id.toString().isBlank()) {
                        return error("Argument \"id\" is required.");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    if (args.get("displayFieldName") instanceof String s && !s.isBlank()) attrs.put("displayFieldName", s);
                    if (args.get("description") instanceof String s) attrs.put("description", s);
                    if (args.get("tenantScoped") instanceof Boolean b) attrs.put("tenantScoped", b);
                    if (attrs.isEmpty()) {
                        return error("Provide at least one of displayFieldName, description, tenantScoped.");
                    }

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "collections",
                            "id", id.toString(),
                            "attributes", attrs));
                    String path = "/api/collections/" + URLEncoder.encode(id.toString(), StandardCharsets.UTF_8);
                    try {
                        return McpErrorMapper.toResult(gateway.patch(path, body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .isError(true)
                .content(List.of(new TextContent(message)))
                .build();
    }
}
