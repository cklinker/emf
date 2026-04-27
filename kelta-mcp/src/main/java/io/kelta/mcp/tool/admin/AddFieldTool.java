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
public class AddFieldTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public AddFieldTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string("Collection the field belongs to."));
        properties.put("fieldName", Schemas.string("Field name (camelCase recommended)."));
        properties.put("type", Schemas.string(
                "Field type: text, longText, number, integer, decimal, boolean, date, datetime, reference, picklist, multiPicklist, json, file, image."));
        properties.put("required", Schemas.bool("Whether the field is required (default false).", false));
        properties.put("unique", Schemas.bool("Whether values must be unique (default false).", false));
        properties.put("description", Schemas.string("Optional description shown in the admin UI."));
        properties.put("defaultValue", Schemas.string("Optional default value as a string. Numeric/boolean values are still passed as strings here."));
        properties.put("validation", Schemas.freeObject(
                "Optional validation config (regex, min/max, etc.) — shape depends on the field type."));
        properties.put("referenceCollection", Schemas.string(
                "For type=reference, the target collection name."));

        Tool tool = Tool.builder()
                .name("add_field")
                .description("Add a field to an existing collection. Wraps POST /api/fields with a JSON:API body.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "fieldName", "type")))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object fn = args.get("fieldName");
                    Object t = args.get("type");
                    if (cn == null || cn.toString().isBlank()
                            || fn == null || fn.toString().isBlank()
                            || t == null || t.toString().isBlank()) {
                        return error("Arguments \"collectionName\", \"fieldName\", and \"type\" are required.");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("collectionName", cn.toString());
                    attrs.put("fieldName", fn.toString());
                    attrs.put("type", t.toString());
                    if (args.get("required") instanceof Boolean b) attrs.put("required", b);
                    if (args.get("unique") instanceof Boolean b) attrs.put("unique", b);
                    if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);
                    if (args.get("defaultValue") instanceof String s) attrs.put("defaultValue", s);
                    if (args.get("validation") instanceof Map<?, ?> v) attrs.put("validation", v);
                    if (args.get("referenceCollection") instanceof String s && !s.isBlank()) attrs.put("referenceCollection", s);

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "fields",
                            "attributes", attrs));
                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/fields", body));
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
