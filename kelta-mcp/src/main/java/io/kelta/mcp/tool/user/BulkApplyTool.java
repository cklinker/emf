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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BulkApplyTool implements UserTool {

    private static final List<String> ALLOWED_OPS = List.of("add", "update", "remove");

    private final GatewayHttpClient gateway;

    public BulkApplyTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> opSchema = new LinkedHashMap<>();
        opSchema.put("type", "object");
        opSchema.put("description",
                "One operation. {\"op\":\"add\"|\"update\"|\"remove\", \"type\":\"<collection>\", "
                + "\"id\":\"<id>\" (required for update/remove), \"attributes\":{...} (for add/update), "
                + "\"relationships\":{...} (optional for add/update)}");
        opSchema.put("additionalProperties", true);

        Map<String, Object> opsArray = new LinkedHashMap<>();
        opsArray.put("type", "array");
        opsArray.put("description", "Ordered list of operations. Applied as a single transaction — all succeed or all roll back.");
        opsArray.put("items", opSchema);
        opsArray.put("minItems", 1);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operations", opsArray);

        Tool tool = Tool.builder()
                .name("bulk_apply")
                .title("Bulk Apply")
                .description("Apply multiple record changes atomically via /api/_atomic. All operations succeed together or none do. Use this for multi-record changes that must roll back as a unit (e.g. moving line items between orders, rewiring relationships).")
                .inputSchema(Schemas.object(properties, List.of("operations")))
                .annotations(ToolHints.write(true, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object opsObj = args.get("operations");
                    if (!(opsObj instanceof List<?> ops) || ops.isEmpty()) {
                        return error("Argument \"operations\" must be a non-empty array.");
                    }
                    for (int i = 0; i < ops.size(); i++) {
                        Object o = ops.get(i);
                        if (!(o instanceof Map<?, ?> op)) {
                            return error("operations[" + i + "] must be an object.");
                        }
                        Object opName = op.get("op");
                        if (opName == null || !ALLOWED_OPS.contains(opName.toString())) {
                            return error("operations[" + i + "].op must be one of " + ALLOWED_OPS + ".");
                        }
                        if (op.get("type") == null) {
                            return error("operations[" + i + "].type (collection name) is required.");
                        }
                        if (("update".equals(opName.toString()) || "remove".equals(opName.toString()))
                                && op.get("id") == null) {
                            return error("operations[" + i + "].id is required for update/remove.");
                        }
                    }

                    Map<String, Object> body = Map.of("atomic:operations", ops);
                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/_atomic", body));
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
