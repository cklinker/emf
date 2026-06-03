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
public class DeletePicklistTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public DeletePicklistTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("picklist", Schemas.string(
                "Picklist name or id (UUID). Names are resolved via "
                        + "GET /api/global-picklists?filter[name][EQ]=... before the delete is issued."));

        Tool tool = Tool.builder()
                .name("delete_picklist")
                .title("Delete Picklist")
                .description("Delete a global picklist. Wraps DELETE /api/global-picklists/{id}. Cascades to its picklist-values. Accepts either the picklist name or a UUID; names are looked up first.")
                .inputSchema(Schemas.object(properties, List.of("picklist")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    Object pv = args == null ? null : args.get("picklist");
                    if (pv == null || pv.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"picklist\" is required.")))
                                .build();
                    }
                    String input = pv.toString();
                    try {
                        String id;
                        if (GetPicklistTool.UUID_PATTERN.matcher(input).matches()) {
                            id = input;
                        } else {
                            GatewayHttpClient.Response lookup = gateway.get(
                                    "/api/global-picklists?filter[name][EQ]="
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
                                                "No picklist found with name \"" + input + "\".")))
                                        .build();
                            }
                        }
                        GatewayHttpClient.Response response = gateway.delete(
                                "/api/global-picklists/"
                                        + URLEncoder.encode(id, StandardCharsets.UTF_8));
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted picklist " + input + " (id=" + id
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
