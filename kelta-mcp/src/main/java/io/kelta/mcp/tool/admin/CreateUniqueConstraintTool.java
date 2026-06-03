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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin tool: create a composite unique constraint on a collection.
 *
 * <p>Wraps {@code POST /api/admin/collections/{name}/unique-constraints}.
 * The underlying Postgres index is created against the collection's physical
 * table, so duplicate inserts on the constrained column tuple are rejected at
 * the database layer (409 Conflict from the runtime's
 * {@code UniqueConstraintViolationException} handler).
 */
@Component
public class CreateUniqueConstraintTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public CreateUniqueConstraintTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> fieldNamesArray = new LinkedHashMap<>();
        fieldNamesArray.put("type", "array");
        fieldNamesArray.put("description",
                "Field names (in collection schema order) participating in the constraint. "
                        + "Provide 2+ for a composite constraint, e.g. [\"title\", \"provider\", \"region\"].");
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "string");
        fieldNamesArray.put("items", itemSchema);
        fieldNamesArray.put("minItems", 1);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection name (e.g. \"availabilities\"). The collection must already exist."));
        properties.put("fieldNames", fieldNamesArray);

        Tool tool = Tool.builder()
                .name("create_unique_constraint")
                .title("Create Unique Constraint")
                .description("Create a composite unique constraint on a collection. Enforced at "
                        + "the Postgres level via CREATE UNIQUE INDEX, so duplicate inserts return "
                        + "a 409 Conflict. Use this for multi-column uniqueness like "
                        + "Availability(title, provider, region) or Season(show, seasonNumber); "
                        + "single-column uniqueness is better expressed via add_field.unique.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "fieldNames")))
                .annotations(ToolHints.write(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();

                    Object cn = args.get("collectionName");
                    if (cn == null || cn.toString().isBlank()) {
                        return error("Argument \"collectionName\" is required.");
                    }
                    List<String> fieldNames = extractFieldNames(args.get("fieldNames"));
                    if (fieldNames == null || fieldNames.isEmpty()) {
                        return error("Argument \"fieldNames\" must be a non-empty array of strings.");
                    }

                    Map<String, Object> body = Map.of("fieldNames", fieldNames);
                    String path = "/api/admin/collections/"
                            + URLEncoder.encode(cn.toString(), StandardCharsets.UTF_8)
                            + "/unique-constraints";

                    try {
                        GatewayHttpClient.Response response = gateway.post(path, body);
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(response.body())))
                                    .build();
                        }
                        return McpErrorMapper.toResult(response);
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }

    static List<String> extractFieldNames(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return null;
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) return null;
            String s = item.toString().trim();
            if (s.isEmpty()) return null;
            result.add(s);
        }
        return result;
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .isError(true)
                .content(List.of(new TextContent(message)))
                .build();
    }
}
