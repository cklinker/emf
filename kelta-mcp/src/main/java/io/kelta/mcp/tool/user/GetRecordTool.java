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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetRecordTool implements UserTool {

    private final GatewayHttpClient gateway;

    public GetRecordTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string("Collection name."));
        properties.put("id", Schemas.string(
                "Record id (UUID), or display field value if the collection has one (e.g. email for users)."));
        properties.put("include", Schemas.string(
                "Comma-separated relationship names to include in the response."));

        Tool tool = Tool.builder()
                .name("get_record")
                .title("Get Record")
                .description("Fetch a single record by id from a collection. Returns the JSON:API single-resource response.")
                .inputSchema(Schemas.object(properties, List.of("collection", "id")))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cv = args.get("collection");
                    Object iv = args.get("id");
                    if (cv == null || cv.toString().isBlank() || iv == null || iv.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Arguments \"collection\" and \"id\" are required.")))
                                .build();
                    }
                    String path = "/api/" + URLEncoder.encode(cv.toString(), StandardCharsets.UTF_8)
                            + "/" + URLEncoder.encode(iv.toString(), StandardCharsets.UTF_8);
                    Object include = args.get("include");
                    if (include != null && !include.toString().isBlank()) {
                        path += "?include=" + URLEncoder.encode(include.toString(), StandardCharsets.UTF_8);
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
