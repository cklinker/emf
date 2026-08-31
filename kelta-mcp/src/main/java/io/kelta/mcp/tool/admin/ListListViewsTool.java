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
public class ListListViewsTool implements AdminTool {

    private final GatewayHttpClient gateway;
    private final AdminLookups lookups;

    public ListListViewsTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
        this.lookups = new AdminLookups(gateway);
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection name or id (UUID) whose list views to return."));
        properties.put("pageSize", Schemas.integer(
                "Page size (default 200, max 200).", 1, 200));

        Tool tool = Tool.builder()
                .name("list_listviews")
                .title("List List Views")
                .description("List saved list views for a collection. Wraps GET /api/list-views?filter[collectionId][eq]={id}. Use before create_listview to check whether a view already exists.")
                .inputSchema(Schemas.object(properties, List.of("collectionName")))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    Object cn = args == null ? null : args.get("collectionName");
                    if (cn == null || cn.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"collectionName\" is required.")))
                                .build();
                    }
                    String collectionId;
                    if (GetPicklistTool.UUID_PATTERN.matcher(cn.toString()).matches()) {
                        collectionId = cn.toString();
                    } else {
                        collectionId = lookups.collectionIdByName(cn.toString());
                        if (collectionId == null) {
                            return CallToolResult.builder()
                                    .isError(true)
                                    .content(List.of(new TextContent(
                                            "Collection \"" + cn + "\" not found.")))
                                    .build();
                        }
                    }
                    int pageSize = 200;
                    if (args.get("pageSize") instanceof Number n) {
                        pageSize = Math.max(1, Math.min(200, n.intValue()));
                    }
                    String path = "/api/list-views?filter[collectionId][eq]="
                            + URLEncoder.encode(collectionId, StandardCharsets.UTF_8)
                            + "&page[size]=" + pageSize;
                    try {
                        return McpErrorMapper.toResult(gateway.get(path));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
