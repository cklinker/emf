package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Translates MCP-friendly field-creation arguments into the JSON:API body the
 * worker's {@code POST /api/fields} endpoint expects. Shared by {@link AddFieldTool}
 * and {@link CreateCollectionTool} so both go through the same alias mapping
 * and per-type payload assembly.
 */
final class FieldBodyBuilder {

    static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final GatewayHttpClient gateway;

    FieldBodyBuilder(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    /** Builds the body, or returns a {@link Result} carrying a user-readable error. */
    Result build(Map<String, Object> args) {
        Object fn = args.get("fieldName");
        Object t = args.get("type");
        if (fn == null || fn.toString().isBlank()
                || t == null || t.toString().isBlank()) {
            return Result.error("Arguments \"fieldName\" and \"type\" are required.");
        }

        String collectionId = resolveCollectionId(args);
        if (collectionId == null) {
            return Result.error("Provide either \"collectionId\" or \"collectionName\".");
        }

        String nativeType = resolveNativeType(t.toString());
        if (nativeType == null) {
            return Result.error("Unknown field type \"" + t + "\". See the tool description for accepted aliases.");
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("collectionId", collectionId);
        attrs.put("name", fn.toString());
        attrs.put("type", nativeType);
        if (args.get("displayName") instanceof String s && !s.isBlank()) attrs.put("displayName", s);
        if (args.get("required") instanceof Boolean b) attrs.put("required", b);
        if (args.get("unique") instanceof Boolean b) attrs.put("uniqueConstraint", b);
        if (args.get("indexed") instanceof Boolean b) attrs.put("indexed", b);
        if (args.get("searchable") instanceof Boolean b) attrs.put("searchable", b);
        if (args.get("description") instanceof String s && !s.isBlank()) attrs.put("description", s);
        if (args.get("defaultValue") instanceof String s) attrs.put("defaultValue", s);
        if (args.get("validation") instanceof Map<?, ?> v) attrs.put("constraints", v);

        Map<String, Object> relationships = new LinkedHashMap<>();

        if ("PICKLIST".equals(nativeType) || "MULTI_PICKLIST".equals(nativeType)) {
            String err = applyPicklistConfig(args, attrs);
            if (err != null) return Result.error(err);
        } else if (isLookupType(nativeType)) {
            String err = applyReferenceRelationship(args, attrs, relationships);
            if (err != null) return Result.error(err);
        } else if ("VECTOR".equals(nativeType)) {
            String err = applyVectorConfig(args, attrs);
            if (err != null) return Result.error(err);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "fields");
        data.put("attributes", attrs);
        if (!relationships.isEmpty()) {
            data.put("relationships", relationships);
        }
        return Result.ok(Map.of("data", data));
    }

    String resolveCollectionId(Map<String, Object> args) {
        if (args.get("collectionId") instanceof String cid && !cid.isBlank()) {
            return cid;
        }
        if (args.get("collectionName") instanceof String cn && !cn.isBlank()) {
            if (UUID_PATTERN.matcher(cn).matches()) {
                return cn;
            }
            return lookupCollectionIdByName(cn);
        }
        return null;
    }

    String lookupCollectionIdByName(String collectionName) {
        String path = "/api/collections?filter[name][eq]="
                + URLEncoder.encode(collectionName, StandardCharsets.UTF_8);
        GatewayHttpClient.Response res = gateway.get(path);
        if (!res.isSuccess() || res.body() == null) {
            return null;
        }
        return extractFirstId(res.body());
    }

    static String extractFirstId(String json) {
        if (json == null) return null;
        int dataIdx = json.indexOf("\"data\"");
        if (dataIdx < 0) return null;
        int idIdx = json.indexOf("\"id\"", dataIdx);
        if (idIdx < 0) return null;
        int openQuote = json.indexOf('"', idIdx + 4);
        if (openQuote < 0) return null;
        int closeQuote = json.indexOf('"', openQuote + 1);
        if (closeQuote < 0) return null;
        String value = json.substring(openQuote + 1, closeQuote);
        return value.isBlank() ? null : value;
    }

    private String applyPicklistConfig(Map<String, Object> args, Map<String, Object> attrs) {
        Object pid = args.get("picklistSourceId");
        if (!(pid instanceof String picklistSourceId) || picklistSourceId.isBlank()) {
            return "picklist fields require \"picklistSourceId\" (UUID of the source picklist).";
        }
        String sourceType = "GLOBAL";
        if (args.get("picklistSourceType") instanceof String st && !st.isBlank()) {
            sourceType = st.toUpperCase(Locale.ROOT);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("picklistSourceType", sourceType);
        config.put("picklistSourceId", picklistSourceId);
        attrs.put("fieldTypeConfig", config);
        return null;
    }

    private String applyVectorConfig(Map<String, Object> args, Map<String, Object> attrs) {
        Object raw = args.get("dimension");
        if (raw == null) {
            return "Vector fields require a \"dimension\" argument.";
        }
        int dim;
        if (raw instanceof Number n) {
            dim = n.intValue();
        } else {
            try {
                dim = Integer.parseInt(raw.toString());
            } catch (NumberFormatException e) {
                return "\"dimension\" must be an integer.";
            }
        }
        if (dim < 1 || dim > 16384) {
            return "\"dimension\" must be between 1 and 16384.";
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dimension", dim);
        attrs.put("fieldTypeConfig", config);
        return null;
    }

    private String applyReferenceRelationship(Map<String, Object> args,
                                              Map<String, Object> attrs,
                                              Map<String, Object> relationships) {
        String targetId = null;
        if (args.get("referenceCollectionId") instanceof String s && !s.isBlank()) {
            targetId = s;
        } else if (args.get("referenceCollection") instanceof String s && !s.isBlank()) {
            targetId = UUID_PATTERN.matcher(s).matches() ? s : lookupCollectionIdByName(s);
        }
        if (targetId == null) {
            return "reference/lookup fields require \"referenceCollectionId\" (UUID of the target collection) or a resolvable \"referenceCollection\" name.";
        }

        if (args.get("relationshipName") instanceof String rn && !rn.isBlank()) {
            attrs.put("relationshipName", rn);
        }
        if (args.get("relationshipType") instanceof String rt && !rt.isBlank()) {
            attrs.put("relationshipType", rt.toUpperCase(Locale.ROOT));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "collections");
        data.put("id", targetId);
        relationships.put("referenceCollectionId", Map.of("data", data));
        return null;
    }

    private static boolean isLookupType(String nativeType) {
        return "LOOKUP".equals(nativeType)
                || "REFERENCE".equals(nativeType)
                || "MASTER_DETAIL".equals(nativeType);
    }

    /**
     * Maps an MCP-friendly type alias to the native uppercase {@code FieldType}
     * enum value the worker accepts. Returns null for unknown aliases.
     */
    static String resolveNativeType(String alias) {
        if (alias == null) return null;
        String trimmed = alias.trim();
        if (trimmed.isEmpty()) return null;
        return switch (trimmed) {
            case "text", "string", "String", "STRING" -> "STRING";
            case "longText", "longtext", "long_text" -> "STRING";
            case "number", "integer", "Integer", "INTEGER" -> "INTEGER";
            case "decimal", "double", "Double", "DOUBLE" -> "DOUBLE";
            case "long", "Long", "LONG" -> "LONG";
            case "boolean", "bool", "Boolean", "BOOLEAN" -> "BOOLEAN";
            case "date", "Date", "DATE" -> "DATE";
            case "datetime", "dateTime", "DateTime", "DATETIME" -> "DATETIME";
            case "picklist", "Picklist", "PICKLIST" -> "PICKLIST";
            case "multiPicklist", "multipicklist", "MULTI_PICKLIST" -> "MULTI_PICKLIST";
            case "reference", "Reference", "REFERENCE",
                 "lookup", "Lookup", "LOOKUP" -> "LOOKUP";
            case "masterDetail", "master_detail", "MASTER_DETAIL" -> "MASTER_DETAIL";
            case "json", "Json", "JSON" -> "JSON";
            case "richText", "rich_text", "RichText", "RICH_TEXT" -> "RICH_TEXT";
            case "vector", "Vector", "VECTOR" -> "VECTOR";
            default -> null;
        };
    }

    record Result(Map<String, Object> body, String errorMessage) {
        static Result ok(Map<String, Object> body) {
            return new Result(body, null);
        }

        static Result error(String message) {
            return new Result(null, message);
        }

        boolean isError() {
            return errorMessage != null;
        }
    }
}
