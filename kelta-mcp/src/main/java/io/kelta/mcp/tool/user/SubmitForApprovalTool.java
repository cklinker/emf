package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SubmitForApprovalTool implements UserTool {

    private final GatewayHttpClient gateway;

    public SubmitForApprovalTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collectionId", Schemas.string(
                "Collection id (UUID) of the record being submitted. Use get_collection_schema to look up by name."));
        properties.put("recordId", Schemas.string("Record id to submit for approval."));
        properties.put("processId", Schemas.string(
                "Optional approval process id. If omitted, the platform auto-selects a matching process."));

        Tool tool = Tool.builder()
                .name("submit_for_approval")
                .description("Submit a record for approval. Wraps POST /api/approvals/submit. Returns the new approval instance id; use list_approvals to track its status.")
                .inputSchema(Schemas.object(properties, List.of("collectionId", "recordId")))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cId = args.get("collectionId");
                    Object rId = args.get("recordId");
                    if (cId == null || cId.toString().isBlank()
                            || rId == null || rId.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Arguments \"collectionId\" and \"recordId\" are required.")))
                                .build();
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("collectionId", cId.toString());
                    body.put("recordId", rId.toString());
                    Object processId = args.get("processId");
                    if (processId != null && !processId.toString().isBlank()) {
                        body.put("processId", processId.toString());
                    }

                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/approvals/submit", body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
