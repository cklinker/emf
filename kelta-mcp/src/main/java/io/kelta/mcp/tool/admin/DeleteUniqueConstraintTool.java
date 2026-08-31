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
                "Collection name (e.g. \"availabilities\")."));
        properties.put("indexName", Schemas.string(
                "Index name of the constraint to drop (e.g. \"uniq_availabilities_title_provider\"). "
                + "Use list_unique_constraints to find it."));

        Tool tool = Tool.builder()
                .name("delete_unique_constraint")
                .title("Delete Unique Constraint")
                .description("Drop a composite unique constraint from a collection. "
                        + "Wraps DELETE /api/admin/collections/{name}/unique-constraints/{indexName}. "
                        + "Use list_unique_constraints to find the indexName first.")
                .inputSchema(Schemas.object(properties, List.of("collectionName", "indexName")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    Object idx = args.get("indexName");
                    if (cn == null || cn.toString().isBlank()) {
                        return error("Argument \"collectionName\" is required.");
                    }
                    if (idx == null || idx.toString().isBlank()) {
                        return error("Argument \"indexName\" is required.");
                    }
                    String path = "/api/admin/collections/"
                            + URLEncoder.encode(cn.toString(), StandardCharsets.UTF_8)
                            + "/unique-constraints/"
                            + URLEncoder.encode(idx.toString(), StandardCharsets.UTF_8);
                    try {
                        GatewayHttpClient.Response response = gateway.delete(path);
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted unique constraint " + idx + " from "
                                                    + cn + " (HTTP " + response.status().value() + ")")))
                                    .build();
                        }
                        return McpErrorMapper.toResult(response);
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
