package io.kelta.worker.scim.controller;

import io.kelta.worker.scim.ScimConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scim/v2")
public class ScimDiscoveryController {

    @GetMapping(value = "/ServiceProviderConfig", produces = ScimConstants.CONTENT_TYPE_SCIM)
    public ResponseEntity<Map<String, Object>> getServiceProviderConfig() {
        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("schemas", List.of(ScimConstants.SCHEMA_SP_CONFIG)),
                Map.entry("documentationUri", "https://kelta.io/docs/scim"),
                Map.entry("patch", Map.of("supported", true)),
                Map.entry("bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0)),
                Map.entry("filter", Map.of("supported", true, "maxResults", ScimConstants.MAX_PAGE_SIZE)),
                Map.entry("changePassword", Map.of("supported", false)),
                Map.entry("sort", Map.of("supported", false)),
                Map.entry("etag", Map.of("supported", false)),
                Map.entry("authenticationSchemes", List.of(
                        Map.of("type", "oauthbearertoken",
                                "name", "OAuth Bearer Token",
                                "description", "Authentication scheme using the OAuth Bearer Token Standard",
                                "specUri", "https://www.rfc-editor.org/info/rfc6750",
                                "primary", true)
                ))
        ));
    }

    @GetMapping(value = "/ResourceTypes", produces = ScimConstants.CONTENT_TYPE_SCIM)
    public ResponseEntity<Map<String, Object>> getResourceTypes() {
        return ResponseEntity.ok(Map.of(
                "schemas", List.of(ScimConstants.SCHEMA_LIST_RESPONSE),
                "totalResults", 2,
                "Resources", List.of(
                        Map.of("schemas", List.of(ScimConstants.SCHEMA_RESOURCE_TYPE),
                                "id", "User",
                                "name", "User",
                                "endpoint", "/Users",
                                "schema", ScimConstants.SCHEMA_USER,
                                "meta", Map.of("resourceType", "ResourceType",
                                        "location", "/scim/v2/ResourceTypes/User")),
                        Map.of("schemas", List.of(ScimConstants.SCHEMA_RESOURCE_TYPE),
                                "id", "Group",
                                "name", "Group",
                                "endpoint", "/Groups",
                                "schema", ScimConstants.SCHEMA_GROUP,
                                "meta", Map.of("resourceType", "ResourceType",
                                        "location", "/scim/v2/ResourceTypes/Group"))
                )
        ));
    }

    @GetMapping(value = "/Schemas", produces = ScimConstants.CONTENT_TYPE_SCIM)
    public ResponseEntity<Map<String, Object>> getSchemas() {
        return ResponseEntity.ok(Map.of(
                "schemas", List.of(ScimConstants.SCHEMA_LIST_RESPONSE),
                "totalResults", 2,
                "Resources", List.of(
                        buildUserSchema(),
                        buildGroupSchema()
                )
        ));
    }

    private Map<String, Object> buildUserSchema() {
        return Map.ofEntries(
                Map.entry("schemas", List.of(ScimConstants.SCHEMA_SCHEMA)),
                Map.entry("id", ScimConstants.SCHEMA_USER),
                Map.entry("name", "User"),
                Map.entry("description", "User Account"),
                Map.entry("attributes", List.of(
                        schemaAttr("userName", "string", "User's primary email address", true, "readWrite", "always"),
                        schemaAttr("name", "complex", "User's name", false, "readWrite", "default"),
                        schemaAttr("displayName", "string", "User's display name", false, "readWrite", "default"),
                        schemaAttr("locale", "string", "User's locale", false, "readWrite", "default"),
                        schemaAttr("timezone", "string", "User's timezone", false, "readWrite", "default"),
                        schemaAttr("active", "boolean", "Whether the user is active", true, "readWrite", "default"),
                        schemaAttr("emails", "complex", "User's email addresses", false, "readWrite", "default"),
                        schemaAttr("externalId", "string", "External identifier", false, "readWrite", "default")
                )),
                Map.entry("meta", Map.of("resourceType", "Schema", "location", "/scim/v2/Schemas/" + ScimConstants.SCHEMA_USER))
        );
    }

    private Map<String, Object> buildGroupSchema() {
        return Map.ofEntries(
                Map.entry("schemas", List.of(ScimConstants.SCHEMA_SCHEMA)),
                Map.entry("id", ScimConstants.SCHEMA_GROUP),
                Map.entry("name", "Group"),
                Map.entry("description", "Group"),
                Map.entry("attributes", List.of(
                        schemaAttr("displayName", "string", "Group display name", true, "readWrite", "always"),
                        schemaAttr("members", "complex", "Group members", false, "readWrite", "default"),
                        schemaAttr("externalId", "string", "External identifier", false, "readWrite", "default")
                )),
                Map.entry("meta", Map.of("resourceType", "Schema", "location", "/scim/v2/Schemas/" + ScimConstants.SCHEMA_GROUP))
        );
    }

    private Map<String, Object> schemaAttr(String name, String type, String description,
                                            boolean required, String mutability, String returned) {
        return Map.of(
                "name", name,
                "type", type,
                "description", description,
                "required", required,
                "mutability", mutability,
                "returned", returned,
                "multiValued", "complex".equals(type) && ("emails".equals(name) || "members".equals(name))
        );
    }
}
