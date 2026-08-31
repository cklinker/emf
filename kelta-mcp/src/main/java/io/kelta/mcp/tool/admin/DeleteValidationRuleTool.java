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
public class DeleteValidationRuleTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public DeleteValidationRuleTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Schemas.string("Validation rule id (UUID)."));

        Tool tool = Tool.builder()
                .name("delete_validation_rule")
                .title("Delete Validation Rule")
                .description("Delete a validation rule by id. Wraps DELETE /api/validation-rules/{id}. "
                        + "Use list_validation_rules to find the rule id first.")
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
                    String idStr = id.toString();
                    try {
                        GatewayHttpClient.Response response = gateway.delete(
                                "/api/validation-rules/"
                                        + URLEncoder.encode(idStr, StandardCharsets.UTF_8));
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted validation rule " + idStr
                                                    + " (HTTP " + response.status().value() + ")")))
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
