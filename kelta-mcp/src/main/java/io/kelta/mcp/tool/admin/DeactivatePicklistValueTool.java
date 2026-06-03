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
public class DeactivatePicklistValueTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public DeactivatePicklistValueTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Schemas.string("Picklist value id (UUID)."));

        Tool tool = Tool.builder()
                .name("deactivate_picklist_value")
                .title("Deactivate Picklist Value")
                .description("Mark a picklist value inactive so it no longer appears in pickers but existing records keep their stored value. Wraps PATCH /api/picklist-values/{id} with isActive=false. Use update_picklist_value to re-activate or to change other attributes.")
                .inputSchema(Schemas.object(properties, List.of("id")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    Object id = args == null ? null : args.get("id");
                    if (id == null || id.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"id\" is required.")))
                                .build();
                    }
                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "picklist-values",
                            "id", id.toString(),
                            "attributes", Map.of("isActive", false)));
                    String path = "/api/picklist-values/"
                            + URLEncoder.encode(id.toString(), StandardCharsets.UTF_8);
                    try {
                        return McpErrorMapper.toResult(gateway.patch(path, body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
