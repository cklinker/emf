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
public class GetPicklistTool implements AdminTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final GatewayHttpClient gateway;

    public GetPicklistTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("picklist", Schemas.string(
                "Picklist name (e.g. \"stages\") or id (UUID). Names are resolved via "
                        + "GET /api/global-picklists?filter[name][EQ]=... before the lookup is issued."));

        Tool tool = Tool.builder()
                .name("get_picklist")
                .title("Get Picklist")
                .description("Fetch a global picklist by name or id, returned together with its values. Wraps GET /api/global-picklists/{id} plus GET /api/global-picklists/{id}/picklist-values and merges them into one response.")
                .inputSchema(Schemas.object(properties, List.of("picklist")))
                .annotations(ToolHints.read())
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
                        if (UUID_PATTERN.matcher(input).matches()) {
                            id = input;
                        } else {
                            GatewayHttpClient.Response lookup = gateway.get(
                                    "/api/global-picklists?filter[name][EQ]="
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
                                                "No picklist found with name \"" + input + "\".")))
                                        .build();
                            }
                        }

                        String encId = URLEncoder.encode(id, StandardCharsets.UTF_8);
                        GatewayHttpClient.Response picklistRes = gateway.get("/api/global-picklists/" + encId);
                        if (!picklistRes.isSuccess()) {
                            return McpErrorMapper.toResult(picklistRes);
                        }
                        GatewayHttpClient.Response valuesRes = gateway.get(
                                "/api/global-picklists/" + encId + "/picklist-values");
                        String combined = "{\n  \"picklist\": " + picklistRes.body()
                                + ",\n  \"values\": "
                                + (valuesRes.isSuccess()
                                        ? valuesRes.body()
                                        : ("\"HTTP " + (valuesRes.status() == null ? 0 : valuesRes.status().value()) + "\""))
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
