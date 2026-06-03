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
public class AddPicklistValueTool implements AdminTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final GatewayHttpClient gateway;

    public AddPicklistValueTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("picklist", Schemas.string(
                "Picklist name (e.g. \"stages\") or id (UUID) the value belongs to. Names are resolved before the POST."));
        properties.put("value", Schemas.string("Stored value (machine identifier, e.g. \"OPEN\")."));
        properties.put("label", Schemas.string("Display label (human-facing, e.g. \"Open\")."));
        properties.put("sortOrder", Schemas.integer(
                "Display order; lower numbers come first. Defaults to 0 if omitted.", null, null));
        properties.put("isActive", Schemas.bool("Whether the value is selectable. Defaults to true.", true));
        properties.put("isDefault", Schemas.bool("Whether this is the default selection.", false));
        properties.put("color", Schemas.string("Optional hex/CSS color (e.g. \"#00ccaa\")."));
        properties.put("description", Schemas.string("Optional description."));

        Tool tool = Tool.builder()
                .name("add_picklist_value")
                .title("Add Picklist Value")
                .description("Add a single value to an existing global picklist. Wraps POST /api/picklist-values with picklistSourceType=GLOBAL and picklistSourceId resolved from the picklist name or id.")
                .inputSchema(Schemas.object(properties, List.of("picklist", "value", "label")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object picklist = args.get("picklist");
                    Object value = args.get("value");
                    Object label = args.get("label");
                    if (picklist == null || picklist.toString().isBlank()) {
                        return error("Argument \"picklist\" is required.");
                    }
                    if (value == null || value.toString().isBlank()) {
                        return error("Argument \"value\" is required.");
                    }
                    if (label == null || label.toString().isBlank()) {
                        return error("Argument \"label\" is required.");
                    }

                    String input = picklist.toString();
                    try {
                        String picklistId;
                        if (UUID_PATTERN.matcher(input).matches()) {
                            picklistId = input;
                        } else {
                            GatewayHttpClient.Response lookup = gateway.get(
                                    "/api/global-picklists?filter[name][EQ]="
                                            + URLEncoder.encode(input, StandardCharsets.UTF_8)
                                            + "&page[size]=1");
                            if (!lookup.isSuccess()) {
                                return McpErrorMapper.toResult(lookup);
                            }
                            picklistId = extractFirstId(lookup.body());
                            if (picklistId == null) {
                                return error("No picklist found with name \"" + input + "\".");
                            }
                        }

                        Map<String, Object> attrs = new LinkedHashMap<>();
                        attrs.put("picklistSourceType", "GLOBAL");
                        attrs.put("picklistSourceId", picklistId);
                        attrs.put("value", value.toString());
                        attrs.put("label", label.toString());
                        if (args.get("sortOrder") instanceof Number n) attrs.put("sortOrder", n.intValue());
                        if (args.get("isActive") instanceof Boolean b) attrs.put("isActive", b);
                        if (args.get("isDefault") instanceof Boolean b) attrs.put("isDefault", b);
                        if (args.get("color") instanceof String s && !s.isBlank()) attrs.put("color", s);
                        if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);

                        Map<String, Object> body = Map.of("data", Map.of(
                                "type", "picklist-values",
                                "attributes", attrs));
                        return McpErrorMapper.toResult(gateway.post("/api/picklist-values", body));
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

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .isError(true)
                .content(List.of(new TextContent(message)))
                .build();
    }
}
