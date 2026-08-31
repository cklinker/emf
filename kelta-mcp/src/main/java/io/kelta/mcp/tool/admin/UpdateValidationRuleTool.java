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
        properties.put("id", Schemas.string("Validation rule id (UUID)."));
        properties.put("name", Schemas.string("New rule name."));
        properties.put("errorConditionFormula", Schemas.string(
                "New error condition formula — record is REJECTED when this evaluates TRUE."));
        properties.put("errorMessage", Schemas.string("New error message shown to the user."));
        properties.put("errorField", Schemas.string(
                "Field to attribute the error to (pass an empty string to clear)."));
        properties.put("evaluateOn", Schemas.string(
                "When to run: CREATE, UPDATE, or CREATE_AND_UPDATE."));
        properties.put("severity", Schemas.string("ERROR or WARNING."));
        properties.put("active", Schemas.bool("Active flag.", true));

        Tool tool = Tool.builder()
                .name("update_validation_rule")
                .title("Update Validation Rule")
                .description("Update a validation rule's mutable attributes. "
                        + "Wraps PATCH /api/validation-rules/{id}. "
                        + "Only the fields you supply are sent — omitted fields are left unchanged. "
                        + "Remember: errorConditionFormula is an ERROR condition — the record is "
                        + "REJECTED when it evaluates TRUE.")
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
                        return error("Argument \"id\" is required.");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    if (args.get("name") instanceof String s && !s.isBlank()) attrs.put("name", s);
                    if (args.get("errorConditionFormula") instanceof String s && !s.isBlank())
                        attrs.put("errorConditionFormula", s);
                    if (args.get("errorMessage") instanceof String s && !s.isBlank())
                        attrs.put("errorMessage", s);
                    if (args.get("errorField") instanceof String s) attrs.put("errorField", s);
                    if (args.get("evaluateOn") instanceof String s && !s.isBlank())
                        attrs.put("evaluateOn", s);
                    if (args.get("severity") instanceof String s && !s.isBlank())
                        attrs.put("severity", s);
                    if (args.get("active") instanceof Boolean b) attrs.put("active", b);

                    if (attrs.isEmpty()) {
                        return error("Provide at least one of name, errorConditionFormula, "
                                + "errorMessage, errorField, evaluateOn, severity, active.");
                    }

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "validation-rules",
                            "id", id.toString(),
                            "attributes", attrs));
                    String path = "/api/validation-rules/"
                            + URLEncoder.encode(id.toString(), StandardCharsets.UTF_8);
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
