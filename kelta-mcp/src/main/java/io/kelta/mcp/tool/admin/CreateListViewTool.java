package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CreateListViewTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public CreateListViewTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string("Collection the list view applies to."));
        properties.put("name", Schemas.string("List view name (unique within collection)."));
        properties.put("displayedFields", Schemas.string(
                "Comma-separated field names to show as columns, in display order."));
        properties.put("filter", Schemas.freeObject(
                "Optional saved-filter expression in JSON:API filter shape, e.g. {\"status\":{\"EQ\":\"OPEN\"}}."));
        properties.put("sort", Schemas.string("Default sort, e.g. \"-createdAt\"."));
        properties.put("isDefault", Schemas.bool("Whether this is the default list view.", false));

        Tool tool = Tool.builder()
                .name("create_listview")
                .title("Create List View")
                .description("Create a saved list view (column set + filter + sort) for a collection. Wraps POST /api/listViews.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "name", "displayedFields")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object name = args.get("name");
                    Object df = args.get("displayedFields");
                    if (cn == null || cn.toString().isBlank()
                            || name == null || name.toString().isBlank()
                            || df == null || df.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Arguments \"collectionName\", \"name\", and \"displayedFields\" are required.")))
                                .build();
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("collectionName", cn.toString());
                    attrs.put("name", name.toString());
                    attrs.put("displayedFields", df.toString());
                    if (args.get("filter") instanceof Map<?, ?> f) attrs.put("filter", f);
                    if (args.get("sort") instanceof String s && !s.isBlank()) attrs.put("sort", s);
                    if (args.get("isDefault") instanceof Boolean b) attrs.put("isDefault", b);

                    Map<String, Object> body = Map.of("data", Map.of(
                            "type", "listViews",
                            "attributes", attrs));
                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/listViews", body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
