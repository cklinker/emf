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
                "Field type: string, text, rich_text, number, integer, decimal, boolean, date, datetime, "
                        + "reference, picklist, multi_picklist, json, vector, file, image."));
        properties.put("required", Schemas.bool("Whether the field is required (default false).", false));
        properties.put("unique", Schemas.bool("Whether values must be unique (default false).", false));
        properties.put("description", Schemas.string("Optional description shown in the admin UI."));
        properties.put("defaultValue", Schemas.anyScalar(
                "Optional default value. Pass it in the field's native JSON type — e.g. true for booleans, 360 for numbers, \"hello\" for strings. The platform coerces and stores it according to the field type."));
        properties.put("validation", Schemas.freeObject(
                "Optional validation config (regex, min/max, etc.) — shape depends on the field type."));
        properties.put("referenceCollection", Schemas.string(
                "For type=reference, the target collection name."));
        properties.put("dimension", Schemas.integer(
                "For type=vector, the embedding dimension (1..16000). Required for VECTOR fields.",
                1, 16000));

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

                    String typeArg = normalizeType(t.toString());
                    Integer dimension = readDimension(args.get("dimension"));
                    if ("vector".equalsIgnoreCase(typeArg) && dimension == null) {
                        return error("Argument \"dimension\" is required when \"type\" is \"vector\" "
                                + "(1..16000; 1536 matches OpenAI text-embedding-3-small).");
                    }
                    if (dimension != null && (dimension < 1 || dimension > 16000)) {
                        return error("\"dimension\" must be between 1 and 16000.");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("collectionName", cn.toString());
                    attrs.put("fieldName", fn.toString());
                    attrs.put("type", typeArg);
                    if (args.get("required") instanceof Boolean b) attrs.put("required", b);
                    if (args.get("unique") instanceof Boolean b) attrs.put("unique", b);
                    if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);
                    if (args.containsKey("defaultValue")) {
                        Object dv = args.get("defaultValue");
                        if (dv instanceof String || dv instanceof Boolean || dv instanceof Number) {
                            attrs.put("defaultValue", dv);
                        } else if (dv == null) {
                            attrs.put("defaultValue", null);
                        }
                    }
                    if (args.get("validation") instanceof Map<?, ?> v) attrs.put("validation", v);
                    if (args.get("referenceCollection") instanceof String s && !s.isBlank()) attrs.put("referenceCollection", s);
                    if (dimension != null) {
                        attrs.put("fieldTypeConfig", Map.of("dimension", dimension));
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

    /**
     * Rewrites the camelCase {@code richText} alias to canonical {@code rich_text}
     * so the worker's FieldLifecycleHook (which accepts snake_case lowercase or
     * uppercase enum forms) doesn't reject it. Other inputs pass through.
     */
    private static String normalizeType(String raw) {
        return "richText".equals(raw) ? "rich_text" : raw;
    }

    private static Integer readDimension(Object raw) {
        return switch (raw) {
            case Integer i -> i;
            case Number n -> n.intValue();
            case String s when !s.isBlank() -> {
                try {
                    yield Integer.parseInt(s.trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case null, default -> null;
        };
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .isError(true)
                .content(List.of(new TextContent(message)))
                .build();
    }
}
