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
public class DeleteUniqueConstraintTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public DeleteUniqueConstraintTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection name the constraint belongs to."));
        properties.put("indexName", Schemas.string(
                "Index name of the constraint to drop (see list_unique_constraints)."));

        Tool tool = Tool.builder()
                .name("delete_unique_constraint")
                .title("Delete Unique Constraint")
                .description("Drop a composite unique constraint by index name. Wraps DELETE /api/admin/collections/{name}/unique-constraints/{indexName}. Use list_unique_constraints to discover index names.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "indexName")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object ix = args.get("indexName");
                    if (cn == null || cn.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"collectionName\" is required.")))
                                .build();
                    }
                    if (ix == null || ix.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"indexName\" is required.")))
                                .build();
                    }
                    String collection = cn.toString();
                    String indexName = ix.toString();
                    try {
                        GatewayHttpClient.Response response = gateway.delete(
                                "/api/admin/collections/"
                                        + URLEncoder.encode(collection, StandardCharsets.UTF_8)
                                        + "/unique-constraints/"
                                        + URLEncoder.encode(indexName, StandardCharsets.UTF_8));
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Dropped constraint " + indexName + " from " + collection
                                                    + " (HTTP " + response.status().value() + ")")))
                                    .build();
                        }
                        return McpErrorMapper.toResult(response);
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
