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
 * Admin tool that creates a composite UNIQUE constraint on an existing
 * collection — e.g. {@code Availability(title, provider, region)} or
 * {@code EditorialListItem(list, position)}. Wraps
 * {@code POST /api/_composite-unique-constraints}.
 *
 * <p>The single-column {@code unique} flag on {@code add_field} continues to
 * handle the per-field case; this tool is for the multi-column scenarios
 * called out in the runtime schemas where no single field is unique on its
 * own but a tuple of fields must be.
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
                "API field names (in declaration order) that together must be unique. Must list at least 2 fields.");
        fieldNamesArray.put("items", Map.of("type", "string"));
        fieldNamesArray.put("minItems", 2);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection the constraint belongs to (e.g. \"availability\")."));
        properties.put("fieldNames", fieldNamesArray);

        Tool tool = Tool.builder()
                .name("create_unique_constraint")
                .title("Create Composite Unique Constraint")
                .description(
                        "Create a multi-column UNIQUE constraint on a collection's physical table. "
                                + "Wraps POST /api/_composite-unique-constraints. The single-column case is still "
                                + "handled by add_field with unique=true; use this tool when the uniqueness spans "
                                + "two or more fields, such as Availability(title,provider,region) or "
                                + "EditorialListItem(list,position).")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "fieldNames")))
                .annotations(ToolHints.write(false, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object fns = args.get("fieldNames");
                    if (cn == null || cn.toString().isBlank()) {
                        return error("Argument \"collectionName\" is required.");
                    }
                    if (!(fns instanceof List<?> rawFieldNames) || rawFieldNames.size() < 2) {
                        return error("Argument \"fieldNames\" must be an array of at least 2 field names.");
                    }
                    List<String> fieldNames = new ArrayList<>(rawFieldNames.size());
                    for (Object o : rawFieldNames) {
                        if (o == null || o.toString().isBlank()) {
                            return error("Each entry in \"fieldNames\" must be a non-blank string.");
                        }
                        fieldNames.add(o.toString());
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("collectionName", cn.toString());
                    body.put("fieldNames", fieldNames);

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
