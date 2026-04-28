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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite admin tool: create a collection and (optionally) its initial
 * fields in a single Claude tool call.
 *
 * <p>Wraps two underlying endpoints:
 * <ol>
 *   <li>POST /api/collections — create the collection metadata</li>
 *   <li>POST /api/fields (per field) — create each requested field</li>
 * </ol>
 *
 * <p>If field creation fails partway through, the collection is left
 * in place — there's no atomic transaction across system collections,
 * but the response surfaces which fields succeeded and which didn't,
 * so a follow-up update_field/add_field call can recover.
 */
@Component
public class CreateCollectionTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public CreateCollectionTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> fieldItem = new LinkedHashMap<>();
        fieldItem.put("type", "object");
        fieldItem.put("description",
                "{\"fieldName\":\"...\",\"type\":\"text|number|boolean|date|datetime|reference|picklist|...\","
                + "\"required\":bool,\"unique\":bool,\"description\":\"...\"}");
        fieldItem.put("additionalProperties", true);

        Map<String, Object> fieldsArray = new LinkedHashMap<>();
        fieldsArray.put("type", "array");
        fieldsArray.put("description", "Optional initial field set. Each entry will be POSTed to /api/fields after the collection is created.");
        fieldsArray.put("items", fieldItem);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Schemas.string("Collection name (e.g. \"projects\"). Used as the URL slug — keep it kebab-cased or single-word."));
        properties.put("displayFieldName", Schemas.string(
                "Optional name of the field used as the human-readable label for a record (e.g. \"name\")."));
        properties.put("description", Schemas.string("Optional description shown in the admin UI."));
        properties.put("tenantScoped", Schemas.bool(
                "Whether records are tenant-scoped (default true). Only set false for shared system data.", true));
        properties.put("fields", fieldsArray);

        Tool tool = Tool.builder()
                .name("create_collection")
                .title("Create Collection")
                .description("Create a new collection definition, optionally with an initial set of fields. The collection is created first; each field is then created in order. Returns a summary with the new collection id and per-field success/failure.")
                .inputSchema(Schemas.object(properties, List.of("name")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object n = args.get("name");
                    if (n == null || n.toString().isBlank()) {
                        return error("Argument \"name\" is required.");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("name", n.toString());
                    if (args.get("displayFieldName") instanceof String s && !s.isBlank()) attrs.put("displayFieldName", s);
                    if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);
                    if (args.get("tenantScoped") instanceof Boolean b) attrs.put("tenantScoped", b);

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "collections",
                            "attributes", attrs));

                    GatewayHttpClient.Response collectionRes;
                    try {
                        collectionRes = gateway.post("/api/collections", body);
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                    if (!collectionRes.isSuccess()) {
                        return McpErrorMapper.toResult(collectionRes);
                    }

                    Object fieldsObj = args.get("fields");
                    if (!(fieldsObj instanceof List<?> fields) || fields.isEmpty()) {
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(collectionRes.body())))
                                .build();
                    }

                    List<Map<String, Object>> fieldResults = new ArrayList<>();
                    for (int i = 0; i < fields.size(); i++) {
                        Object item = fields.get(i);
                        if (!(item instanceof Map<?, ?> fieldAttrs)) {
                            fieldResults.add(Map.of("index", i, "error", "field entry must be an object"));
                            continue;
                        }
                        Map<String, Object> fAttrs = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : fieldAttrs.entrySet()) {
                            fAttrs.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        fAttrs.putIfAbsent("collectionName", n.toString());

                        Map<String, Object> fieldBody = Map.of("data", Map.of(
                                "type", "fields",
                                "attributes", fAttrs));
                        GatewayHttpClient.Response fr;
                        try {
                            fr = gateway.post("/api/fields", fieldBody);
                        } catch (RuntimeException e) {
                            fieldResults.add(Map.of("index", i, "error", e.getClass().getSimpleName()));
                            continue;
                        }
                        fieldResults.add(Map.of(
                                "index", i,
                                "fieldName", fAttrs.getOrDefault("fieldName", "?"),
                                "status", fr.status() == null ? 0 : fr.status().value(),
                                "body", fr.isSuccess() ? "ok" : fr.body()));
                    }

                    String summary = "{\n  \"collection\": " + collectionRes.body()
                            + ",\n  \"fields\": " + fieldResults
                            + "\n}";
                    return CallToolResult.builder()
                            .content(List.of(new TextContent(summary)))
                            .build();
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
