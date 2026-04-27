package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListCollectionsTool implements UserTool {

    private final GatewayHttpClient gateway;

    public ListCollectionsTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("includeSystem", Schemas.bool(
                "Include system collections (users, fields, layouts, etc.). Default true.",
                true));

        Tool tool = Tool.builder()
                .name("list_collections")
                .description("List all collections available in the current tenant. Returns JSON:API formatted list with collection metadata. Use this to discover what data is available before querying.")
                .inputSchema(Schemas.object(properties, List.of()))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    boolean includeSystem = readBool(request.arguments(), "includeSystem", true);
                    String path = "/api/collections";
                    if (!includeSystem) {
                        path += "?filter[isSystem][EQ]=false";
                    }
                    try {
                        return McpErrorMapper.toResult(gateway.get(path));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }

    private static boolean readBool(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null) return defaultValue;
        Object v = args.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
