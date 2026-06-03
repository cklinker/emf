package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
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
                "Optional exact-match filter on picklist name. When omitted, returns all picklists."));

        Tool tool = Tool.builder()
                .name("list_picklists")
                .title("List Picklists")
                .description("List global picklists in the current tenant. Wraps GET /api/global-picklists with an optional name filter. Use this before create_picklist to check whether a picklist already exists.")
                .inputSchema(Schemas.object(properties, List.of()))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    String path = "/api/global-picklists";
                    if (args != null && args.get("name") instanceof String s && !s.isBlank()) {
                        path += "?filter[name][EQ]="
                                + URLEncoder.encode(s, StandardCharsets.UTF_8);
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
