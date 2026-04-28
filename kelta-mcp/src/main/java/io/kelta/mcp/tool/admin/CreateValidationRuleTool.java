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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CreateValidationRuleTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public CreateValidationRuleTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string("Collection the rule applies to."));
        properties.put("name", Schemas.string("Rule name (unique within collection)."));
        properties.put("expression", Schemas.string(
                "Boolean expression that must evaluate true. Failure triggers the rule. "
                + "Reference fields with their attribute names (e.g. \"amount > 0\")."));
        properties.put("errorMessage", Schemas.string(
                "Error message shown to the user when the rule fails."));
        properties.put("active", Schemas.bool("Whether the rule is active (default true).", true));

        Tool tool = Tool.builder()
                .name("create_validation_rule")
                .title("Create Validation Rule")
                .description("Create a validation rule on a collection. Records that fail the expression are rejected at create/update time.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "name", "expression", "errorMessage")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object name = args.get("name");
                    Object expr = args.get("expression");
                    Object msg = args.get("errorMessage");
                    if (cn == null || cn.toString().isBlank()
                            || name == null || name.toString().isBlank()
                            || expr == null || expr.toString().isBlank()
                            || msg == null || msg.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Arguments \"collectionName\", \"name\", \"expression\", and \"errorMessage\" are required.")))
                                .build();
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("collectionName", cn.toString());
                    attrs.put("name", name.toString());
                    attrs.put("expression", expr.toString());
                    attrs.put("errorMessage", msg.toString());
                    if (args.get("active") instanceof Boolean b) attrs.put("active", b);
                    else attrs.put("active", true);

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "validationRules",
                            "attributes", attrs));
                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/validationRules", body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
