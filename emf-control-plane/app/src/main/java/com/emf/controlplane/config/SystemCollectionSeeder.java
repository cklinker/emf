package com.emf.controlplane.config;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.ValidationRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds system collection definitions into the database at application startup.
 *
 * <p>For each system collection defined in {@link SystemCollectionDefinitions}:
 * <ol>
 *   <li>Checks if the collection exists in the {@code collection} table</li>
 *   <li>If not present, creates the collection entity with its field definitions</li>
 *   <li>If present, verifies that all expected fields exist and adds any missing ones</li>
 * </ol>
 *
 * <p>System collections are marked with {@code systemCollection=true} in the database.
 * Their physical tables are managed by Flyway migrations, NOT by the worker.
 *
 * <p>This seeder runs early (Order=10) to ensure system collections are available
 * before other components (like the gateway bootstrap) need them.
 */
@Component
@Order(10)
public class SystemCollectionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemCollectionSeeder.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** Default tenant ID from V9 migration — used for system-owned collection definitions. */
    static final String SYSTEM_TENANT_ID = "00000000-0000-0000-0000-000000000001";

    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;

    public SystemCollectionSeeder(CollectionRepository collectionRepository,
                                  FieldRepository fieldRepository) {
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CollectionDefinition> systemDefinitions = SystemCollectionDefinitions.all();
        log.info("Seeding {} system collection definitions...", systemDefinitions.size());

        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (CollectionDefinition definition : systemDefinitions) {
            SeedResult result = seedCollection(definition);
            switch (result) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }

        log.info("System collection seeding complete: {} created, {} updated, {} unchanged",
                created, updated, unchanged);
    }

    /**
     * Seeds a single system collection definition into the database.
     *
     * @param definition the collection definition to seed
     * @return the result of the seeding operation
     */
    private SeedResult seedCollection(CollectionDefinition definition) {
        Optional<Collection> existing = collectionRepository.findByName(definition.name());

        if (existing.isEmpty()) {
            createCollection(definition);
            return SeedResult.CREATED;
        }

        Collection collection = existing.get();

        // Ensure it's marked as a system collection
        if (!collection.isSystemCollection()) {
            collection.setSystemCollection(true);
            collectionRepository.save(collection);
            log.info("Marked existing collection '{}' as system collection", definition.name());
        }

        // Sync fields - add any missing fields
        boolean fieldsUpdated = syncFields(collection, definition);
        if (fieldsUpdated) {
            return SeedResult.UPDATED;
        }

        return SeedResult.UNCHANGED;
    }

    /**
     * Creates a new collection entity from the definition.
     */
    private void createCollection(CollectionDefinition definition) {
        Collection collection = new Collection();
        collection.setName(definition.name());
        collection.setDisplayName(definition.displayName());
        collection.setDescription(definition.description());
        collection.setStorageMode(definition.storageConfig().mode().name());
        collection.setSystemCollection(true);
        collection.setActive(true);
        collection.setCurrentVersion(1);
        collection.setTenantId(SYSTEM_TENANT_ID);

        // Set path for API routing
        if (definition.apiConfig() != null) {
            collection.setPath(definition.apiConfig().basePath());
        }

        // Create field entities
        int fieldOrder = 0;
        for (FieldDefinition fieldDef : definition.fields()) {
            Field field = createFieldEntity(fieldDef, fieldOrder++);
            collection.addField(field);
        }

        collectionRepository.save(collection);
        log.info("Created system collection '{}' with {} fields (table: {})",
                definition.name(), definition.fields().size(),
                definition.storageConfig().tableName());
    }

    /**
     * Syncs field definitions between the database and the definition.
     * Adds any missing fields. Does NOT remove extra fields (to be safe).
     *
     * @return true if any fields were added
     */
    private boolean syncFields(Collection collection, CollectionDefinition definition) {
        List<Field> existingFields = fieldRepository.findByCollectionId(collection.getId());
        Set<String> existingFieldNames = existingFields.stream()
                .map(Field::getName)
                .collect(Collectors.toSet());

        boolean updated = false;
        int fieldOrder = existingFields.size();

        for (FieldDefinition fieldDef : definition.fields()) {
            if (!existingFieldNames.contains(fieldDef.name())) {
                Field field = createFieldEntity(fieldDef, fieldOrder++);
                collection.addField(field);
                updated = true;
                log.info("Added missing field '{}' to system collection '{}'",
                        fieldDef.name(), definition.name());
            }
        }

        if (updated) {
            collectionRepository.save(collection);
        }

        return updated;
    }

    /**
     * Creates a Field JPA entity from a FieldDefinition, persisting all available metadata.
     */
    private Field createFieldEntity(FieldDefinition fieldDef, int order) {
        Field field = new Field();
        field.setName(fieldDef.name());
        field.setDisplayName(fieldDef.name());
        field.setType(mapFieldType(fieldDef.type()));
        field.setRequired(!fieldDef.nullable());
        field.setUnique(fieldDef.unique());
        field.setOrder(order);
        field.setActive(true);

        // Column name mapping (API name -> DB column)
        if (fieldDef.columnName() != null) {
            field.setColumnName(fieldDef.columnName());
        }

        // Immutable flag
        field.setImmutable(fieldDef.immutable());

        // Indexed: unique fields and reference fields should be indexed
        field.setIndexed(fieldDef.unique() || fieldDef.referenceConfig() != null);

        // Default value
        if (fieldDef.defaultValue() != null) {
            field.setDefaultValue(serializeToJson(fieldDef.defaultValue()));
        }

        // Constraints: combine validationRules + enumValues into structured JSON
        String constraintsJson = serializeConstraints(fieldDef.validationRules(), fieldDef.enumValues());
        if (constraintsJson != null) {
            field.setConstraints(constraintsJson);
        }

        // Field type config
        if (fieldDef.fieldTypeConfig() != null && !fieldDef.fieldTypeConfig().isEmpty()) {
            field.setFieldTypeConfig(serializeToJson(fieldDef.fieldTypeConfig()));
        }

        // Reference config
        if (fieldDef.referenceConfig() != null) {
            field.setReferenceTarget(fieldDef.referenceConfig().targetCollection());
            field.setRelationshipType(fieldDef.referenceConfig().relationshipType());
            field.setRelationshipName(fieldDef.referenceConfig().relationshipName());
            field.setCascadeDelete(fieldDef.referenceConfig().cascadeDelete());
        }

        return field;
    }

    /**
     * Maps runtime FieldType to the string type stored in the database.
     */
    private String mapFieldType(com.emf.runtime.model.FieldType fieldType) {
        return switch (fieldType) {
            case STRING -> "string";
            case INTEGER -> "number";
            case LONG -> "number";
            case DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            case DATE -> "date";
            case DATETIME -> "datetime";
            case JSON -> "object";
            case ARRAY -> "array";
            case PICKLIST -> "string";
            case MULTI_PICKLIST -> "array";
            case CURRENCY -> "number";
            case PERCENT -> "number";
            case AUTO_NUMBER -> "string";
            case PHONE -> "string";
            case EMAIL -> "string";
            case URL -> "string";
            case RICH_TEXT -> "string";
            case ENCRYPTED -> "string";
            case EXTERNAL_ID -> "string";
            case GEOLOCATION -> "number";
            case REFERENCE -> "string";
            case LOOKUP -> "string";
            case MASTER_DETAIL -> "string";
            case FORMULA -> "string";
            case ROLLUP_SUMMARY -> "string";
        };
    }

    /**
     * Serializes a value to JSON string for JSONB columns.
     */
    private String serializeToJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value to JSON: {}", value, e);
            return null;
        }
    }

    /**
     * Serializes validation rules and enum values into a structured JSON constraints object.
     *
     * @return JSON string or null if no constraints
     */
    private String serializeConstraints(ValidationRules rules, List<String> enumValues) {
        Map<String, Object> constraints = new LinkedHashMap<>();

        if (rules != null) {
            if (rules.minLength() != null) {
                constraints.put("minLength", rules.minLength());
            }
            if (rules.maxLength() != null) {
                constraints.put("maxLength", rules.maxLength());
            }
            if (rules.pattern() != null) {
                constraints.put("pattern", rules.pattern());
            }
            if (rules.minValue() != null) {
                constraints.put("minValue", rules.minValue());
            }
            if (rules.maxValue() != null) {
                constraints.put("maxValue", rules.maxValue());
            }
        }

        if (enumValues != null && !enumValues.isEmpty()) {
            constraints.put("enumValues", enumValues);
        }

        if (constraints.isEmpty()) {
            return null;
        }

        return serializeToJson(constraints);
    }

    private enum SeedResult {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
