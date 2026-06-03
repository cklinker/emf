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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DeleteCollectionTool implements AdminTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final GatewayHttpClient gateway;

    public DeleteCollectionTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string(
                "Collection name (e.g. \"projects\") or id (UUID). Names are resolved to ids via "
                        + "GET /api/collections?filter[name][EQ]=... before the delete is issued."));

        Tool tool = Tool.builder()
                .name("delete_collection")
                .title("Delete Collection")
                .description("Delete a collection definition and all of its records. Wraps DELETE /api/collections/{id}. Irreversible — every record in the collection is dropped along with the schema. Accepts either the collection name or a UUID; names are looked up first.")
                .inputSchema(Schemas.object(properties, List.of("collection")))
                .annotations(ToolHints.write(true, true))
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
                    String input = cv.toString();

                    try {
                        String id;
                        if (UUID_PATTERN.matcher(input).matches()) {
                            id = input;
                        } else {
                            GatewayHttpClient.Response lookup = gateway.get(
                                    "/api/collections?filter[name][EQ]="
                                            + URLEncoder.encode(input, StandardCharsets.UTF_8)
                                            + "&page[size]=1");
                            if (!lookup.isSuccess()) {
                                return McpErrorMapper.toResult(lookup);
                            }
                            id = extractFirstId(lookup.body());
                            if (id == null) {
                                return CallToolResult.builder()
                                        .isError(true)
                                        .content(List.of(new TextContent(
                                                "No collection found with name \"" + input + "\".")))
                                        .build();
                            }
                        }

                        GatewayHttpClient.Response response = gateway.delete(
                                "/api/collections/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
                        if (response.isSuccess()) {
                            return CallToolResult.builder()
                                    .content(List.of(new TextContent(
                                            "Deleted collection " + input + " (id=" + id
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

    static String extractFirstId(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode data = root.path("data");
            JsonNode first = data.isArray() && !data.isEmpty() ? data.get(0) : data;
            JsonNode id = first.path("id");
            return id.isMissingNode() || id.isNull() ? null : id.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
