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
public class CreateRecordTool implements UserTool {

    private final GatewayHttpClient gateway;

    public CreateRecordTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string("Collection name (e.g. \"accounts\")."));
        properties.put("attributes", Schemas.freeObject(
                "Field values for the new record, keyed by field name. Reference get_collection_schema to know what fields exist."));
        properties.put("relationships", Schemas.freeObject(
                "Optional JSON:API relationships object. Each entry maps a relationship name to {\"data\": {\"type\": \"...\", \"id\": \"...\"}} or an array thereof for to-many."));

        Tool tool = Tool.builder()
                .name("create_record")
                .title("Create Record")
                .description("Create a single record in a collection. Wraps POST /api/{collection} in JSON:API format. Returns the created record on success (HTTP 201).")
                .inputSchema(Schemas.object(properties, List.of("collection", "attributes")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cv = args.get("collection");
                    Object av = args.get("attributes");
                    if (cv == null || cv.toString().isBlank()) {
                        return error("Argument \"collection\" is required.");
                    }
                    if (!(av instanceof Map<?, ?> attributes) || attributes.isEmpty()) {
                        return error("Argument \"attributes\" must be a non-empty object.");
                    }
                    String collection = cv.toString();

                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", collection);
                    data.put("attributes", attributes);
                    Object rel = args.get("relationships");
                    if (rel instanceof Map<?, ?> rmap && !rmap.isEmpty()) {
                        data.put("relationships", rmap);
                    }
                    Map<String, Object> body = Map.of("data", data);

                    String path = "/api/" + URLEncoder.encode(collection, StandardCharsets.UTF_8);
                    try {
                        return McpErrorMapper.toResult(gateway.post(path, body));
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
