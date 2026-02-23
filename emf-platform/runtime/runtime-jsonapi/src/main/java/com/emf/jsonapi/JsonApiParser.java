package com.emf.jsonapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for parsing JSON:API formatted strings into JsonApiDocument objects.
 * Handles both single resource and collection responses, as well as error responses.
 */
@Service
public class JsonApiParser {
    private static final Logger log = LoggerFactory.getLogger(JsonApiParser.class);
    private final ObjectMapper objectMapper;

    public JsonApiParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a JSON:API response body into a JsonApiDocument.
     *
     * @param responseBody the JSON:API formatted string
     * @return JsonApiDocument containing the parsed data
     * @throws JsonApiParseException if the response body cannot be parsed
     */
    public JsonApiDocument parse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            throw new JsonApiParseException("Response body is null or empty");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonApiDocument document = new JsonApiDocument();

            // Parse errors if present
            if (rootNode.has("errors")) {
                List<JsonApiError> errors = parseErrors(rootNode.get("errors"));
                document.setErrors(errors);
            }

            // Parse data if present (can be single resource or collection)
            if (rootNode.has("data")) {
                JsonNode dataNode = rootNode.get("data");
                boolean isSingleResource = !dataNode.isNull() && !dataNode.isArray();
                List<ResourceObject> data = parseData(dataNode);
                document.setData(data);
                document.setSingleResource(isSingleResource);
            }

            // Parse included resources if present
            if (rootNode.has("included")) {
                List<ResourceObject> included = parseIncluded(rootNode.get("included"));
                document.setIncluded(included);
            }

            // Parse meta if present
            if (rootNode.has("meta")) {
                Map<String, Object> meta = parseMeta(rootNode.get("meta"));
                document.setMeta(meta);
            }

            return document;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON:API response: {}", e.getMessage());
            throw new JsonApiParseException("Failed to parse JSON:API response", e);
        }
    }

    /**
     * Parse the data field, which can be either a single resource object or an array of resources.
     */
    private List<ResourceObject> parseData(JsonNode dataNode) throws JsonProcessingException {
        List<ResourceObject> resources = new ArrayList<>();

        if (dataNode.isNull()) {
            // data can be null in JSON:API
            return resources;
        }

        if (dataNode.isArray()) {
            // Collection response
            for (JsonNode resourceNode : dataNode) {
                ResourceObject resource = parseResourceObject(resourceNode);
                resources.add(resource);
            }
        } else {
            // Single resource response
            ResourceObject resource = parseResourceObject(dataNode);
            resources.add(resource);
        }

        return resources;
    }

    /**
     * Parse the included field, which is always an array of resource objects.
     */
    private List<ResourceObject> parseIncluded(JsonNode includedNode) throws JsonProcessingException {
        List<ResourceObject> resources = new ArrayList<>();

        if (includedNode.isArray()) {
            for (JsonNode resourceNode : includedNode) {
                ResourceObject resource = parseResourceObject(resourceNode);
                resources.add(resource);
            }
        }

        return resources;
    }

    /**
     * Parse a single resource object with type, id, attributes, and relationships.
     */
    private ResourceObject parseResourceObject(JsonNode resourceNode) throws JsonProcessingException {
        ResourceObject resource = new ResourceObject();

        // Parse type and id (required fields)
        if (resourceNode.has("type")) {
            resource.setType(resourceNode.get("type").asText());
        }
        if (resourceNode.has("id")) {
            resource.setId(resourceNode.get("id").asText());
        }

        // Parse attributes
        if (resourceNode.has("attributes")) {
            Map<String, Object> attributes = parseAttributes(resourceNode.get("attributes"));
            resource.setAttributes(attributes);
        }

        // Parse relationships
        if (resourceNode.has("relationships")) {
            Map<String, Relationship> relationships = parseRelationships(resourceNode.get("relationships"));
            resource.setRelationships(relationships);
        }

        return resource;
    }

    /**
     * Parse attributes as a map of field names to values.
     */
    private Map<String, Object> parseAttributes(JsonNode attributesNode) throws JsonProcessingException {
        return objectMapper.convertValue(attributesNode, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Parse relationships as a map of relationship names to Relationship objects.
     */
    private Map<String, Relationship> parseRelationships(JsonNode relationshipsNode) throws JsonProcessingException {
        Map<String, Relationship> relationships = new HashMap<>();

        relationshipsNode.fields().forEachRemaining(entry -> {
            String relationshipName = entry.getKey();
            JsonNode relationshipNode = entry.getValue();

            try {
                Relationship relationship = parseRelationship(relationshipNode);
                relationships.put(relationshipName, relationship);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse relationship '{}': {}", relationshipName, e.getMessage());
            }
        });

        return relationships;
    }

    /**
     * Parse a single relationship, which can contain data (single or array) and links.
     */
    private Relationship parseRelationship(JsonNode relationshipNode) throws JsonProcessingException {
        Relationship relationship = new Relationship();

        // Parse data (can be single ResourceIdentifier, array, or null)
        if (relationshipNode.has("data")) {
            JsonNode dataNode = relationshipNode.get("data");

            if (dataNode.isNull()) {
                relationship.setData(null);
            } else if (dataNode.isArray()) {
                // Array of resource identifiers
                List<ResourceIdentifier> identifiers = new ArrayList<>();
                for (JsonNode idNode : dataNode) {
                    ResourceIdentifier identifier = parseResourceIdentifier(idNode);
                    identifiers.add(identifier);
                }
                relationship.setData(identifiers);
            } else {
                // Single resource identifier
                ResourceIdentifier identifier = parseResourceIdentifier(dataNode);
                relationship.setData(identifier);
            }
        }

        // Parse links
        if (relationshipNode.has("links")) {
            Map<String, String> links = objectMapper.convertValue(
                relationshipNode.get("links"),
                new TypeReference<Map<String, String>>() {}
            );
            relationship.setLinks(links);
        }

        return relationship;
    }

    /**
     * Parse a resource identifier with type and id.
     */
    private ResourceIdentifier parseResourceIdentifier(JsonNode idNode) {
        ResourceIdentifier identifier = new ResourceIdentifier();

        if (idNode.has("type")) {
            identifier.setType(idNode.get("type").asText());
        }
        if (idNode.has("id")) {
            identifier.setId(idNode.get("id").asText());
        }

        return identifier;
    }

    /**
     * Parse errors array.
     */
    private List<JsonApiError> parseErrors(JsonNode errorsNode) throws JsonProcessingException {
        List<JsonApiError> errors = new ArrayList<>();

        if (errorsNode.isArray()) {
            for (JsonNode errorNode : errorsNode) {
                JsonApiError error = parseError(errorNode);
                errors.add(error);
            }
        }

        return errors;
    }

    /**
     * Parse a single error object.
     */
    private JsonApiError parseError(JsonNode errorNode) throws JsonProcessingException {
        JsonApiError error = new JsonApiError();

        if (errorNode.has("id")) {
            error.setId(errorNode.get("id").asText());
        }
        if (errorNode.has("status")) {
            error.setStatus(errorNode.get("status").asText());
        }
        if (errorNode.has("code")) {
            error.setCode(errorNode.get("code").asText());
        }
        if (errorNode.has("title")) {
            error.setTitle(errorNode.get("title").asText());
        }
        if (errorNode.has("detail")) {
            error.setDetail(errorNode.get("detail").asText());
        }
        if (errorNode.has("source")) {
            Map<String, Object> source = objectMapper.convertValue(
                errorNode.get("source"),
                new TypeReference<Map<String, Object>>() {}
            );
            error.setSource(source);
        }
        if (errorNode.has("meta")) {
            Map<String, Object> meta = objectMapper.convertValue(
                errorNode.get("meta"),
                new TypeReference<Map<String, Object>>() {}
            );
            error.setMeta(meta);
        }

        return error;
    }

    /**
     * Parse meta as a map of key-value pairs.
     */
    private Map<String, Object> parseMeta(JsonNode metaNode) throws JsonProcessingException {
        return objectMapper.convertValue(metaNode, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Static method to parse a JSON string into a ResourceObject.
     * Used by IncludeResolver to deserialize cached resources from Redis.
     *
     * @param json JSON string representation of a resource object
     * @return ResourceObject parsed from the JSON
     * @throws JsonApiParseException if parsing fails
     */
    public static ResourceObject parseResourceObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new JsonApiParseException("JSON string is null or empty");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode resourceNode = mapper.readTree(json);

            ResourceObject resource = new ResourceObject();

            // Parse type and id (required fields)
            if (resourceNode.has("type")) {
                resource.setType(resourceNode.get("type").asText());
            }
            if (resourceNode.has("id")) {
                resource.setId(resourceNode.get("id").asText());
            }

            // Parse attributes
            if (resourceNode.has("attributes")) {
                Map<String, Object> attributes = mapper.convertValue(
                    resourceNode.get("attributes"),
                    new TypeReference<Map<String, Object>>() {}
                );
                resource.setAttributes(attributes);
            }

            // Parse relationships
            if (resourceNode.has("relationships")) {
                Map<String, Relationship> relationships = parseRelationshipsStatic(resourceNode.get("relationships"), mapper);
                resource.setRelationships(relationships);
            }

            return resource;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ResourceObject from JSON: {}", e.getMessage());
            throw new JsonApiParseException("Failed to parse ResourceObject from JSON", e);
        }
    }

    /**
     * Static helper to parse relationships for the static parseResourceObject method.
     */
    private static Map<String, Relationship> parseRelationshipsStatic(JsonNode relationshipsNode, ObjectMapper mapper) throws JsonProcessingException {
        Map<String, Relationship> relationships = new HashMap<>();

        relationshipsNode.fields().forEachRemaining(entry -> {
            String relationshipName = entry.getKey();
            JsonNode relationshipNode = entry.getValue();

            try {
                Relationship relationship = parseRelationshipStatic(relationshipNode, mapper);
                relationships.put(relationshipName, relationship);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse relationship '{}': {}", relationshipName, e.getMessage());
            }
        });

        return relationships;
    }

    /**
     * Static helper to parse a single relationship for the static parseResourceObject method.
     */
    private static Relationship parseRelationshipStatic(JsonNode relationshipNode, ObjectMapper mapper) throws JsonProcessingException {
        Relationship relationship = new Relationship();

        // Parse data (can be single ResourceIdentifier, array, or null)
        if (relationshipNode.has("data")) {
            JsonNode dataNode = relationshipNode.get("data");

            if (dataNode.isNull()) {
                relationship.setData(null);
            } else if (dataNode.isArray()) {
                // Array of resource identifiers
                List<ResourceIdentifier> identifiers = new ArrayList<>();
                for (JsonNode idNode : dataNode) {
                    ResourceIdentifier identifier = parseResourceIdentifierStatic(idNode);
                    identifiers.add(identifier);
                }
                relationship.setData(identifiers);
            } else {
                // Single resource identifier
                ResourceIdentifier identifier = parseResourceIdentifierStatic(dataNode);
                relationship.setData(identifier);
            }
        }

        // Parse links
        if (relationshipNode.has("links")) {
            Map<String, String> links = mapper.convertValue(
                relationshipNode.get("links"),
                new TypeReference<Map<String, String>>() {}
            );
            relationship.setLinks(links);
        }

        return relationship;
    }

    /**
     * Static helper to parse a resource identifier for the static parseResourceObject method.
     */
    private static ResourceIdentifier parseResourceIdentifierStatic(JsonNode idNode) {
        ResourceIdentifier identifier = new ResourceIdentifier();

        if (idNode.has("type")) {
            identifier.setType(idNode.get("type").asText());
        }
        if (idNode.has("id")) {
            identifier.setId(idNode.get("id").asText());
        }

        return identifier;
    }

    /**
     * Exception thrown when JSON:API parsing fails.
     */
    public static class JsonApiParseException extends RuntimeException {
        public JsonApiParseException(String message) {
            super(message);
        }

        public JsonApiParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Serializes a ResourceObject to JSON string.
     * Used by IncludeResolver to cache resources in Redis.
     *
     * @param resource The ResourceObject to serialize
     * @return JSON string representation of the resource
     * @throws JsonApiParseException if serialization fails
     */
    public String serializeResourceObject(ResourceObject resource) {
        if (resource == null) {
            throw new JsonApiParseException("ResourceObject is null");
        }

        try {
            return objectMapper.writeValueAsString(resource);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ResourceObject to JSON: {}", e.getMessage());
            throw new JsonApiParseException("Failed to serialize ResourceObject to JSON", e);
        }
    }
}
