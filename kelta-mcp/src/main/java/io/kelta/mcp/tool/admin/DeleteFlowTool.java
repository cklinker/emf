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
public class DeleteFlowTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public DeleteFlowTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Schemas.string(
                "Flow id (UUID) or name. Names are resolved via "
                        + "GET /api/flows?filter[name][EQ]=... before the delete is issued."));

        Tool tool = Tool.builder()
                .name("delete_flow")
                .title("Delete Flow")
                .description("Delete an automation flow. Wraps DELETE /api/flows/{id}. Flows with recorded run history may be rejected by the gateway — that error is surfaced as-is. Accepts either the flow UUID or name; names are resolved first.")
                .inputSchema(Schemas.object(properties, List.of("id")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    Object iv = args == null ? null : args.get("id");
                    if (iv == null || iv.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"id\" is required.")))
                                .build();
                    }
                    String input = iv.toString();
                    try {
                        String id;
                        if (GetPicklistTool.UUID_PATTERN.matcher(input).matches()) {
                            id = input;
                        } else {
                            GatewayHttpClient.Response lookup = gateway.get(
                                    "/api/flows?filter[name][EQ]="
                                            + URLEncoder.encode(input, StandardCharsets.UTF_8)
                                            + "&page[size]=1");
                            if (!lookup.isSuccess()) {
                                return McpErrorMapper.toResult(lookup);
                            }
                            id = DeleteCollectionTool.extractFirstId(lookup.body());
                            if (id == null) {
                                return CallToolResult.builder()
                                        .isError(true)
                                        .content(List.of(new TextContent(
                                                "No flow found with name \"" + input + "\".")))
                                        .build();
                            }
                        }
                        GatewayHttpClient.Response response = gateway.delete(
                                "/api/flows/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted flow " + input + " (id=" + id
                                                    + ", HTTP " + response.status().value() + ")")))
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
