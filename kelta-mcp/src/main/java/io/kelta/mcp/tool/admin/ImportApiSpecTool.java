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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImportApiSpecTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public ImportApiSpecTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Schemas.string("Display name for the imported spec."));
        properties.put("raw", Schemas.string("OpenAPI spec as raw JSON or YAML text."));
        properties.put("sourceUrl", Schemas.string("Optional source URL the spec was downloaded from."));

        Tool tool = Tool.builder()
                .name("import_api_spec")
                .title("Import API Spec")
                .description("Import an external OpenAPI 3.x spec into the platform's API spec library. Operations are parsed and indexed so flows can call them. Wraps POST /api/api-specs/import. Returns a diff of added/changed/removed operations.")
                .inputSchema(Schemas.object(properties, List.of("name", "raw")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object n = args.get("name");
                    Object raw = args.get("raw");
                    if (n == null || n.toString().isBlank()
                            || raw == null || raw.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Arguments \"name\" and \"raw\" are required.")))
                                .build();
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", n.toString());
                    body.put("raw", raw.toString());
                    if (args.get("sourceUrl") instanceof String s && !s.isBlank()) body.put("sourceUrl", s);

                    try {
                        return McpErrorMapper.toResult(gateway.post("/api/api-specs/import", body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
