package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.AdminTool;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
import io.kelta.mcp.tool.UserTool;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetCollectionSchemaTool implements UserTool, AdminTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                .title("Get Collection Schema")
                .description("Return the full schema for a collection: metadata + all field definitions (type, required, unique, validation rules). Use this before constructing a query or create_record call to know what attributes are available.")
                .inputSchema(Schemas.object(properties, List.of("collection")))
                .annotations(ToolHints.read())
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
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

                        // The fields system collection links each field to its parent
                        // by `collectionId` (a UUID), NOT by `collectionName`. Filtering
                        // on `collectionName` returns HTTP 400 from the worker because
                        // that column doesn't exist on the FieldDefinition entity, so
                        // we have to extract the collection's id from the first
                        // response and use that.
                        String collectionId = extractCollectionId(collectionRes.body());
                        GatewayHttpClient.Response fieldsRes = collectionId == null
                                ? null
                                : gateway.get(
                                        "/api/fields?filter[collectionId][EQ]="
                                                + URLEncoder.encode(collectionId, StandardCharsets.UTF_8)
                                                + "&page[size]=200");

                        String fieldsBody = (fieldsRes != null && fieldsRes.isSuccess())
                                ? fieldsRes.body()
                                : "null";
                        String combined = "{\n  \"collection\": "
                                + collectionRes.body()
                                + ",\n  \"fields\": "
                                + fieldsBody
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

    /**
     * Pull {@code data.id} out of a JSON:API single-resource response body.
     * Returns null on any parse failure or if the id is missing — callers
     * fall back to skipping the fields fetch in that case.
     */
    static String extractCollectionId(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode id = root.path("data").path("id");
            return id.isMissingNode() || id.isNull() ? null : id.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
