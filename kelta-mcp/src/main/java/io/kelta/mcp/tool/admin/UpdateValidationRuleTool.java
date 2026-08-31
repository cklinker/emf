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
public class UpdateValidationRuleTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public UpdateValidationRuleTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ruleId", Schemas.string("UUID of the validation rule to update."));
        properties.put("name", Schemas.string("New rule name."));
        properties.put("errorConditionFormula", Schemas.string(
                "Updated error condition formula. The record is REJECTED when this evaluates TRUE."));
        properties.put("errorMessage", Schemas.string("Updated error message shown to the user."));
        properties.put("errorField", Schemas.string(
                "Field name the error is attributed to in API responses and forms."));
        properties.put("evaluateOn", Schemas.string(
                "When the rule runs: CREATE, UPDATE, or CREATE_AND_UPDATE."));
        properties.put("severity", Schemas.string("ERROR (blocks the save) or WARNING."));
        properties.put("active", Schemas.bool("Enable or disable the rule.", true));

        Tool tool = Tool.builder()
                .name("update_validation_rule")
                .title("Update Validation Rule")
                .description("Update an existing validation rule. Only fields supplied in the request are changed. Wraps PATCH /api/validation-rules/{id}.")
                .inputSchema(Schemas.object(properties, List.of("ruleId")))
                .annotations(ToolHints.write(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object id = args.get("ruleId");
                    if (id == null || id.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"ruleId\" is required.")))
                                .build();
                    }
                    String ruleId = id.toString();
                    Map<String, Object> attrs = new LinkedHashMap<>();
                    if (args.get("name") instanceof String s && !s.isBlank()) attrs.put("name", s);
                    if (args.get("errorConditionFormula") instanceof String s && !s.isBlank()) attrs.put("errorConditionFormula", s);
                    if (args.get("errorMessage") instanceof String s && !s.isBlank()) attrs.put("errorMessage", s);
                    if (args.get("errorField") instanceof String s && !s.isBlank()) attrs.put("errorField", s);
                    if (args.get("evaluateOn") instanceof String s && !s.isBlank()) attrs.put("evaluateOn", s);
                    if (args.get("severity") instanceof String s && !s.isBlank()) attrs.put("severity", s);
                    if (args.get("active") instanceof Boolean b) attrs.put("active", b);

                    if (attrs.isEmpty()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Provide at least one attribute to update.")))
                                .build();
                    }

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "validation-rules",
                            "id", ruleId,
                            "attributes", attrs));
                    try {
                        return McpErrorMapper.toResult(gateway.patch(
                                "/api/validation-rules/"
                                        + URLEncoder.encode(ruleId, StandardCharsets.UTF_8),
                                body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
