package io.kelta.ai.service;

import io.kelta.ai.config.AiConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

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
                .bodyToMono(Map.class)
                .block();
    }

    public void createFields(String tenantId, String userId, String collectionId,
                              List<Map<String, Object>> fields) {
        log.info("Creating {} fields for collection {} via worker API", fields.size(), collectionId);

        for (Map<String, Object> field : fields) {
            Map<String, Object> fieldAttrs = new java.util.LinkedHashMap<>(field);
            fieldAttrs.put("collectionId", collectionId);

            Map<String, Object> jsonApiBody = Map.of(
                    "data", Map.of("type", "fields", "attributes", fieldAttrs)
            );

            try {
                webClient.post()
                        .uri("/api/fields")
                        .header("X-Tenant-ID", tenantId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jsonApiBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                log.debug("Created field '{}' for collection {}", field.get("name"), collectionId);
            } catch (Exception e) {
                log.error("Failed to create field '{}' for collection {}: {}",
                        field.get("name"), collectionId, e.getMessage());
            }
        }
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
}
