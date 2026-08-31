package io.kelta.runtime.module;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses module manifest JSON into {@link ModuleManifest} records.
 *
 * @since 1.0.0
 */
public class ModuleManifestParser {

    private final ObjectMapper objectMapper;

    public ModuleManifestParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null")
            .rebuild()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /**
     * Parses manifest JSON into a ModuleManifest.
     *
     * @param json the manifest JSON string
     * @return the parsed manifest
     * @throws ModuleManifestException if parsing fails or required fields are missing
     */
    public ModuleManifest parse(String json) {
        Objects.requireNonNull(json, "manifest JSON cannot be null");

        try {
            JsonNode root = objectMapper.readTree(json);

            String id = requireString(root, "id");
            String name = requireString(root, "name");
            String version = requireString(root, "version");
            String moduleClass = requireString(root, "moduleClass");

            String description = optionalString(root, "description");
            String author = optionalString(root, "author");
            String minPlatformVersion = optionalString(root, "minPlatformVersion");

            List<String> permissions = parseStringList(root, "permissions");
            List<ModuleManifest.ActionHandlerManifest> handlers = parseActionHandlers(root);
            List<ModuleManifest.CollectionManifest> collections = parseCollections(root);
            String webhookHandlerKey = optionalString(root, "webhookHandlerKey");
            String uiBundlePath = optionalString(root, "uiBundlePath");
            List<String> services = parseStringList(root, "services");
            List<ModuleManifest.RouteManifest> routes = parseRoutes(root);

            return new ModuleManifest(
                id, name, version, description, author,
                moduleClass, minPlatformVersion, permissions, handlers, collections,
                webhookHandlerKey, uiBundlePath, services, routes
            );
        } catch (ModuleManifestException e) {
            throw e;
        } catch (JacksonException e) {
            throw new ModuleManifestException("Invalid manifest JSON: " + e.getMessage(), e);
        }
    }

    private List<ModuleManifest.ActionHandlerManifest> parseActionHandlers(JsonNode root) {
        JsonNode handlersNode = root.get("actionHandlers");
        if (handlersNode == null || !handlersNode.isArray()) {
            return List.of();
        }

        List<ModuleManifest.ActionHandlerManifest> handlers = new ArrayList<>();
        for (JsonNode node : handlersNode) {
            String key = requireString(node, "key");
            String name = requireString(node, "name");
            String category = optionalString(node, "category");
            String description = optionalString(node, "description");
            String icon = optionalString(node, "icon");
            String configSchema = optionalJsonString(node, "configSchema");
            String inputSchema = optionalJsonString(node, "inputSchema");
            String outputSchema = optionalJsonString(node, "outputSchema");

            handlers.add(new ModuleManifest.ActionHandlerManifest(
                key, name, category, description, icon,
                configSchema, inputSchema, outputSchema
            ));
        }
        return List.copyOf(handlers);
    }

    private List<ModuleManifest.CollectionManifest> parseCollections(JsonNode root) {
        JsonNode collectionsNode = root.get("collections");
        if (collectionsNode == null || !collectionsNode.isArray()) {
            return List.of();
        }

        List<ModuleManifest.CollectionManifest> collections = new ArrayList<>();
        for (JsonNode node : collectionsNode) {
            String name = requireString(node, "name");
            String displayName = optionalString(node, "displayName");

            List<ModuleManifest.CollectionManifest.FieldManifest> fields = new ArrayList<>();
            JsonNode fieldsNode = node.get("fields");
            if (fieldsNode != null && fieldsNode.isArray()) {
                for (JsonNode fieldNode : fieldsNode) {
                    fields.add(new ModuleManifest.CollectionManifest.FieldManifest(
                        requireString(fieldNode, "name"),
                        optionalString(fieldNode, "displayName"),
                        requireString(fieldNode, "type"),
                        optionalBoolean(fieldNode, "required")
                    ));
                }
            }

            collections.add(new ModuleManifest.CollectionManifest(
                name, displayName, List.copyOf(fields)));
        }
        return List.copyOf(collections);
    }

    private String requireString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new ModuleManifestException("Required field '" + field + "' is missing or blank");
        }
        return value.asText();
    }

    private String optionalString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        return value.asText();
    }

    private boolean optionalBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private String optionalJsonString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.toString();
    }

    /**
     * Reads the {@code routes} array.
     *
     * <p>A malformed route is rejected rather than skipped. Silently dropping one would leave the
     * module installed and apparently healthy while an endpoint its UI depends on simply 404s,
     * which is a far harder failure to diagnose than a refused install.
     */
    private List<ModuleManifest.RouteManifest> parseRoutes(JsonNode root) {
        JsonNode value = root.get("routes");
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<ModuleManifest.RouteManifest> routes = new ArrayList<>();
        for (JsonNode item : value) {
            String path = item.path("path").asText(null);
            String handlerKey = item.path("handlerKey").asText(null);
            if (path == null || path.isBlank() || !path.startsWith("/")) {
                throw new ModuleManifestException(
                    "Module route 'path' is required and must start with '/': " + path);
            }
            if (path.contains("..")) {
                throw new ModuleManifestException(
                    "Module route path must not contain '..': " + path);
            }
            if (handlerKey == null || handlerKey.isBlank()) {
                throw new ModuleManifestException(
                    "Module route '" + path + "' declares no handlerKey");
            }
            List<String> methods = new ArrayList<>();
            for (JsonNode m : item.path("methods")) {
                if (m.isTextual()) {
                    methods.add(m.asText().toUpperCase(java.util.Locale.ROOT));
                }
            }
            if (methods.isEmpty()) {
                methods.add("GET");
            }
            routes.add(new ModuleManifest.RouteManifest(path, List.copyOf(methods), handlerKey));
        }
        return List.copyOf(routes);
    }

    private List<String> parseStringList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Exception thrown when a module manifest cannot be parsed.
     */
    public static class ModuleManifestException extends RuntimeException {
        public ModuleManifestException(String message) {
            super(message);
        }

        public ModuleManifestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
