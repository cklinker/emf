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
                "Collection name whose validation rules to list."));
        properties.put("pageSize", Schemas.integer(
                "Page size (default 200, max 200).", 1, 200));

        Tool tool = Tool.builder()
                .name("list_validation_rules")
                .title("List Validation Rules")
                .description("List all validation rules for a collection. Wraps GET /api/validation-rules?filter[collectionId][eq]={id}. Use this to check what rules exist before creating a new one.")
                .inputSchema(Schemas.object(properties, List.of("collectionName")))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cn = args.get("collectionName");
                    if (cn == null || cn.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"collectionName\" is required.")))
                                .build();
                    }
                    int pageSize = 200;
                    if (args.get("pageSize") instanceof Number n) {
                        pageSize = Math.max(1, Math.min(200, n.intValue()));
                    }
                    try {
                        String collectionId = lookups.collectionIdByName(cn.toString());
                        if (collectionId == null) {
                            return CallToolResult.builder()
                                    .isError(true)
                                    .content(List.of(new TextContent("Collection \"" + cn + "\" not found.")))
                                    .build();
                        }
                        String path = "/api/validation-rules?filter[collectionId][eq]="
                                + URLEncoder.encode(collectionId, StandardCharsets.UTF_8)
                                + "&page[size]=" + pageSize;
                        return McpErrorMapper.toResult(gateway.get(path));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
