package io.kelta.mcp.tool.admin;

import io.kelta.mcp.client.GatewayHttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway-backed name/id resolution shared by the admin tools.
 *
 * <p>All parsing is done with Jackson on the JSON:API envelope. The old
 * substring scan ("first {@code "id"} after {@code "data"}") returned the
 * {@code relationships.createdBy.data.id} — the acting <em>user's</em> UUID —
 * because the worker serializes {@code relationships} before the record's own
 * {@code id}. Every helper here reads {@code data[0].id} / {@code data.id}
 * explicitly so response key order can never matter again.
 */
final class AdminLookups {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayHttpClient gateway;

    AdminLookups(GatewayHttpClient gateway) {
        this.gateway = gateway;
    }

    /**
     * The id of the first resource in a JSON:API body: {@code data.id} for a
     * single-resource document, {@code data[0].id} for a list document.
     * Returns null when the body has no resource.
     */
    static String firstResourceId(String json) {
        JsonNode data = dataNode(json);
        if (data == null) return null;
        JsonNode id = data.path("id");
        return id.isMissingNode() || id.isNull() || id.asString().isBlank() ? null : id.asString();
    }

    /** A string attribute of the first resource in a JSON:API body, or null. */
    static String firstResourceAttribute(String json, String attribute) {
        JsonNode data = dataNode(json);
        if (data == null) return null;
        JsonNode value = data.path("attributes").path(attribute);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static JsonNode dataNode(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                return data.isEmpty() ? null : data.get(0);
            }
            return data.isObject() ? data : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Resolves a collection name to its UUID, or null when not found. */
    String collectionIdByName(String collectionName) {
        String path = "/api/collections?filter[name][eq]="
                + URLEncoder.encode(collectionName, StandardCharsets.UTF_8);
        GatewayHttpClient.Response res = gateway.get(path);
        if (!res.isSuccess() || res.body() == null) {
            return null;
        }
        return firstResourceId(res.body());
    }

    /** Resolves a collection UUID to its name, or null when not found. */
    String collectionNameById(String collectionId) {
        String path = "/api/collections/" + URLEncoder.encode(collectionId, StandardCharsets.UTF_8);
        GatewayHttpClient.Response res = gateway.get(path);
        if (!res.isSuccess() || res.body() == null) {
            return null;
        }
        return firstResourceAttribute(res.body(), "name");
    }

    /**
     * All fields of a collection as {@code fieldName -> fieldId}. Used to
     * resolve layout field references and {@code displayFieldName}.
     */
    Map<String, String> fieldIdsByName(String collectionId) {
        String path = "/api/fields?filter[collectionId][EQ]="
                + URLEncoder.encode(collectionId, StandardCharsets.UTF_8)
                + "&page[size]=200";
        GatewayHttpClient.Response res = gateway.get(path);
        Map<String, String> out = new LinkedHashMap<>();
        if (!res.isSuccess() || res.body() == null) {
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(res.body());
            for (JsonNode item : root.path("data")) {
                String name = item.path("attributes").path("name").asString();
                String id = item.path("id").asString();
                if (!name.isBlank() && !id.isBlank()) {
                    out.put(name, id);
                }
            }
        } catch (RuntimeException e) {
            return out;
        }
        return out;
    }
}
