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
public class ListPicklistsTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public ListPicklistsTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Schemas.string(
                "Optional exact-match filter on picklist name. Omit to list all global picklists in the tenant."));
        properties.put("pageSize", Schemas.integer(
                "Page size (default 200, max 500).", 1, 500));

        Tool tool = Tool.builder()
                .name("list_picklists")
                .title("List Picklists")
                .description("List global picklists. Wraps GET /api/global-picklists. With a name filter (filter[name][EQ]=…) this is the idempotent existence check that setup scripts use before deciding whether to create_picklist.")
                .inputSchema(Schemas.object(properties, List.of()))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    StringBuilder path = new StringBuilder("/api/global-picklists?");
                    int pageSize = 200;
                    if (args.get("pageSize") instanceof Number n) {
                        pageSize = Math.max(1, Math.min(500, n.intValue()));
                    }
                    path.append("page[size]=").append(pageSize);
                    if (args.get("name") instanceof String s && !s.isBlank()) {
                        path.append("&filter[name][EQ]=")
                                .append(URLEncoder.encode(s, StandardCharsets.UTF_8));
                    }
                    try {
                        return McpErrorMapper.toResult(gateway.get(path.toString()));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
