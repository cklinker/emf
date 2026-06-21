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

/**
 * MCP user tool exposing vector similarity (semantic) search over a collection's {@code VECTOR}
 * field, so an agent can retrieve records ranked by meaning rather than keyword match — the
 * retrieval half of RAG.
 *
 * @since 1.0.0
 */
@Component
public class SemanticSearchTool implements UserTool {

    private final GatewayHttpClient gateway;

    public SemanticSearchTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string(
                "Collection name to search; it must define a VECTOR field."));
        properties.put("query", Schemas.string(
                "Natural-language text; records are ranked by semantic similarity to it."));
        properties.put("limit", Schemas.integer("Maximum results to return (default 10).", 1, 100));

        Tool tool = Tool.builder()
                .name("semantic_search")
                .title("Semantic Search")
                .description("Vector similarity search over a collection's VECTOR field. Wraps POST "
                        + "/api/{collection}/semantic-search; returns the nearest records by cosine "
                        + "distance, with per-record distances under meta. Prefer this over `search` "
                        + "when ranking by meaning rather than keyword match.")
                .inputSchema(Schemas.object(properties, List.of("collection", "query")))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cv = args.get("collection");
                    Object qv = args.get("query");
                    if (cv == null || cv.toString().isBlank()) {
                        return error("Argument \"collection\" is required.");
                    }
                    if (qv == null || qv.toString().isBlank()) {
                        return error("Argument \"query\" is required.");
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("query", qv.toString());
                    Object limit = args.get("limit");
                    if (limit != null) {
                        body.put("limit", limit);
                    }

                    String path = "/api/" + URLEncoder.encode(cv.toString(), StandardCharsets.UTF_8)
                            + "/semantic-search";
                    try {
                        return McpErrorMapper.toResult(gateway.post(path, body));
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
