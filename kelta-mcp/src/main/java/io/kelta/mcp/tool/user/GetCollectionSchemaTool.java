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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetCollectionSchemaTool implements UserTool {

    private final GatewayHttpClient gateway;

    public GetCollectionSchemaTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string(
                "Collection name or ID (e.g. \"accounts\" or a UUID). Looked up via /api/collections."));

        Tool tool = Tool.builder()
                .name("get_collection_schema")
                .description("Return the full schema for a collection: metadata + all field definitions (type, required, unique, validation rules). Use this before constructing a query or create_record call to know what attributes are available.")
                .inputSchema(Schemas.object(properties, List.of("collection")))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    Object cv = args == null ? null : args.get("collection");
                    if (cv == null || cv.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"collection\" is required.")))
                                .build();
                    }
                    String collection = cv.toString();

                    try {
                        GatewayHttpClient.Response collectionRes = gateway.get(
                                "/api/collections/" + URLEncoder.encode(collection, StandardCharsets.UTF_8));
                        if (!collectionRes.isSuccess()) {
                            return McpErrorMapper.toResult(collectionRes);
                        }

                        GatewayHttpClient.Response fieldsRes = gateway.get(
                                "/api/fields?filter[collectionName][EQ]="
                                        + URLEncoder.encode(collection, StandardCharsets.UTF_8)
                                        + "&page[size]=200");

                        String combined = "{\n  \"collection\": "
                                + collectionRes.body()
                                + ",\n  \"fields\": "
                                + (fieldsRes.isSuccess() ? fieldsRes.body() : "null")
                                + "\n}";
                        return CallToolResult.builder()
                                .content(List.of(new TextContent(combined)))
                                .build();
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }
}
