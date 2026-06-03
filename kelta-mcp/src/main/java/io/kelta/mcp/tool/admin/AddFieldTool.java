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
                "Field type: text (TEXT, long-form plain text), richText (RICH_TEXT, HTML/markdown), string,"
                        + " longText, number, integer, decimal, boolean, date, datetime, reference,"
                        + " picklist, multiPicklist, json, file, image, vector (requires dimension)."));
        properties.put("required", Schemas.bool("Whether the field is required (default false).", false));
        properties.put("unique", Schemas.bool("Whether values must be unique (default false).", false));
        properties.put("description", Schemas.string("Optional description shown in the admin UI."));
        properties.put("defaultValue", Schemas.string("Optional default value as a string. Numeric/boolean values are still passed as strings here."));
        properties.put("validation", Schemas.freeObject(
                "Optional validation config (regex, min/max, etc.) — shape depends on the field type."));
        properties.put("referenceCollection", Schemas.string(
                "For type=reference, the target collection name."));
        properties.put("dimension", Schemas.integer(
                "For type=vector, the embedding dimension (e.g. 1536 for OpenAI text-embedding-3-small). Required for VECTOR fields.",
                1, 16_000));

        Tool tool = Tool.builder()
                .name("add_field")
                .title("Add Field")
                .description("Add a field to an existing collection. Wraps POST /api/fields with a JSON:API body.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "fieldName", "type")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
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

                    String rawType = t.toString();
                    String wireType = mapTypeAlias(rawType);

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("collectionName", cn.toString());
                    attrs.put("fieldName", fn.toString());
                    attrs.put("type", wireType);
                    if (args.get("required") instanceof Boolean b) attrs.put("required", b);
                    if (args.get("unique") instanceof Boolean b) attrs.put("unique", b);
                    if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);
                    if (args.get("defaultValue") instanceof String s) attrs.put("defaultValue", s);
                    if (args.get("validation") instanceof Map<?, ?> v) attrs.put("validation", v);
                    if (args.get("referenceCollection") instanceof String s && !s.isBlank()) attrs.put("referenceCollection", s);

                    if (isVectorType(wireType)) {
                        Object dim = args.get("dimension");
                        if (!(dim instanceof Number)) {
                            return error("Argument \"dimension\" is required when type=vector.");
                        }
                        attrs.put("fieldTypeConfig", Map.of("dimension", ((Number) dim).intValue()));
                    }

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

    /**
     * Translates camelCase aliases that the worker's lifecycle hook does not
     * accept into their snake_case equivalents. Everything else is passed
     * through untouched — the worker handles canonicalization.
     */
    static String mapTypeAlias(String alias) {
        String trimmed = alias.trim();
        return switch (trimmed) {
            case "richText" -> "rich_text";
            case "multiPicklist" -> "multi_picklist";
            default -> trimmed;
        };
    }

    static boolean isVectorType(String wireType) {
        return "vector".equalsIgnoreCase(wireType.trim());
    }
}
