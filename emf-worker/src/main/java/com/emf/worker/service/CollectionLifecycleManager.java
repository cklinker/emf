package com.emf.worker.service;

import com.emf.runtime.context.TenantContext;
import com.emf.runtime.model.*;
import com.emf.runtime.model.system.SystemCollectionDefinitions;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.ValidationRuleDefinition;
import com.emf.runtime.validation.ValidationRuleRegistry;
import com.emf.worker.config.WorkerMetricsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of collections on this worker.
 *
 * <p>Reads collection and field definitions directly from the database using JDBC,
 * eliminating the need for a control plane service.
 *
 * <p>For system collections (those in {@link SystemCollectionDefinitions}), the canonical
 * in-memory definition is used directly, ensuring complete metadata (StorageConfig, ApiConfig,
 * EventsConfig, immutableFields, columnMapping, etc.) is always available.
 *
 * <p>For user-defined collections, definitions are built from database records.
 *
 * <p>Handles:
 * <ul>
 *   <li>Loading collection definitions from the database</li>
 *   <li>Building runtime CollectionDefinition objects</li>
 *   <li>Registering collections in the local registry</li>
 *   <li>Initializing storage (creating database tables for user collections)</li>
 *   <li>Tearing down collections when unassigned</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Service
public class CollectionLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(CollectionLifecycleManager.class);

    private static final String SELECT_COLLECTION_BY_ID = """
            SELECT id, name, display_name, description, storage_mode, active,
                   current_version, system_collection, path, tenant_id, display_field_id
            FROM collection WHERE id = ? AND active = true
            """;

    private static final String SELECT_COLLECTION_BY_NAME = """
            SELECT id, name, display_name, description, storage_mode, active,
                   current_version, system_collection, path, tenant_id, display_field_id
            FROM collection WHERE name = ? AND active = true
            LIMIT 1
            """;

    private static final String SELECT_FIELD_NAME_BY_ID = """
            SELECT name FROM field WHERE id = ? AND active = true
            """;

    private static final String SELECT_FIELDS_BY_COLLECTION = """
            SELECT name, type, required, unique_constraint, indexed, default_value,
                   constraints, field_type_config, reference_target, relationship_type,
                   relationship_name, cascade_delete, field_order, column_name, immutable
            FROM field WHERE collection_id = ? AND active = true
            ORDER BY field_order
            """;

    private static final String SELECT_VALIDATION_RULES = """
            SELECT name, error_condition_formula, error_message, error_field,
                   evaluate_on, active
            FROM validation_rule WHERE collection_id = ? AND active = true
            """;

    private static final String SELECT_TENANT_SLUG = """
            SELECT slug FROM tenant WHERE id = ?
            """;

    private final CollectionRegistry collectionRegistry;
    private final StorageAdapter storageAdapter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Canonical system collection definitions keyed by name. */
    private final Map<String, CollectionDefinition> systemDefinitions;

    /** Optional validation rule registry for storing custom validation rules. */
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
                                       JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper) {
        this.collectionRegistry = collectionRegistry;
        this.storageAdapter = storageAdapter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.systemDefinitions = SystemCollectionDefinitions.byName();
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
     * Initializes a collection on this worker by reading its definition from
     * the database, registering it locally, and initializing storage.
     *
     * @param collectionId the collection ID to initialize
     */
    public void initializeCollection(String collectionId) {
        log.info("Initializing collection: {}", collectionId);

        if (metricsConfig != null) {
            metricsConfig.getInitializingCount().incrementAndGet();
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    SELECT_COLLECTION_BY_ID, collectionId);

            if (rows.isEmpty()) {
                log.error("Collection not found in database: {}", collectionId);
                return;
            }

            Map<String, Object> collectionRow = rows.get(0);
            String collectionName = (String) collectionRow.get("name");

            if (collectionName == null || collectionName.isBlank()) {
                log.error("Collection {} has no name", collectionId);
                return;
            }

            // Build CollectionDefinition
            CollectionDefinition definition = buildDefinition(collectionId, collectionName, collectionRow);

            // Skip VIRTUAL collections — they have no physical storage
            if (definition.storageConfig() != null
                    && definition.storageConfig().mode() == StorageMode.VIRTUAL) {
                log.info("Skipping VIRTUAL collection '{}' (id={}) — no storage to initialize",
                        collectionName, collectionId);
                collectionRegistry.register(definition);
                activeCollections.put(collectionId, collectionName);
                return;
            }

            // Register in local registry (makes it available to DynamicCollectionRouter)
            collectionRegistry.register(definition);

            // Set tenant context so the storage adapter uses the correct schema
            setTenantContextFromRow(collectionRow);
            try {
                // Initialize storage (creates database table if needed)
                // System collections have Flyway-managed tables, so initializeCollection is a no-op for them
                storageAdapter.initializeCollection(definition);
            } finally {
                TenantContext.clear();
            }

            // Load and register validation rules from DB
            loadAndRegisterValidationRules(collectionId, collectionName);

            // Track as active
            activeCollections.put(collectionId, collectionName);

            log.info("Successfully initialized collection '{}' (id={})", collectionName, collectionId);

        } catch (Exception e) {
            log.error("Failed to initialize collection {}: {}", collectionId, e.getMessage(), e);
        } finally {
            if (metricsConfig != null) {
                metricsConfig.getInitializingCount().decrementAndGet();
            }
        }
    }

    /**
     * Refreshes a collection definition on this worker by reading the latest
     * schema from the database and migrating the storage schema.
     *
     * <p>This is called when a collection's fields change (e.g., a new field is added).
     * It reads the updated definition, re-registers it, and triggers schema migration
     * (ALTER TABLE ADD COLUMN) for any new fields.
     *
     * @param collectionId the collection ID to refresh
     */
    public void refreshCollection(String collectionId) {
        String collectionName = activeCollections.get(collectionId);
        if (collectionName == null) {
            log.warn("Cannot refresh unknown collection: {}", collectionId);
            return;
        }

        log.info("Refreshing collection definition: '{}' (id={})", collectionName, collectionId);

        try {
            CollectionDefinition oldDefinition = collectionRegistry.get(collectionName);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    SELECT_COLLECTION_BY_ID, collectionId);

            if (rows.isEmpty()) {
                log.error("Collection not found in database for refresh: {}", collectionId);
                return;
            }

            Map<String, Object> collectionRow = rows.get(0);
            CollectionDefinition newDefinition = buildDefinition(collectionId, collectionName, collectionRow);

            // Re-register with updated definition
            collectionRegistry.register(newDefinition);

            // Set tenant context so the storage adapter uses the correct schema
            setTenantContextFromRow(collectionRow);
            try {
                // Migrate the storage schema
                if (oldDefinition != null) {
                    storageAdapter.updateCollectionSchema(oldDefinition, newDefinition);
                    log.info("Schema migration completed for collection '{}' (id={})",
                            collectionName, collectionId);
                } else {
                    storageAdapter.initializeCollection(newDefinition);
                    log.info("Storage initialized for collection '{}' (id={})",
                            collectionName, collectionId);
                }
            } finally {
                TenantContext.clear();
            }

            // Refresh validation rules
            loadAndRegisterValidationRules(collectionId, collectionName);

        } catch (Exception e) {
            log.error("Failed to refresh collection '{}' (id={}): {}",
                    collectionName, collectionId, e.getMessage(), e);
        }
    }

    /**
     * Tears down a collection on this worker by removing it from the local registry.
     * Data is not dropped -- it persists in the database.
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
     * Attempts to load and initialize a collection by name and tenant ID.
     *
     * <p>Looks up the collection from the database by name, then initializes it
     * locally (register + storage + validation rules). If the collection is already
     * loaded, returns the existing definition.
     *
     * <p>This method is thread-safe. Concurrent calls for the same collection
     * will only initialize it once due to the {@code activeCollections} check.
     *
     * @param collectionName the collection name
     * @param tenantId       the tenant ID, may be {@code null}
     * @return the loaded collection definition, or {@code null} if not found
     */
    public CollectionDefinition loadCollectionByName(String collectionName, String tenantId) {
        // Check if already loaded
        CollectionDefinition existing = collectionRegistry.get(collectionName);
        if (existing != null) {
            return existing;
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    SELECT_COLLECTION_BY_NAME, collectionName);

            if (rows.isEmpty()) {
                log.debug("Collection '{}' not found in database (tenantId={})",
                        collectionName, tenantId);
                return null;
            }

            Map<String, Object> collectionRow = rows.get(0);
            String collectionId = (String) collectionRow.get("id");

            // Check again in case another thread initialized it
            if (activeCollections.containsKey(collectionId)) {
                return collectionRegistry.get(collectionName);
            }

            // Initialize the collection fully
            initializeCollection(collectionId);
            return collectionRegistry.get(collectionName);

        } catch (Exception e) {
            log.warn("Failed to load collection '{}' on demand (tenantId={}): {}",
                    collectionName, tenantId, e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Tenant Context Helpers
    // =========================================================================

    /**
     * Sets the TenantContext (tenant ID and slug) from a collection database row.
     * This is needed during bootstrap and refresh operations where no HTTP request
     * is in progress, so the storage adapter can resolve the correct tenant schema.
     */
    private void setTenantContextFromRow(Map<String, Object> collectionRow) {
        String tenantId = (String) collectionRow.get("tenant_id");
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.set(tenantId);
            try {
                String slug = jdbcTemplate.queryForObject(SELECT_TENANT_SLUG, String.class, tenantId);
                if (slug != null && !slug.isBlank()) {
                    TenantContext.setSlug(slug);
                }
            } catch (Exception e) {
                log.debug("Could not resolve tenant slug for tenant '{}': {}", tenantId, e.getMessage());
            }
        }
    }

    // =========================================================================
    // Definition Building
    // =========================================================================

    /**
     * Builds a CollectionDefinition for the given collection.
     *
     * <p>For system collections, uses the canonical definition from
     * {@link SystemCollectionDefinitions} which includes complete metadata
     * (StorageConfig with table name, ApiConfig, EventsConfig, immutableFields,
     * columnMapping, etc.).
     *
     * <p>For user-defined collections, builds the definition from database records.
     */
    private CollectionDefinition buildDefinition(String collectionId, String collectionName,
                                                   Map<String, Object> collectionRow) {
        // Use canonical definition for system collections
        CollectionDefinition systemDef = systemDefinitions.get(collectionName);
        if (systemDef != null) {
            return systemDef;
        }

        // Build from DB data for user-defined collections
        return buildDefinitionFromDb(collectionId, collectionName, collectionRow);
    }

    /**
     * Builds a CollectionDefinition from database records for user-defined collections.
     */
    private CollectionDefinition buildDefinitionFromDb(String collectionId, String collectionName,
                                                        Map<String, Object> data) {
        CollectionDefinitionBuilder builder = new CollectionDefinitionBuilder()
                .name(collectionName)
                .displayName(getStringOrNull(data, "display_name", collectionName))
                .description(getStringOrNull(data, "description", null))
                .apiConfig(ApiConfig.allEnabled("/api/" + collectionName));

        // Parse fields from the database
        List<FieldDefinition> fields = loadFieldsFromDb(collectionId);
        if (fields.isEmpty()) {
            fields.add(FieldDefinition.string("name"));
        }
        builder.fields(fields);

        // Resolve display field ID to field name
        String displayFieldId = getStringOrNull(data, "display_field_id", null);
        if (displayFieldId != null) {
            String displayFieldName = resolveFieldName(displayFieldId);
            if (displayFieldName != null) {
                builder.displayFieldName(displayFieldName);
            }
        }

        // Parse storage config
        String storageMode = getStringOrNull(data, "storage_mode", "PHYSICAL_TABLES");
        if ("PHYSICAL_TABLE".equals(storageMode)) {
            storageMode = "PHYSICAL_TABLES";
        }
        builder.storageConfig(new StorageConfig(
                StorageMode.valueOf(storageMode), "tbl_" + collectionName, null));

        // Parse system collection flag
        Boolean systemCollection = (Boolean) data.get("system_collection");
        if (Boolean.TRUE.equals(systemCollection)) {
            builder.systemCollection(true);
        }

        return builder.build();
    }

    /**
     * Loads field definitions from the database for a given collection.
     */
    private List<FieldDefinition> loadFieldsFromDb(String collectionId) {
        List<FieldDefinition> fields = new ArrayList<>();

        List<Map<String, Object>> fieldRows = jdbcTemplate.queryForList(
                SELECT_FIELDS_BY_COLLECTION, collectionId);

        for (Map<String, Object> row : fieldRows) {
            String fieldName = (String) row.get("name");
            String typeStr = (String) row.get("type");

            if (fieldName == null || typeStr == null) {
                continue;
            }

            FieldType fieldType = reverseMapFieldType(typeStr);
            boolean required = Boolean.TRUE.equals(row.get("required"));
            boolean unique = Boolean.TRUE.equals(row.get("unique_constraint"));
            boolean immutable = Boolean.TRUE.equals(row.get("immutable"));
            String columnName = (String) row.get("column_name");

            // Parse reference config
            ReferenceConfig refConfig = null;
            String referenceTarget = (String) row.get("reference_target");
            String relationshipType = (String) row.get("relationship_type");
            if (referenceTarget != null && !referenceTarget.isBlank()) {
                String relationshipName = (String) row.get("relationship_name");
                if ("MASTER_DETAIL".equals(relationshipType)) {
                    refConfig = ReferenceConfig.masterDetail(referenceTarget, relationshipName);
                } else if ("LOOKUP".equals(relationshipType)) {
                    refConfig = ReferenceConfig.lookup(referenceTarget, relationshipName);
                } else {
                    refConfig = ReferenceConfig.toCollection(referenceTarget);
                }
            }

            // Parse fieldTypeConfig JSON
            Map<String, Object> parsedFieldTypeConfig = null;
            Object fieldTypeConfigObj = row.get("field_type_config");
            if (fieldTypeConfigObj instanceof String ftcStr && !ftcStr.isBlank()) {
                try {
                    parsedFieldTypeConfig = objectMapper.readValue(ftcStr,
                            objectMapper.getTypeFactory().constructMapType(
                                    HashMap.class, String.class, Object.class));
                } catch (Exception ex) {
                    log.warn("Failed to parse fieldTypeConfig for field '{}': {}",
                            fieldName, ex.getMessage());
                }
            } else if (fieldTypeConfigObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> configMap = (Map<String, Object>) fieldTypeConfigObj;
                parsedFieldTypeConfig = configMap;
            }

            FieldDefinition fieldDef = new FieldDefinition(
                    fieldName, fieldType, !required, immutable, unique,
                    null, null, null, refConfig, parsedFieldTypeConfig, columnName);
            fields.add(fieldDef);
        }

        return fields;
    }

    /**
     * Loads validation rules from the database and registers them.
     */
    private void loadAndRegisterValidationRules(String collectionId, String collectionName) {
        if (validationRuleRegistry == null) {
            return;
        }

        try {
            List<Map<String, Object>> ruleRows = jdbcTemplate.queryForList(
                    SELECT_VALIDATION_RULES, collectionId);

            List<ValidationRuleDefinition> rules = new ArrayList<>();
            for (Map<String, Object> row : ruleRows) {
                String name = (String) row.get("name");
                String formula = (String) row.get("error_condition_formula");
                String errorMessage = (String) row.get("error_message");
                String errorField = (String) row.get("error_field");
                String evaluateOn = (String) row.get("evaluate_on");
                boolean active = Boolean.TRUE.equals(row.get("active"));

                if (name != null && formula != null && errorMessage != null) {
                    rules.add(new ValidationRuleDefinition(
                            name, formula, errorMessage, errorField,
                            evaluateOn != null ? evaluateOn : "CREATE_AND_UPDATE",
                            active));
                }
            }

            validationRuleRegistry.register(collectionName, rules);
            long activeCount = rules.stream().filter(ValidationRuleDefinition::active).count();
            log.info("Registered {} validation rules ({} active) for collection '{}'",
                    rules.size(), activeCount, collectionName);

        } catch (Exception e) {
            log.warn("Failed to load validation rules for collection '{}': {}",
                    collectionName, e.getMessage());
            validationRuleRegistry.register(collectionName, List.of());
        }
    }

    // =========================================================================
    // Field Type Mapping
    // =========================================================================

    /**
     * Reverse-maps the database field type string to a FieldType enum.
     *
     * <p>The database stores simplified type names (e.g., "string", "number")
     * from the forward mapping in SystemCollectionSeeder.mapFieldType().
     * This method converts them back to FieldType enum values.
     *
     * <p>For "number", defaults to DOUBLE (consistent with control-plane convention
     * where FieldService maps "number" to DOUBLE).
     */
    static FieldType reverseMapFieldType(String dbType) {
        if (dbType == null) {
            return FieldType.STRING;
        }
        return switch (dbType.toLowerCase()) {
            case "string" -> FieldType.STRING;
            case "number" -> FieldType.DOUBLE;
            case "boolean" -> FieldType.BOOLEAN;
            case "date" -> FieldType.DATE;
            case "datetime" -> FieldType.DATETIME;
            case "object" -> FieldType.JSON;
            case "array" -> FieldType.ARRAY;
            case "reference" -> FieldType.REFERENCE;
            default -> {
                // Try direct enum match for any non-standard values
                try {
                    yield FieldType.valueOf(dbType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown DB field type '{}', defaulting to STRING", dbType);
                    yield FieldType.STRING;
                }
            }
        };
    }

    /**
     * Resolves a field ID to its field name.
     */
    private String resolveFieldName(String fieldId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    SELECT_FIELD_NAME_BY_ID, fieldId);
            if (!rows.isEmpty()) {
                return (String) rows.get(0).get("name");
            }
        } catch (Exception e) {
            log.warn("Failed to resolve field name for id '{}': {}", fieldId, e.getMessage());
        }
        return null;
    }

    private String getStringOrNull(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return defaultValue;
    }
}
