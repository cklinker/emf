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

    /**
     * Operators the worker actually honors. The set comes from
     * {@code io.kelta.runtime.query.FilterOperator} — the worker uppercases the
     * URL token then does an enum {@code valueOf}, silently dropping any name
     * that doesn't match. We mirror it exactly here so the MCP boundary
     * rejects unsupported operators with a clear message instead of
     * forwarding them to be silently dropped (which would produce confidently
     * wrong query results to an LLM consumer).
     *
     * <p>Notable absences vs. common JSON:API conventions:
     * <ul>
     *   <li>{@code IN} / {@code NIN} — the URL parser only accepts scalar
     *       values; IN is constructed programmatically by other call sites
     *       and not URL-addressable.</li>
     *   <li>{@code BETWEEN} — combine {@code GTE} + {@code LTE} on the same
     *       field.</li>
     *   <li>{@code STARTSWITH} / {@code ENDSWITH} / {@code IS_NULL} — wrong
     *       spellings; the enum names are {@code STARTS} / {@code ENDS} /
     *       {@code ISNULL}.</li>
     * </ul>
     */
    private static final List<String> ALLOWED_OPS = List.of(
            "EQ", "NEQ", "GT", "GTE", "LT", "LTE",
            "CONTAINS", "STARTS", "ENDS",
            "ICONTAINS", "ISTARTS", "IENDS", "IEQ",
            "ISNULL");

    private final GatewayHttpClient gateway;

    public QueryCollectionTool(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public SyncToolSpecification toSpecification() {
        Map<String, Object> filter = Schemas.freeObject("""
                Filter expressions, keyed by field name. Each value is an object mapping \
                OPERATOR -> value. Multiple operators on one field combine with AND (use \
                this for ranges); multiple fields combine with AND across each other. \
                IMPORTANT: there is NO OR — for "match A or B" run two queries and union \
                the results client-side.

                Supported operators (operator name is case-insensitive; values are \
                case-sensitive unless you use an I-prefixed variant):
                  EQ, NEQ                       exact / not equal
                  GT, GTE, LT, LTE              comparison (numbers, strings, ISO-8601 timestamps)
                  CONTAINS, STARTS, ENDS        substring / prefix / suffix (case-sensitive)
                  ICONTAINS, ISTARTS, IENDS,
                  IEQ                           same matches, case-insensitive
                  ISNULL                        value must be "true" or "false"

                NOT supported (the call will be rejected with an error). Workarounds:
                  • IN, NIN          — call this tool once per value, union results client-side
                  • BETWEEN          — use GTE + LTE on the same field
                  • STARTSWITH       — use STARTS (or ISTARTS for case-insensitive)
                  • ENDSWITH         — use ENDS (or IENDS)
                  • IS_NULL          — use ISNULL
                  • NOT_CONTAINS,
                    EXISTS           — not currently expressible as a single query

                Example — created in 2026 AND status equals ACTIVE:
                  { "createdAt": { "GTE": "2026-01-01T00:00:00Z", "LT": "2027-01-01T00:00:00Z" },
                    "status":    { "EQ":  "ACTIVE" } }

                Example — case-insensitive name search with a price ceiling:
                  { "name":  { "ICONTAINS": "widget" },
                    "price": { "LTE": 99.99 } }
                """);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("collection", Schemas.string(
                "Collection name as it appears in `list_collections` "
                + "(e.g. \"customers\", \"accounts\", \"orders\")."));
        properties.put("filter", filter);
        properties.put("sort", Schemas.string(
                "Comma-separated sort keys. Prefix '-' for descending. "
                + "Examples: \"lastName\"  |  \"-createdAt\"  |  \"lastName,-createdAt\"."));
        properties.put("pageSize", Schemas.integer(
                "Records per page (default 20, max 200). Set to 1 when you only need a count — "
                + "the total is in `meta.totalCount` regardless of page size.", 1, 200));
        properties.put("pageNumber", Schemas.integer(
                "1-based page index (default 1). Response `links.next` / `links.prev` indicate "
                + "further pages when present.", 1, null));
        properties.put("fields", Schemas.string(
                "Sparse fieldset — comma-separated attribute names to return per record. "
                + "Default returns all attributes. Example: \"firstName,lastName,email\"."));
        properties.put("include", Schemas.string(
                "Comma-separated relationship names to eager-load. Each related record appears "
                + "once in the top-level `included[]` array; `data[].relationships[name].data` "
                + "references them by id + type. Example: \"account,owner\"."));

        Tool tool = Tool.builder()
                .name("query_collection")
                .description("""
                        Run a JSON:API list query against a Kelta collection (GET /api/{collection}).

                        Use this for browsing or finding records by criteria. For a single record \
                        by id use `get_record`; for cross-collection full-text search use `search`. \
                        Before filtering on a field you've never seen, call `get_collection_schema` \
                        to discover field names and types.

                        Capabilities (every argument is optional except `collection`):
                          • filter  — combine any number of fields and operators. All conditions \
                        AND together; there is NO OR (run separate queries for "either / or"). \
                        Supported ops: EQ NEQ GT GTE LT LTE CONTAINS STARTS ENDS ICONTAINS \
                        ISTARTS IENDS IEQ ISNULL. See the `filter` argument for usage and \
                        workarounds for unsupported names (IN, BETWEEN, etc.).
                          • sort    — multi-key, '-' prefix for descending. Direct attributes only \
                        (cannot sort by relationship fields).
                          • page    — pageSize + pageNumber (1-based, default 20 / 1, max 1000). \
                        Total record count is always at `meta.totalCount`, regardless of pageSize.
                          • fields  — sparse fieldset: only return the columns you need.
                          • include — eager-load related records (returned in top-level \
                        `included[]`). Single hop only — nested includes like `account.owner` \
                        are not supported.

                        Returns the JSON:API envelope:
                          {
                            "data":     [ { "id": "...", "type": "<collection>",
                                            "attributes": {...}, "relationships": {...} } ],
                            "included": [ ... ],   // only when `include` is set
                            "meta":     { "totalCount": N, "pageNumber": ...,
                                          "pageSize": ..., "totalPages": ... },
                            "links":    { "self": "...", "next": "...", "prev": "..." }
                          }

                        Common patterns:

                          // count-only: pageSize=1 and read meta.totalCount
                          { "collection": "customers", "pageSize": 1 }

                          // most-recent N records of a given status
                          { "collection": "orders",
                            "filter":   { "status": { "EQ": "OPEN" } },
                            "sort":     "-createdAt",
                            "pageSize": 10 }

                          // range query (BETWEEN-style) using two ops on one field
                          { "collection": "orders",
                            "filter": { "total": { "GTE": 100, "LTE": 1000 } } }

                          // case-insensitive name search
                          { "collection": "customers",
                            "filter": { "lastName": { "ICONTAINS": "smith" } } }

                          // records missing a value
                          { "collection": "customers",
                            "filter": { "phone": { "ISNULL": "true" } } }

                          // selected fields plus an eager-loaded relationship
                          { "collection": "customers",
                            "fields":  "firstName,lastName,email",
                            "include": "account",
                            "pageSize": 25 }

                          // pagination: walk pages with pageNumber, or follow links.next
                          { "collection": "customers", "pageSize": 50, "pageNumber": 3 }
                        """)
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
                    String opError = validateFilterOperators(args.get("filter"));
                    if (opError != null) {
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(opError)))
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

    /**
     * Returns a user-facing error message if {@code filter} contains an
     * operator outside {@link #ALLOWED_OPS}, otherwise null. Validating at
     * the MCP boundary turns a silent worker drop (which produces wrong
     * results) into an actionable error the caller can correct.
     */
    static String validateFilterOperators(Object filter) {
        if (!(filter instanceof Map<?, ?> filterMap)) return null;
        for (Map.Entry<?, ?> e : filterMap.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> ops)) continue;
            for (Object opKey : ops.keySet()) {
                String op = String.valueOf(opKey).toUpperCase(Locale.ROOT);
                if (!ALLOWED_OPS.contains(op)) {
                    return "Unsupported filter operator \"" + opKey + "\" on field \""
                            + e.getKey() + "\". Supported: " + ALLOWED_OPS
                            + ". Workarounds — ranges: GTE+LTE on one field; "
                            + "case-insensitive: ICONTAINS / ISTARTS / IENDS / IEQ; "
                            + "null check: ISNULL with value \"true\"/\"false\"; "
                            + "IN / OR are not supported in a single query — "
                            + "run multiple calls and union client-side.";
                }
            }
        }
        return null;
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
