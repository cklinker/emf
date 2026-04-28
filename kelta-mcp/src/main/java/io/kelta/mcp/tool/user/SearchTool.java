package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
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
public class SearchTool implements UserTool {

    private final GatewayHttpClient gateway;

    public SearchTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Schemas.string("Free-text query (matches against searchable fields across all collections)."));
        properties.put("limit", Schemas.integer("Maximum results to return (default 20).", 1, 100));

        Tool tool = Tool.builder()
                .name("search")
                .title("Search")
                .description("Cross-collection full-text search. Returns hits with collection name, record id, and matched snippet. Useful when you don't know which collection holds the answer.")
                .inputSchema(Schemas.object(properties, List.of("query")))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object q = args.get("query");
                    if (q == null || q.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"query\" is required.")))
                                .build();
                    }
                    String path = "/api/_search?q="
                            + URLEncoder.encode(q.toString(), StandardCharsets.UTF_8);
                    Object limit = args.get("limit");
                    if (limit != null) {
                        path += "&limit=" + URLEncoder.encode(limit.toString(), StandardCharsets.UTF_8);
                    }
                    try {
                        return McpErrorMapper.toResult(gateway.get(path));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
