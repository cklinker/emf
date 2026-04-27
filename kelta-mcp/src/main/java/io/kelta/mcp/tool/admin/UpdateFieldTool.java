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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class UpdateFieldTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public UpdateFieldTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Schemas.string("Field id (UUID)."));
        properties.put("required", Schemas.bool("New required flag.", false));
        properties.put("unique", Schemas.bool("New unique flag.", false));
        properties.put("description", Schemas.string("New description."));
        properties.put("defaultValue", Schemas.string("New default value."));
        properties.put("validation", Schemas.freeObject("New validation config."));

        Tool tool = Tool.builder()
                .name("update_field")
                .description("Update a field's mutable attributes. Wraps PATCH /api/fields/{id}. Note: changing field type or fieldName is generally rejected by the platform — drop+recreate is the path for those.")
                .inputSchema(Schemas.object(properties, List.of("id")))
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
                    if (args.get("required") instanceof Boolean b) attrs.put("required", b);
                    if (args.get("unique") instanceof Boolean b) attrs.put("unique", b);
                    if (args.get("description") instanceof String s) attrs.put("description", s);
                    if (args.get("defaultValue") instanceof String s) attrs.put("defaultValue", s);
                    if (args.get("validation") instanceof Map<?, ?> v) attrs.put("validation", v);
                    if (attrs.isEmpty()) {
                        return error("Provide at least one of required, unique, description, defaultValue, validation.");
                    }

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "fields",
                            "id", id.toString(),
                            "attributes", attrs));
                    String path = "/api/fields/" + URLEncoder.encode(id.toString(), StandardCharsets.UTF_8);
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
