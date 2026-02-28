package com.emf.runtime.module;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

            return new ModuleManifest(
                id, name, version, description, author,
                moduleClass, minPlatformVersion, permissions, handlers
            );
        } catch (ModuleManifestException e) {
            throw e;
        } catch (JsonProcessingException e) {
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

    private String optionalJsonString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.toString();
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
