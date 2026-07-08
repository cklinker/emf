package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for calling the kelta-worker APIs.
 * Used when applying AI proposals (creating collections, layouts).
 */
@Service
public class WorkerApiClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerApiClient.class);

    private final WebClient webClient;

    public WorkerApiClient(WebClient.Builder webClientBuilder, AiConfigProperties config) {
        this.webClient = webClientBuilder
                .baseUrl(config.workerServiceUrl())
                .build();
    }

    public Map<String, Object> createCollection(String tenantId, String userId, Map<String, Object> collectionData) {
        log.info("Creating collection via worker API for tenant {}", tenantId);

        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of(
                        "type", "collections",
                        "attributes", collectionData
                )
        );

        return webClient.post()
                .uri("/api/collections")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException(parseWorkerError(body))))
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Parse a JSON:API error response into a human-readable message.
     */
    @SuppressWarnings("unchecked")
    private String parseWorkerError(String body) {
        try {
            Map<String, Object> parsed = new tools.jackson.databind.ObjectMapper()
                    .readValue(body, Map.class);
            List<Map<String, Object>> errors = (List<Map<String, Object>>) parsed.get("errors");
            if (errors != null && !errors.isEmpty()) {
                StringBuilder msg = new StringBuilder();
                for (Map<String, Object> error : errors) {
                    String detail = (String) error.getOrDefault("detail", error.get("title"));
                    Map<String, Object> source = (Map<String, Object>) error.get("source");
                    String field = source != null ? (String) source.get("pointer") : null;
                    if (field != null) {
                        // Extract field name from pointer like "/data/attributes/name"
                        String fieldName = field.contains("/") ? field.substring(field.lastIndexOf('/') + 1) : field;
                        msg.append(fieldName).append(": ").append(detail);
                    } else {
                        msg.append(detail);
                    }
                    msg.append("; ");
                }
                return msg.toString().replaceAll("; $", "");
            }
        } catch (Exception e) {
            // Fall through to raw body
        }
        return body;
    }

    public List<String> createFields(String tenantId, String userId, String collectionId,
                              List<Map<String, Object>> fields) {
        log.info("Creating {} fields for collection {} via worker API", fields.size(), collectionId);
        List<String> errors = new java.util.ArrayList<>();

        for (Map<String, Object> field : fields) {
            Map<String, Object> fieldAttrs = new java.util.LinkedHashMap<>(field);
            fieldAttrs.put("collectionId", collectionId);

            // Flatten referenceConfig into top-level properties for the worker API
            @SuppressWarnings("unchecked")
            Map<String, Object> refConfig = (Map<String, Object>) fieldAttrs.remove("referenceConfig");
            if (refConfig != null) {
                if (refConfig.containsKey("targetCollection")) {
                    fieldAttrs.put("referenceTarget", refConfig.get("targetCollection"));
                }
                if (refConfig.containsKey("relationshipName")) {
                    fieldAttrs.put("relationshipName", refConfig.get("relationshipName"));
                }
                String type = String.valueOf(fieldAttrs.getOrDefault("type", ""));
                fieldAttrs.put("relationshipType", type.toUpperCase());
                fieldAttrs.put("cascadeDelete",
                        refConfig.getOrDefault("cascadeDelete", false));
            }

            // Ensure displayName is set (use field name with spaces if missing)
            if (!fieldAttrs.containsKey("displayName") || fieldAttrs.get("displayName") == null) {
                String name = String.valueOf(fieldAttrs.getOrDefault("name", ""));
                fieldAttrs.put("displayName", name.replace("_", " ")
                        .substring(0, 1).toUpperCase() + name.replace("_", " ").substring(1));
            }

            // Map nullable to required (worker uses required, AI uses nullable)
            if (fieldAttrs.containsKey("nullable")) {
                boolean nullable = Boolean.TRUE.equals(fieldAttrs.remove("nullable"));
                fieldAttrs.putIfAbsent("required", !nullable);
            }

            // Remove defaultValue — the worker expects JSON type, but Claude sends
            // plain strings like "false" or "0" which fail validation.
            // Better to not set defaults than to fail field creation.
            fieldAttrs.remove("defaultValue");

            // Remove unique if false (worker uses uniqueConstraint, not unique)
            Object uniqueVal = fieldAttrs.remove("unique");
            if (Boolean.TRUE.equals(uniqueVal)) {
                fieldAttrs.put("uniqueConstraint", true);
            }

            // Remove any null values that could cause serialization issues
            fieldAttrs.entrySet().removeIf(e -> e.getValue() == null);

            Map<String, Object> jsonApiBody = Map.of(
                    "data", Map.of("type", "fields", "attributes", fieldAttrs)
            );

            log.info("Creating field '{}' type={} attrs={}", fieldAttrs.get("name"), fieldAttrs.get("type"), fieldAttrs.keySet());

            try {
                webClient.post()
                        .uri("/api/fields")
                        .header("X-Tenant-ID", tenantId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jsonApiBody)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class)
                                        .map(body -> new RuntimeException(body)))
                        .bodyToMono(Map.class)
                        .block();
                log.debug("Created field '{}' for collection {}", field.get("name"), collectionId);
            } catch (Exception e) {
                String msg = "Failed to create field '" + field.get("name") + "': " + e.getMessage();
                log.error(msg);
                errors.add(msg);
            }
        }
        return errors;
    }

    public Map<String, Object> createPageLayout(String tenantId, String userId, Map<String, Object> layoutData) {
        log.info("Creating page layout via worker API for tenant {}", tenantId);

        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of(
                        "type", "page-layouts",
                        "attributes", layoutData
                )
        );

        return webClient.post()
                .uri("/api/page-layouts")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listCollections(String tenantId) {
        Map<String, Object> response = webClient.get()
                .uri("/api/collections?page[size]=1000")
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("data")) {
            return (List<Map<String, Object>>) response.get("data");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listFields(String tenantId, String collectionId) {
        Map<String, Object> response = webClient.get()
                .uri("/api/collections/{collectionId}/fields?page[size]=1000", collectionId)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("data")) {
            return (List<Map<String, Object>>) response.get("data");
        }
        return List.of();
    }

    // =========================================================================
    // Global Picklists
    // =========================================================================

    public Map<String, Object> createGlobalPicklist(String tenantId, String userId, Map<String, Object> picklistData) {
        log.info("Creating global picklist '{}' via worker API", picklistData.get("name"));

        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "global-picklists", "attributes", picklistData));

        return webClient.post()
                .uri("/api/global-picklists")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public void createPicklistValues(String tenantId, String userId, String picklistId,
                                      List<Map<String, Object>> values) {
        log.info("Creating {} picklist values for picklist {}", values.size(), picklistId);
        for (Map<String, Object> value : values) {
            Map<String, Object> valueAttrs = new java.util.LinkedHashMap<>(value);
            valueAttrs.put("globalPicklistId", picklistId);
            valueAttrs.put("picklistSourceType", "GLOBAL");
            valueAttrs.put("picklistSourceId", picklistId);

            Map<String, Object> jsonApiBody = Map.of(
                    "data", Map.of("type", "picklist-values", "attributes", valueAttrs));
            try {
                webClient.post()
                        .uri("/api/picklist-values")
                        .header("X-Tenant-ID", tenantId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jsonApiBody)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                response -> response.bodyToMono(String.class)
                                        .map(body -> new RuntimeException("Picklist value error: " + body)))
                        .bodyToMono(Map.class)
                        .block();
                log.debug("Created picklist value '{}'", value.get("value"));
            } catch (Exception e) {
                log.warn("Failed to create picklist value '{}': {}", value.get("value"), e.getMessage());
            }
        }
    }

    // =========================================================================
    // Layout Sections & Fields
    // =========================================================================

    public Map<String, Object> createLayoutSection(String tenantId, String userId, Map<String, Object> sectionData) {
        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "layout-sections", "attributes", sectionData));

        return webClient.post()
                .uri("/api/layout-sections")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> createLayoutField(String tenantId, String userId, Map<String, Object> fieldData) {
        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "layout-fields", "attributes", fieldData));

        return webClient.post()
                .uri("/api/layout-fields")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    // =========================================================================
    // Menus & Menu Items
    // =========================================================================

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMenus(String tenantId) {
        Map<String, Object> response = webClient.get()
                .uri("/api/ui-menus?include=ui-menu-items&page[size]=100")
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("data")) {
            return (List<Map<String, Object>>) response.get("data");
        }
        return List.of();
    }

    public Map<String, Object> createMenu(String tenantId, String userId, Map<String, Object> menuData) {
        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "ui-menus", "attributes", menuData));

        return webClient.post()
                .uri("/api/ui-menus")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /**
     * Create a page-builder ui-page (app-intelligence slice 2). The caller controls
     * `published` in the attributes — the AI apply path always sends false (draft).
     */
    public Map<String, Object> createUiPage(String tenantId, String userId, Map<String, Object> pageData) {
        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "ui-pages", "attributes", pageData));

        return webClient.post()
                .uri("/api/ui-pages")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> createMenuItem(String tenantId, String userId, Map<String, Object> itemData) {
        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "ui-menu-items", "attributes", itemData));

        return webClient.post()
                .uri("/api/ui-menu-items")
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    // =========================================================================
    // Read helpers (used by AI read tools)
    // =========================================================================

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getCollectionByName(String tenantId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String needle = name.toLowerCase();
        return listCollections(tenantId).stream()
                .filter(c -> {
                    Map<String, Object> attrs = (Map<String, Object>) c.get("attributes");
                    if (attrs == null) return false;
                    Object n = attrs.get("name");
                    return n != null && needle.equals(String.valueOf(n).toLowerCase());
                })
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPicklists(String tenantId) {
        Map<String, Object> response = webClient.get()
                .uri("/api/global-picklists?page[size]=200")
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response != null && response.containsKey("data")) {
            return (List<Map<String, Object>>) response.get("data");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getPicklist(String tenantId, String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return Optional.empty();
        Map<String, Object> response = webClient.get()
                .uri(uri -> uri.path("/api/global-picklists")
                        .queryParam("include", "picklist-values")
                        .queryParam("filter[name][EQ]", idOrName)
                        .queryParam("page[size]", 1)
                        .build())
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null) return Optional.empty();
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> picklist = data.getFirst();
        Object included = response.get("included");
        if (included instanceof List<?> incList) {
            List<Map<String, Object>> values = incList.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .filter(o -> "picklist-values".equals(o.get("type")))
                    .toList();
            Map<String, Object> withValues = new LinkedHashMap<>(picklist);
            withValues.put("values", values);
            return Optional.of(withValues);
        }
        return Optional.of(picklist);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listValidationRules(String tenantId, String collectionId) {
        Map<String, Object> response = webClient.get()
                .uri(uri -> uri.path("/api/validation-rules")
                        .queryParam("filter[collectionId][EQ]", collectionId)
                        .queryParam("page[size]", 200)
                        .build())
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response != null && response.containsKey("data")) {
            return (List<Map<String, Object>>) response.get("data");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPageLayouts(String tenantId, String collectionId) {
        Map<String, Object> response = webClient.get()
                .uri(uri -> uri.path("/api/page-layouts")
                        .queryParam("filter[collectionId][EQ]", collectionId)
                        .queryParam("page[size]", 50)
                        .build())
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response != null && response.containsKey("data")) {
            return (List<Map<String, Object>>) response.get("data");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sampleRecords(String tenantId, String collectionName,
                                              int limit, List<String> fields,
                                              Map<String, Object> filter) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        Map<String, Object> response = webClient.get()
                .uri(uri -> {
                    var b = uri.path("/api/" + collectionName)
                            .queryParam("page[size]", safeLimit);
                    if (fields != null && !fields.isEmpty()) {
                        b.queryParam("fields[" + collectionName + "]", String.join(",", fields));
                    }
                    if (filter != null) {
                        for (Map.Entry<String, Object> e : filter.entrySet()) {
                            if (e.getValue() != null) {
                                b.queryParam("filter[" + e.getKey() + "][EQ]", String.valueOf(e.getValue()));
                            }
                        }
                    }
                    return b.build();
                })
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new RuntimeException(parseWorkerError(body))))
                .bodyToMono(Map.class)
                .block();
        if (response == null) return Map.of("data", List.of());
        return response;
    }

    public Map<String, Object> updateField(String tenantId, String userId, String fieldId,
                                            Map<String, Object> attrs) {
        Map<String, Object> jsonApiBody = Map.of(
                "data", Map.of("type", "fields", "id", fieldId, "attributes", attrs));

        return webClient.patch()
                .uri("/api/fields/{fieldId}", fieldId)
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonApiBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new RuntimeException(parseWorkerError(body))))
                .bodyToMono(Map.class)
                .block();
    }

    public void deleteField(String tenantId, String userId, String fieldId) {
        webClient.delete()
                .uri("/api/fields/{fieldId}", fieldId)
                .header("X-Tenant-ID", tenantId)
                .header("X-User-Id", userId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new RuntimeException(parseWorkerError(body))))
                .bodyToMono(Void.class)
                .block();
    }
}
