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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite admin tool: build a complete page layout (pageLayout +
 * sections + per-section fields) in a single Claude tool call.
 *
 * <p>Wraps three underlying endpoints:
 * <ol>
 *   <li>POST /api/pageLayouts — layout container</li>
 *   <li>POST /api/layoutSections — one per section, child of the layout</li>
 *   <li>POST /api/layoutFields — one per field, child of its section</li>
 * </ol>
 *
 * <p>The layout is created first; if it fails, no children are
 * attempted. If a section fails, its child fields are skipped but
 * other sections still proceed. The result documents which pieces
 * succeeded so a follow-up call can recover.
 */
@Component
public class CreateLayoutTool implements AdminTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GatewayHttpClient gateway;

    public CreateLayoutTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> sectionFieldItem = new LinkedHashMap<>();
        sectionFieldItem.put("type", "object");
        sectionFieldItem.put("description",
                "{\"fieldName\":\"...\",\"sortOrder\":number,\"width\":\"full|half|third\","
                + "\"readOnly\":bool,\"required\":bool}");
        sectionFieldItem.put("additionalProperties", true);

        Map<String, Object> sectionFieldsArr = new LinkedHashMap<>();
        sectionFieldsArr.put("type", "array");
        sectionFieldsArr.put("description", "Fields in display order within this section.");
        sectionFieldsArr.put("items", sectionFieldItem);

        Map<String, Object> sectionItem = new LinkedHashMap<>();
        sectionItem.put("type", "object");
        sectionItem.put("description",
                "{\"sectionName\":\"...\",\"columns\":1|2|3,\"sortOrder\":number,"
                + "\"fields\":[{...}]}");
        Map<String, Object> sectionProps = new LinkedHashMap<>();
        sectionProps.put("sectionName", Schemas.string("Section heading."));
        sectionProps.put("columns", Schemas.integer("Column count (1-3, default 2).", 1, 3));
        sectionProps.put("sortOrder", Schemas.integer("Display order (auto-assigned if omitted).", 0, null));
        sectionProps.put("fields", sectionFieldsArr);
        sectionItem.put("properties", sectionProps);
        sectionItem.put("additionalProperties", true);

        Map<String, Object> sectionsArr = new LinkedHashMap<>();
        sectionsArr.put("type", "array");
        sectionsArr.put("description", "Sections in display order. Each section contains its own field list.");
        sectionsArr.put("items", sectionItem);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Schemas.string("Layout name (unique within collection)."));
        properties.put("collectionName", Schemas.string("Collection this layout displays."));
        properties.put("recordTypeName", Schemas.string(
                "Optional record type the layout applies to (omit for collection-wide)."));
        properties.put("isDefault", Schemas.bool("Whether this is the default layout for the collection.", false));
        properties.put("sections", sectionsArr);

        Tool tool = Tool.builder()
                .name("create_layout")
                .title("Create Layout")
                .description("Build a complete page layout in one call: layout + sections + fields per section. The layout is created first; sections and their fields follow. Returns a summary of what was created and any per-piece failures.")
                .inputSchema(Schemas.object(properties, List.of("name", "collectionName", "sections")))
                .annotations(ToolHints.write(false, false))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object n = args.get("name");
                    Object cn = args.get("collectionName");
                    Object s = args.get("sections");
                    if (n == null || n.toString().isBlank()
                            || cn == null || cn.toString().isBlank()) {
                        return error("Arguments \"name\" and \"collectionName\" are required.");
                    }
                    if (!(s instanceof List<?> sections)) {
                        return error("Argument \"sections\" must be an array.");
                    }

                    Map<String, Object> layoutAttrs = new LinkedHashMap<>();
                    layoutAttrs.put("name", n.toString());
                    layoutAttrs.put("collectionName", cn.toString());
                    if (args.get("recordTypeName") instanceof String r && !r.isBlank()) layoutAttrs.put("recordTypeName", r);
                    if (args.get("isDefault") instanceof Boolean b) layoutAttrs.put("isDefault", b);

                    Map<String, Object> layoutBody = Map.of("data", Map.of(
                            "type", "pageLayouts",
                            "attributes", layoutAttrs));

                    GatewayHttpClient.Response layoutRes;
                    try {
                        layoutRes = gateway.post("/api/pageLayouts", layoutBody);
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                    if (!layoutRes.isSuccess()) {
                        return McpErrorMapper.toResult(layoutRes);
                    }
                    String layoutId = extractId(layoutRes.body());

                    List<Map<String, Object>> sectionResults = new ArrayList<>();
                    for (int i = 0; i < sections.size(); i++) {
                        Map<String, Object> sectionResult = createSection(layoutId, i, sections.get(i));
                        sectionResults.add(sectionResult);
                    }

                    String summary = "{\n  \"layout\": " + layoutRes.body()
                            + ",\n  \"sections\": " + sectionResults
                            + "\n}";
                    return CallToolResult.builder()
                            .content(List.of(new TextContent(summary)))
                            .build();
                })
                .build();
    }

    private Map<String, Object> createSection(String layoutId, int index, Object sectionRaw) {
        if (!(sectionRaw instanceof Map<?, ?> sectionMap)) {
            return Map.of("index", index, "error", "section entry must be an object");
        }
        Map<String, Object> sectionAttrs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : sectionMap.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (!"fields".equals(key)) sectionAttrs.put(key, e.getValue());
        }
        if (layoutId != null) sectionAttrs.put("pageLayoutId", layoutId);
        sectionAttrs.putIfAbsent("sortOrder", index);

        Map<String, Object> sectionBody = Map.of("data", Map.of(
                "type", "layoutSections",
                "attributes", sectionAttrs));
        GatewayHttpClient.Response sectionRes;
        try {
            sectionRes = gateway.post("/api/layoutSections", sectionBody);
        } catch (RuntimeException e) {
            return Map.of("index", index, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (!sectionRes.isSuccess()) {
            return Map.of(
                    "index", index,
                    "sectionName", sectionAttrs.getOrDefault("sectionName", "?"),
                    "status", sectionRes.status() == null ? 0 : sectionRes.status().value(),
                    "body", sectionRes.body());
        }

        String sectionId = extractId(sectionRes.body());
        Object fieldsRaw = sectionMap.get("fields");
        List<Map<String, Object>> fieldResults = new ArrayList<>();
        if (fieldsRaw instanceof List<?> fields) {
            for (int j = 0; j < fields.size(); j++) {
                fieldResults.add(createLayoutField(sectionId, j, fields.get(j)));
            }
        }
        return Map.of(
                "index", index,
                "sectionName", sectionAttrs.getOrDefault("sectionName", "?"),
                "status", sectionRes.status() == null ? 0 : sectionRes.status().value(),
                "fields", fieldResults);
    }

    private Map<String, Object> createLayoutField(String sectionId, int index, Object fieldRaw) {
        if (!(fieldRaw instanceof Map<?, ?> fieldMap)) {
            return Map.of("index", index, "error", "field entry must be an object");
        }
        Map<String, Object> fieldAttrs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : fieldMap.entrySet()) {
            fieldAttrs.put(String.valueOf(e.getKey()), e.getValue());
        }
        if (sectionId != null) fieldAttrs.put("layoutSectionId", sectionId);
        fieldAttrs.putIfAbsent("sortOrder", index);

        Map<String, Object> body = Map.of("data", Map.of(
                "type", "layoutFields",
                "attributes", fieldAttrs));
        GatewayHttpClient.Response fr;
        try {
            fr = gateway.post("/api/layoutFields", body);
        } catch (RuntimeException e) {
            return Map.of("index", index, "error", e.getClass().getSimpleName());
        }
        return Map.of(
                "index", index,
                "fieldName", fieldAttrs.getOrDefault("fieldName", "?"),
                "status", fr.status() == null ? 0 : fr.status().value(),
                "body", fr.isSuccess() ? "ok" : fr.body());
    }

    private static String extractId(String body) {
        if (body == null) return null;
        try {
            JsonNode n = OBJECT_MAPPER.readTree(body);
            JsonNode id = n.path("data").path("id");
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
