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

/**
 * Admin tool: create a composite (multi-column) unique constraint on a
 * collection. Wraps {@code POST /api/_composite-unique-constraints}.
 *
 * <p>Use this when a single-column {@code unique} flag is insufficient —
 * e.g. {@code Availability(title, provider, region)},
 * {@code Watchlist(user, title)}, {@code Episode(season, episodeNumber)}.
 *
 * <p>The constraint is enforced at the PostgreSQL level, so duplicate
 * inserts return HTTP 409 Conflict with a JSON:API error pointing at the
 * offending tuple.
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
                "Ordered list of field names (≥ 2) covered by the unique constraint. "
                        + "Field order matters — PostgreSQL treats (a,b) and (b,a) as distinct "
                        + "constraints, and only the leading-column order can serve index lookups.");
        fieldNamesArray.put("minItems", 2);
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "string");
        fieldNamesArray.put("items", itemSchema);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection to constrain. The collection and all named fields must already exist."));
        properties.put("fieldNames", fieldNamesArray);

        Tool tool = Tool.builder()
                .name("create_unique_constraint")
                .title("Create Composite Unique Constraint")
                .description("Add a composite unique constraint covering 2+ fields on an existing collection. "
                        + "Wraps POST /api/_composite-unique-constraints. The constraint is enforced by "
                        + "PostgreSQL — subsequent inserts that duplicate the tuple return HTTP 409 Conflict. "
                        + "Idempotent: a duplicate call returns the existing constraint's name unchanged.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "fieldNames")))
                .annotations(ToolHints.write(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object fn = args.get("fieldNames");
                    if (cn == null || cn.toString().isBlank()) {
                        return error("Argument \"collectionName\" is required.");
                    }
                    if (!(fn instanceof List<?> fields) || fields.size() < 2) {
                        return error("Argument \"fieldNames\" must be an array of at least 2 field names.");
                    }
                    List<String> fieldNames = new java.util.ArrayList<>(fields.size());
                    for (Object item : fields) {
                        if (item == null || item.toString().isBlank()) {
                            return error("\"fieldNames\" entries must be non-blank strings.");
                        }
                        fieldNames.add(item.toString());
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("collectionName", cn.toString());
                    attrs.put("fieldNames", fieldNames);

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "compositeUniqueConstraints",
                            "attributes", attrs));
                    try {
                        return McpErrorMapper.toResult(
                                gateway.post("/api/_composite-unique-constraints", body));
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
