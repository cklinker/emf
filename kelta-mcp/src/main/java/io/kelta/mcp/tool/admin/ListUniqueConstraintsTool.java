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
public class ListUniqueConstraintsTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public ListUniqueConstraintsTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection name (e.g. \"availabilities\"). "
                + "Returns all composite unique constraints on that collection."));

        Tool tool = Tool.builder()
                .name("list_unique_constraints")
                .title("List Unique Constraints")
                .description("List composite unique constraints on a collection. "
                        + "Wraps GET /api/admin/collections/{name}/unique-constraints. "
                        + "Each entry includes the indexName needed to call delete_unique_constraint.")
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
                    String path = "/api/admin/collections/"
                            + URLEncoder.encode(cn.toString(), StandardCharsets.UTF_8)
                            + "/unique-constraints";
                    try {
                        return McpErrorMapper.toResult(gateway.get(path));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
