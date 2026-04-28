package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.kelta.mcp.tool.UserTool;
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
public class DeleteRecordTool implements UserTool {

    private final GatewayHttpClient gateway;

    public DeleteRecordTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string("Collection name."));
        properties.put("id", Schemas.string("Record id to delete."));

        Tool tool = Tool.builder()
                .name("delete_record")
                .title("Delete Record")
                .description("Delete a single record. Wraps DELETE /api/{collection}/{id}. Returns 204 No Content on success. Idempotent — deleting a non-existent record returns 404.")
                .inputSchema(Schemas.object(properties, List.of("collection", "id")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cv = args.get("collection");
                    Object iv = args.get("id");
                    if (cv == null || cv.toString().isBlank() || iv == null || iv.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Arguments \"collection\" and \"id\" are required.")))
                                .build();
                    }
                    String path = "/api/" + URLEncoder.encode(cv.toString(), StandardCharsets.UTF_8)
                            + "/" + URLEncoder.encode(iv.toString(), StandardCharsets.UTF_8);
                    try {
                        GatewayHttpClient.Response response = gateway.delete(path);
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted " + cv + "/" + iv + " (HTTP " + response.status().value() + ")")))
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
