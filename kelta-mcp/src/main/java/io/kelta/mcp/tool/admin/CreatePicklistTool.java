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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite admin tool: create a global picklist and its initial values
 * in a single Claude tool call.
 *
 * <p>Wraps:
 * <ol>
 *   <li>POST /api/globalPicklists — create the picklist</li>
 *   <li>POST /api/picklistValues (per value) — create each value</li>
 * </ol>
 */
@Component
public class CreatePicklistTool implements AdminTool {

    private final GatewayHttpClient gateway;

    public CreatePicklistTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> valueItem = new LinkedHashMap<>();
        valueItem.put("type", "object");
        valueItem.put("description",
                "{\"value\":\"...\",\"label\":\"...\",\"sortOrder\":number,\"active\":bool}");
        valueItem.put("additionalProperties", true);

        Map<String, Object> valuesArray = new LinkedHashMap<>();
        valuesArray.put("type", "array");
        valuesArray.put("description", "Initial picklist values, in display order.");
        valuesArray.put("items", valueItem);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Schemas.string("Picklist name (unique within tenant)."));
        properties.put("description", Schemas.string("Optional description."));
        properties.put("values", valuesArray);

        Tool tool = Tool.builder()
                .name("create_picklist")
                .title("Create Picklist")
                .description("Create a global picklist with its initial values. The picklist is created first; each value is then POSTed to /api/picklistValues with picklistName backreference.")
                .inputSchema(Schemas.object(properties, List.of("name", "values")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object n = args.get("name");
                    Object vs = args.get("values");
                    if (n == null || n.toString().isBlank()) {
                        return error("Argument \"name\" is required.");
                    }
                    if (!(vs instanceof List<?> values) || values.isEmpty()) {
                        return error("Argument \"values\" must be a non-empty array.");
                    }

                    Map<String, Object> attrs = new LinkedHashMap<>();
                    attrs.put("name", n.toString());
                    if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);

                    Map<String, Object> picklistBody = Map.of("data", Map.of(
                            "type", "globalPicklists",
                            "attributes", attrs));
                    GatewayHttpClient.Response picklistRes;
                    try {
                        picklistRes = gateway.post("/api/globalPicklists", picklistBody);
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                    if (!picklistRes.isSuccess()) {
                        return McpErrorMapper.toResult(picklistRes);
                    }

                    List<Map<String, Object>> valueResults = new ArrayList<>();
                    for (int i = 0; i < values.size(); i++) {
                        Object v = values.get(i);
                        if (!(v instanceof Map<?, ?> vAttrs)) {
                            valueResults.add(Map.of("index", i, "error", "value entry must be an object"));
                            continue;
                        }
                        Map<String, Object> vMap = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : vAttrs.entrySet()) {
                            vMap.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        vMap.putIfAbsent("picklistName", n.toString());
                        if (!vMap.containsKey("sortOrder")) vMap.put("sortOrder", i);

                        Map<String, Object> valueBody = Map.of("data", Map.of(
                                "type", "picklistValues",
                                "attributes", vMap));
                        GatewayHttpClient.Response vr;
                        try {
                            vr = gateway.post("/api/picklistValues", valueBody);
                        } catch (RuntimeException e) {
                            valueResults.add(Map.of("index", i, "error", e.getClass().getSimpleName()));
                            continue;
                        }
                        valueResults.add(Map.of(
                                "index", i,
                                "value", vMap.getOrDefault("value", "?"),
                                "status", vr.status() == null ? 0 : vr.status().value(),
                                "body", vr.isSuccess() ? "ok" : vr.body()));
                    }

                    String summary = "{\n  \"picklist\": " + picklistRes.body()
                            + ",\n  \"values\": " + valueResults
                            + "\n}";
                    return CallToolResult.builder()
                            .content(List.of(new TextContent(summary)))
                            .build();
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
