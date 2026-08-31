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
public class ListValidationRulesTool implements AdminTool {

    private final GatewayHttpClient gateway;
    private final AdminLookups lookups;

    public ListValidationRulesTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
        this.lookups = new AdminLookups(gateway);
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionName", Schemas.string(
                "Collection name to filter by. Resolves the name to its id before querying. "
                + "Omit to list all validation rules in the tenant."));
        properties.put("pageSize", Schemas.integer(
                "Page size (default 200, max 200).", 1, 200));

        Tool tool = Tool.builder()
                .name("list_validation_rules")
                .title("List Validation Rules")
                .description("List validation rules, optionally filtered by collection. "
                        + "Wraps GET /api/validation-rules with an optional filter[collectionId][EQ]=… "
                        + "query param. Use this to check existing rules before create_validation_rule.")
                .inputSchema(Schemas.object(properties, List.of()))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();

                    int pageSize = 200;
                    if (args.get("pageSize") instanceof Number n) {
                        pageSize = Math.max(1, Math.min(200, n.intValue()));
                    }

                    StringBuilder path = new StringBuilder("/api/validation-rules?page[size]=").append(pageSize);

                    if (args.get("collectionName") instanceof String cn && !cn.isBlank()) {
                        String collectionId = lookups.collectionIdByName(cn);
                        if (collectionId == null) {
                            return McpErrorMapper.fromException(
                                    new IllegalArgumentException("Collection \"" + cn + "\" not found."));
                        }
                        path.append("&filter[collectionId][EQ]=")
                                .append(URLEncoder.encode(collectionId, StandardCharsets.UTF_8));
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
