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
        properties.put("ruleId", Schemas.string("UUID of the validation rule to delete."));

        Tool tool = Tool.builder()
                .name("delete_validation_rule")
                .title("Delete Validation Rule")
                .description("Delete a validation rule by id. Wraps DELETE /api/validation-rules/{id}. Use list_validation_rules to discover rule ids.")
                .inputSchema(Schemas.object(properties, List.of("ruleId")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    Object id = args == null ? null : args.get("ruleId");
                    if (id == null || id.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"ruleId\" is required.")))
                                .build();
                    }
                    String ruleId = id.toString();
                    try {
                        GatewayHttpClient.Response response = gateway.delete(
                                "/api/validation-rules/"
                                        + URLEncoder.encode(ruleId, StandardCharsets.UTF_8));
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted validation rule " + ruleId
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
