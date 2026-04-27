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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class QueryCollectionTool implements UserTool {

    private static final List<String> ALLOWED_OPS = List.of(
            "EQ", "NEQ", "GT", "GTE", "LT", "LTE",
            "IN", "NIN", "CONTAINS", "NOT_CONTAINS",
            "STARTSWITH", "ENDSWITH", "BETWEEN", "EXISTS", "IS_NULL");

    private final GatewayHttpClient gateway;

    public QueryCollectionTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> filter = Schemas.freeObject(
                "Filter expressions, keyed by field name. Each value is an object mapping operator -> value. "
                + "Allowed operators: " + ALLOWED_OPS + ". Example: {\"status\":{\"EQ\":\"ACTIVE\"},"
                + " \"createdAt\":{\"GTE\":\"2024-01-01T00:00:00Z\"}}.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string(
                "Collection name (e.g. \"accounts\", \"users\")."));
        properties.put("filter", filter);
        properties.put("sort", Schemas.string(
                "Comma-separated sort fields. Prefix with '-' for descending. Example: \"lastName,-createdAt\"."));
        properties.put("pageSize", Schemas.integer(
                "Records per page (default 20).", 1, 200));
        properties.put("pageNumber", Schemas.integer(
                "Page number, 1-based (default 1).", 1, null));
        properties.put("fields", Schemas.string(
                "Comma-separated field names to include. Default: all."));
        properties.put("include", Schemas.string(
                "Comma-separated relationship names to include in the response."));

        Tool tool = Tool.builder()
                .name("query_collection")
                .description("List records from a collection with JSON:API filters/sort/paging. Returns the JSON:API list response with `data` and `meta` keys. Prefer this for browsing; use get_record for fetching a known id.")
                .inputSchema(Schemas.object(properties, List.of("collection")))
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments();
                    if (args == null) args = Map.of();
                    Object cv = args.get("collection");
                    if (cv == null || cv.toString().isBlank()) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent("Argument \"collection\" is required.")))
                                .build();
                    }
                    String collection = cv.toString();
                    String query = buildQueryString(args);
                    String path = "/api/" + URLEncoder.encode(collection, StandardCharsets.UTF_8)
                            + (query.isEmpty() ? "" : "?" + query);
                    try {
                        return McpErrorMapper.toResult(gateway.get(path));
                    } catch (RuntimeException e) {
                        return McpErrorMapper.fromException(e);
                    }
                })
                .build();
    }

    static String buildQueryString(Map<String, Object> args) {
        List<String> parts = new ArrayList<>();
        Object filter = args.get("filter");
        if (filter instanceof Map<?, ?> filterMap) {
            for (Map.Entry<?, ?> e : filterMap.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> ops)) continue;
                String fieldName = String.valueOf(e.getKey());
                for (Map.Entry<?, ?> op : ops.entrySet()) {
                    String opName = String.valueOf(op.getKey()).toUpperCase(Locale.ROOT);
                    if (!ALLOWED_OPS.contains(opName)) continue;
                    parts.add("filter[" + enc(fieldName) + "][" + opName + "]=" + enc(String.valueOf(op.getValue())));
                }
            }
        }
        Object sort = args.get("sort");
        if (sort != null && !sort.toString().isBlank()) {
            parts.add("sort=" + enc(sort.toString()));
        }
        Object pageSize = args.get("pageSize");
        if (pageSize != null) parts.add("page[size]=" + enc(pageSize.toString()));
        Object pageNumber = args.get("pageNumber");
        if (pageNumber != null) parts.add("page[number]=" + enc(pageNumber.toString()));
        Object fields = args.get("fields");
        if (fields != null && !fields.toString().isBlank()) parts.add("fields=" + enc(fields.toString()));
        Object include = args.get("include");
        if (include != null && !include.toString().isBlank()) parts.add("include=" + enc(include.toString()));
        return String.join("&", parts);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
