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

/**
 * Materializes an imported OpenAPI GET operation as an external-rest virtual
 * collection (Rec 4f). Wraps
 * {@code POST /api/api-specs/{specId}/operations/{operationId}/materialize}.
 */
@Component
public class MaterializeApiCollectionTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public MaterializeApiCollectionTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("specId", Schemas.string("ID of the imported API spec (from import_api_spec)."));
        properties.put("operationId", Schemas.string(
                "syntheticOpId of the GET operation to back the collection."));
        properties.put("collectionName", Schemas.string(
                "Name for the new collection — lowercase, ^[a-z][a-z0-9_]*$."));
        properties.put("displayName", Schemas.string("Optional display name."));
        properties.put("dataPath", Schemas.string(
                "Optional JSON path to the row array in the response (defaults to the inferred path)."));
        properties.put("idAttribute", Schemas.string(
                "Optional response attribute to use as the record id (defaults to 'id' or the first field)."));
        properties.put("credentialRef", Schemas.string(
                "Optional credential-vault reference for authenticating to the external API."));

        Tool tool = Tool.builder()
                .name("materialize_api_collection")
                .title("Materialize API Collection")
                .description("Create an external-rest virtual collection from an imported OpenAPI GET "
                        + "operation. Fields are derived from the operation's response schema; the "
                        + "collection is served live from the external API through the standard JSON:API "
                        + "surface (no physical table). Requires a spec imported via import_api_spec.")
                .inputSchema(Schemas.object(properties, List.of("specId", "operationId", "collectionName")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object specId = args.get("specId");
                    Object opId = args.get("operationId");
                    Object collectionName = args.get("collectionName");
                    if (specId == null || specId.toString().isBlank()
                            || opId == null || opId.toString().isBlank()
                            || collectionName == null || collectionName.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Arguments \"specId\", \"operationId\" and \"collectionName\" are required.")))
                                .build();
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("collectionName", collectionName.toString());
                    if (args.get("displayName") instanceof String s && !s.isBlank()) body.put("displayName", s);
                    if (args.get("dataPath") instanceof String s && !s.isBlank()) body.put("dataPath", s);
                    if (args.get("idAttribute") instanceof String s && !s.isBlank()) body.put("idAttribute", s);
                    if (args.get("credentialRef") instanceof String s && !s.isBlank()) body.put("credentialRef", s);

                    String path = "/api/api-specs/" + enc(specId.toString())
                            + "/operations/" + enc(opId.toString()) + "/materialize";
                    try {
                        return McpErrorMapper.toResult(gateway.post(path, body));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }

    private static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }
}
