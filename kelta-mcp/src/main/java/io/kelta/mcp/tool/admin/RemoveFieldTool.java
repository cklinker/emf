package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
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
public class RemoveFieldTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public RemoveFieldTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Schemas.string("Field id (UUID)."));

        Tool tool = Tool.builder()
                .name("remove_field")
                .title("Remove Field")
                .description("Remove a field from a collection. Wraps DELETE /api/fields/{id}. The field's data on existing records is dropped — irreversible.")
                .inputSchema(Schemas.object(properties, List.of("id")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object id = args.get("id");
                    if (id == null || id.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"id\" is required.")))
                                .build();
                    }
                    String path = "/api/fields/" + URLEncoder.encode(id.toString(), StandardCharsets.UTF_8);
                    try {
                        GatewayHttpClient.Response response = gateway.delete(path);
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Removed field " + id + " (HTTP " + response.status().value() + ")")))
                                    .build();
                        }
                        return McpErrorMapper.toResult(response);
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
