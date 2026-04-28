package io.kelta.mcp.tool.user;

import io.kelta.mcp.client.GatewayHttpClient;
import io.kelta.mcp.error.McpErrorMapper;
import io.kelta.mcp.tool.Schemas;
import io.kelta.mcp.tool.ToolHints;
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
public class UpdateRecordTool implements UserTool {

    private final GatewayHttpClient gateway;

    public UpdateRecordTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string("Collection name."));
        properties.put("id", Schemas.string("Record id (UUID or display field value)."));
        properties.put("attributes", Schemas.freeObject(
                "Field values to update. Only the keys you include are modified — other fields are untouched (PATCH semantics)."));
        properties.put("relationships", Schemas.freeObject(
                "Optional JSON:API relationships object to replace; each entry maps a relationship name to a JSON:API resource identifier."));

        Tool tool = Tool.builder()
                .name("update_record")
                .title("Update Record")
                .description("Update fields on a single record. Wraps PATCH /api/{collection}/{id}. Only the attributes you supply are modified — omitted fields keep their current values.")
                .inputSchema(Schemas.object(properties, List.of("collection", "id")))
                .annotations(ToolHints.write(true, true))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cv = args.get("collection");
                    Object iv = args.get("id");
                    if (cv == null || cv.toString().isBlank() || iv == null || iv.toString().isBlank()) {
                        return error("Arguments \"collection\" and \"id\" are required.");
                    }
                    Object av = args.get("attributes");
                    Object rv = args.get("relationships");
                    boolean hasAttrs = av instanceof Map<?, ?> am && !am.isEmpty();
                    boolean hasRels = rv instanceof Map<?, ?> rm && !rm.isEmpty();
                    if (!hasAttrs && !hasRels) {
                        return error("Provide at least one of \"attributes\" or \"relationships\" to update.");
                    }

                    String collection = cv.toString();
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("type", collection);
                    data.put("id", iv.toString());
                    if (hasAttrs) data.put("attributes", av);
                    if (hasRels) data.put("relationships", rv);
                    Map<String, Object> body = Map.of("data", data);

                    String path = "/api/" + URLEncoder.encode(collection, StandardCharsets.UTF_8)
                            + "/" + URLEncoder.encode(iv.toString(), StandardCharsets.UTF_8);
                    try {
                        return McpErrorMapper.toResult(gateway.patch(path, body));
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
