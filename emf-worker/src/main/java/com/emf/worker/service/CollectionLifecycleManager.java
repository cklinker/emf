package com.emf.worker.service;

import com.emf.runtime.model.ApiConfig;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.StorageMode;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.worker.config.WorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of collections assigned to this worker.
 *
 * <p>Handles:
 * <ul>
 *   <li>Fetching collection definitions from the control plane</li>
 *   <li>Building runtime CollectionDefinition objects</li>
 *   <li>Registering collections in the local registry</li>
 *   <li>Initializing storage (creating database tables)</li>
 *   <li>Tearing down collections when unassigned</li>
 * </ul>
 */
@Service
public class CollectionLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(CollectionLifecycleManager.class);

    private final CollectionRegistry collectionRegistry;
    private final StorageAdapter storageAdapter;
    private final RestTemplate restTemplate;
    private final WorkerProperties workerProperties;
    private final ObjectMapper objectMapper;

    /**
     * Tracks which collection IDs are actively managed by this worker.
     * Maps collection ID to collection name for quick lookup.
     */
    private final ConcurrentHashMap<String, String> activeCollections = new ConcurrentHashMap<>();

    public CollectionLifecycleManager(CollectionRegistry collectionRegistry,
                                       StorageAdapter storageAdapter,
                                       RestTemplate restTemplate,
                                       WorkerProperties workerProperties,
                                       ObjectMapper objectMapper) {
        this.collectionRegistry = collectionRegistry;
        this.storageAdapter = storageAdapter;
        this.restTemplate = restTemplate;
        this.workerProperties = workerProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Initializes a collection on this worker by fetching its definition from
     * the control plane, registering it locally, and initializing storage.
     *
     * @param collectionId the collection ID to initialize
     */
    @SuppressWarnings("unchecked")
    public void initializeCollection(String collectionId) {
        log.info("Initializing collection: {}", collectionId);

        try {
            // Fetch collection definition from control plane
            String url = workerProperties.getControlPlaneUrl() + "/control/collections/" + collectionId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to fetch collection definition for {}: status={}",
                    collectionId, response.getStatusCode());
                return;
            }

            Map<String, Object> collectionData = response.getBody();
            String collectionName = (String) collectionData.get("name");

            if (collectionName == null || collectionName.isBlank()) {
                log.error("Collection {} has no name in response", collectionId);
                return;
            }

            // Build CollectionDefinition from the response
            CollectionDefinition definition = buildCollectionDefinition(collectionName, collectionData);

            // Register in local registry (makes it available to DynamicCollectionRouter)
            collectionRegistry.register(definition);

            // Initialize storage (creates database table if needed)
            storageAdapter.initializeCollection(definition);

            // Track as active
            activeCollections.put(collectionId, collectionName);

            log.info("Successfully initialized collection '{}' (id={})", collectionName, collectionId);

            // Notify control plane that this collection is ready on this worker
            notifyCollectionReady(collectionId);

        } catch (Exception e) {
            log.error("Failed to initialize collection {}: {}", collectionId, e.getMessage(), e);
        }
    }

    /**
     * Tears down a collection on this worker by removing it from the local registry.
     * Data is not dropped -- it persists for potential reassignment to another worker.
     *
     * @param collectionId the collection ID to tear down
     */
    public void teardownCollection(String collectionId) {
        String collectionName = activeCollections.remove(collectionId);
        if (collectionName != null) {
            collectionRegistry.unregister(collectionName);
            log.info("Torn down collection '{}' (id={})", collectionName, collectionId);
        } else {
            log.warn("Attempted to tear down unknown collection: {}", collectionId);
        }
    }

    /**
     * Returns the set of collection IDs actively managed by this worker.
     *
     * @return set of active collection IDs
     */
    public Set<String> getActiveCollections() {
        return new HashSet<>(activeCollections.keySet());
    }

    /**
     * Returns the number of active collections.
     *
     * @return active collection count
     */
    public int getActiveCollectionCount() {
        return activeCollections.size();
    }

    /**
     * Builds a CollectionDefinition from control plane response data.
     */
    @SuppressWarnings("unchecked")
    private CollectionDefinition buildCollectionDefinition(String name, Map<String, Object> data) {
        CollectionDefinitionBuilder builder = new CollectionDefinitionBuilder()
                .name(name)
                .displayName(getStringOrDefault(data, "displayName", name))
                .description(getStringOrDefault(data, "description", null))
                .apiConfig(ApiConfig.allEnabled("/api/collections/" + name));

        // Parse fields from the response
        List<FieldDefinition> fields = parseFields(data);
        if (fields.isEmpty()) {
            // Every collection needs at least one field; add a default name field
            fields.add(FieldDefinition.string("name"));
        }
        builder.fields(fields);

        // Parse storage config
        String storageMode = getStringOrDefault(data, "storageMode", "PHYSICAL_TABLES");
        String tableName = getStringOrDefault(data, "tableName", "tbl_" + name);
        builder.storageConfig(new com.emf.runtime.model.StorageConfig(
                StorageMode.valueOf(storageMode), tableName, null));

        return builder.build();
    }

    /**
     * Parses field definitions from the control plane collection response.
     */
    @SuppressWarnings("unchecked")
    private List<FieldDefinition> parseFields(Map<String, Object> data) {
        List<FieldDefinition> fields = new ArrayList<>();

        Object fieldsObj = data.get("fields");
        if (fieldsObj instanceof List<?> fieldsList) {
            for (Object fieldObj : fieldsList) {
                if (fieldObj instanceof Map<?, ?> fieldMap) {
                    Map<String, Object> field = (Map<String, Object>) fieldMap;
                    String fieldName = (String) field.get("name");
                    String fieldTypeStr = (String) field.get("type");

                    if (fieldName == null || fieldTypeStr == null) {
                        continue;
                    }

                    try {
                        FieldType fieldType = FieldType.valueOf(fieldTypeStr.toUpperCase());
                        boolean required = Boolean.TRUE.equals(field.get("required"));
                        boolean unique = Boolean.TRUE.equals(field.get("unique"));

                        FieldDefinition fieldDef = new FieldDefinition(
                                fieldName, fieldType, !required, false, unique,
                                null, null, null, null);
                        fields.add(fieldDef);
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown field type '{}' for field '{}', defaulting to STRING",
                                fieldTypeStr, fieldName);
                        fields.add(FieldDefinition.string(fieldName));
                    }
                }
            }
        }

        return fields;
    }

    /**
     * Notifies the control plane that a collection is ready on this worker.
     */
    private void notifyCollectionReady(String collectionId) {
        try {
            String url = workerProperties.getControlPlaneUrl()
                    + "/control/assignments/" + collectionId + "/" + workerProperties.getId() + "/ready";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>("{}", headers);

            restTemplate.postForEntity(url, request, Void.class);
            log.info("Notified control plane that collection {} is ready", collectionId);
        } catch (Exception e) {
            log.warn("Failed to notify control plane of collection readiness for {}: {}",
                    collectionId, e.getMessage());
        }
    }

    private String getStringOrDefault(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return defaultValue;
    }
}
