package com.emf.worker.service;

import com.emf.runtime.model.ApiConfig;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.CollectionDefinitionBuilder;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.StorageMode;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.ValidationRuleDefinition;
import com.emf.runtime.validation.ValidationRuleRegistry;
import com.emf.worker.config.WorkerMetricsConfig;
import com.emf.worker.config.WorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.emf.runtime.model.ReferenceConfig;
import java.util.ArrayList;
import java.util.HashMap;
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
     * Optional validation rule registry for storing custom validation rules.
     */
    private ValidationRuleRegistry validationRuleRegistry;

    /**
     * Optional metrics config for updating initializing collection count.
     * Injected lazily to avoid circular dependency (MetricsConfig depends on this bean).
     */
    private WorkerMetricsConfig metricsConfig;

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

    @Autowired(required = false)
    public void setValidationRuleRegistry(ValidationRuleRegistry validationRuleRegistry) {
        this.validationRuleRegistry = validationRuleRegistry;
    }

    @Autowired(required = false)
    public void setMetricsConfig(WorkerMetricsConfig metricsConfig) {
        this.metricsConfig = metricsConfig;
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

        if (metricsConfig != null) {
            metricsConfig.getInitializingCount().incrementAndGet();
        }

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

            // Fetch and register validation rules
            fetchAndRegisterValidationRules(collectionId, collectionName);

            // Track as active
            activeCollections.put(collectionId, collectionName);

            log.info("Successfully initialized collection '{}' (id={})", collectionName, collectionId);

            // Notify control plane that this collection is ready on this worker
            notifyCollectionReady(collectionId);

        } catch (Exception e) {
            log.error("Failed to initialize collection {}: {}", collectionId, e.getMessage(), e);
        } finally {
            if (metricsConfig != null) {
                metricsConfig.getInitializingCount().decrementAndGet();
            }
        }
    }

    /**
     * Refreshes a collection definition on this worker by fetching the latest
     * schema from the control plane and migrating the storage schema.
     *
     * <p>This is called when a collection's fields change (e.g., a new field is added).
     * It fetches the updated definition, re-registers it, and triggers schema migration
     * (ALTER TABLE ADD COLUMN) for any new fields.
     *
     * @param collectionId the collection ID to refresh
     */
    @SuppressWarnings("unchecked")
    public void refreshCollection(String collectionId) {
        String collectionName = activeCollections.get(collectionId);
        if (collectionName == null) {
            log.warn("Cannot refresh unknown collection: {}", collectionId);
            return;
        }

        log.info("Refreshing collection definition: '{}' (id={})", collectionName, collectionId);

        try {
            // Get the current definition before refresh
            CollectionDefinition oldDefinition = collectionRegistry.get(collectionName);

            // Fetch updated definition from control plane
            String url = workerProperties.getControlPlaneUrl() + "/control/collections/" + collectionId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to fetch updated collection definition for {}: status={}",
                        collectionId, response.getStatusCode());
                return;
            }

            Map<String, Object> collectionData = response.getBody();
            CollectionDefinition newDefinition = buildCollectionDefinition(collectionName, collectionData);

            // Re-register with updated definition
            collectionRegistry.register(newDefinition);

            // Migrate the storage schema (ALTER TABLE for new fields)
            if (oldDefinition != null) {
                storageAdapter.updateCollectionSchema(oldDefinition, newDefinition);
                log.info("Schema migration completed for collection '{}' (id={})", collectionName, collectionId);
            } else {
                // No previous definition — initialize storage fresh
                storageAdapter.initializeCollection(newDefinition);
                log.info("Storage initialized for collection '{}' (id={})", collectionName, collectionId);
            }

            // Refresh validation rules
            fetchAndRegisterValidationRules(collectionId, collectionName);

        } catch (Exception e) {
            log.error("Failed to refresh collection '{}' (id={}): {}",
                    collectionName, collectionId, e.getMessage(), e);
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
            if (validationRuleRegistry != null) {
                validationRuleRegistry.unregister(collectionName);
            }
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

        // Parse storage config (normalize legacy "PHYSICAL_TABLE" → "PHYSICAL_TABLES")
        String storageMode = getStringOrDefault(data, "storageMode", "PHYSICAL_TABLES");
        if ("PHYSICAL_TABLE".equals(storageMode)) {
            storageMode = "PHYSICAL_TABLES";
        }
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

                        // Parse relationship metadata into ReferenceConfig
                        ReferenceConfig refConfig = null;
                        String referenceTarget = (String) field.get("referenceTarget");
                        String relationshipType = (String) field.get("relationshipType");
                        if (referenceTarget != null && !referenceTarget.isBlank()) {
                            boolean cascadeDelete = Boolean.TRUE.equals(field.get("cascadeDelete"));
                            String relationshipName = (String) field.get("relationshipName");
                            if ("MASTER_DETAIL".equals(relationshipType)) {
                                refConfig = ReferenceConfig.masterDetail(referenceTarget, relationshipName);
                            } else if ("LOOKUP".equals(relationshipType)) {
                                refConfig = ReferenceConfig.lookup(referenceTarget, relationshipName);
                            } else {
                                refConfig = ReferenceConfig.toCollection(referenceTarget);
                            }
                        }

                        // Parse fieldTypeConfig JSON into Map
                        Map<String, Object> parsedFieldTypeConfig = null;
                        Object fieldTypeConfigObj = field.get("fieldTypeConfig");
                        if (fieldTypeConfigObj instanceof String ftcStr && !ftcStr.isBlank()) {
                            try {
                                parsedFieldTypeConfig = objectMapper.readValue(ftcStr,
                                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                            } catch (Exception ex) {
                                log.warn("Failed to parse fieldTypeConfig for field '{}': {}", fieldName, ex.getMessage());
                            }
                        } else if (fieldTypeConfigObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> configMap = (Map<String, Object>) fieldTypeConfigObj;
                            parsedFieldTypeConfig = configMap;
                        }

                        FieldDefinition fieldDef = new FieldDefinition(
                                fieldName, fieldType, !required, false, unique,
                                null, null, null, refConfig, parsedFieldTypeConfig);
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
     * Fetches validation rules from the control plane and registers them
     * in the local validation rule registry.
     *
     * @param collectionId   the collection ID
     * @param collectionName the collection name (used as registry key)
     */
    @SuppressWarnings("unchecked")
    private void fetchAndRegisterValidationRules(String collectionId, String collectionName) {
        if (validationRuleRegistry == null) {
            return;
        }

        try {
            String url = workerProperties.getControlPlaneUrl()
                    + "/control/collections/" + collectionId + "/validation-rules";
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Failed to fetch validation rules for collection '{}': status={}",
                        collectionName, response.getStatusCode());
                validationRuleRegistry.register(collectionName, List.of());
                return;
            }

            List<?> rulesList = response.getBody();
            List<ValidationRuleDefinition> rules = new ArrayList<>();

            for (Object ruleObj : rulesList) {
                if (ruleObj instanceof Map<?, ?> ruleMap) {
                    Map<String, Object> rule = (Map<String, Object>) ruleMap;
                    String name = (String) rule.get("name");
                    String formula = (String) rule.get("errorConditionFormula");
                    String errorMessage = (String) rule.get("errorMessage");
                    String errorField = (String) rule.get("errorField");
                    String evaluateOn = (String) rule.get("evaluateOn");
                    boolean active = Boolean.TRUE.equals(rule.get("active"));

                    if (name != null && formula != null && errorMessage != null) {
                        rules.add(new ValidationRuleDefinition(
                                name, formula, errorMessage, errorField,
                                evaluateOn != null ? evaluateOn : "CREATE_AND_UPDATE",
                                active));
                    }
                }
            }

            validationRuleRegistry.register(collectionName, rules);
            long activeCount = rules.stream().filter(ValidationRuleDefinition::active).count();
            log.info("Registered {} validation rules ({} active) for collection '{}'",
                    rules.size(), activeCount, collectionName);

        } catch (Exception e) {
            log.warn("Failed to fetch validation rules for collection '{}': {}",
                    collectionName, e.getMessage());
            // Register empty list so validation doesn't fail if rules can't be fetched
            validationRuleRegistry.register(collectionName, List.of());
        }
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

    /**
     * Attempts to load and initialize a collection by name and tenant ID.
     *
     * <p>Looks up the collection from the control plane by name (scoped to the
     * given tenant), then initializes it locally (register + storage + validation
     * rules). If the collection is already loaded, returns the existing definition.
     *
     * <p>This method is thread-safe. Concurrent calls for the same collection
     * will only initialize it once due to the {@code activeCollections} check.
     *
     * @param collectionName the collection name
     * @param tenantId the tenant ID, may be {@code null}
     * @return the loaded collection definition, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    public com.emf.runtime.model.CollectionDefinition loadCollectionByName(String collectionName, String tenantId) {
        // Check if already loaded
        com.emf.runtime.model.CollectionDefinition existing = collectionRegistry.get(collectionName);
        if (existing != null) {
            return existing;
        }

        try {
            // Look up the collection by name from the control plane
            // The control plane's /control/collections/{idOrName} endpoint supports name lookup
            String url = workerProperties.getControlPlaneUrl() + "/control/collections/" + collectionName;

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (tenantId != null && !tenantId.isBlank()) {
                headers.set("X-Tenant-ID", tenantId);
            }
            org.springframework.http.HttpEntity<Void> requestEntity = new org.springframework.http.HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, requestEntity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Collection '{}' not found on control plane (tenantId={})", collectionName, tenantId);
                return null;
            }

            Map<String, Object> collectionData = response.getBody();
            String collectionId = (String) collectionData.get("id");

            if (collectionId == null) {
                log.warn("Control plane returned collection '{}' without an ID", collectionName);
                return null;
            }

            // Check again in case another thread initialized it while we were fetching
            if (activeCollections.containsKey(collectionId)) {
                return collectionRegistry.get(collectionName);
            }

            // Initialize the collection fully
            initializeCollection(collectionId);

            return collectionRegistry.get(collectionName);

        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.debug("Collection '{}' does not exist on control plane (tenantId={})", collectionName, tenantId);
            return null;
        } catch (Exception e) {
            log.warn("Failed to load collection '{}' on demand (tenantId={}): {}",
                    collectionName, tenantId, e.getMessage());
            return null;
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
