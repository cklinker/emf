package com.emf.runtime.query;

import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.events.RecordEventPublisher;
import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.service.AutoNumberService;
import com.emf.runtime.service.FieldEncryptionService;
import com.emf.runtime.service.RollupSummaryService;
import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.storage.StorageAdapter;
import com.emf.runtime.validation.CustomValidationRuleEngine;
import com.emf.runtime.validation.OperationType;
import com.emf.runtime.validation.TypeCoercionService;
import com.emf.runtime.validation.ValidationEngine;
import com.emf.runtime.validation.ValidationException;
import com.emf.runtime.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Default implementation of the QueryEngine interface.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Validates query parameters against collection definitions</li>
 *   <li>Integrates with StorageAdapter for persistence</li>
 *   <li>Integrates with ValidationEngine for data validation</li>
 *   <li>Integrates with FormulaEvaluator for FORMULA field computation</li>
 *   <li>Integrates with RollupSummaryService for ROLLUP_SUMMARY computation</li>
 *   <li>Integrates with FieldEncryptionService for ENCRYPTED field handling</li>
 *   <li>Integrates with AutoNumberService for AUTO_NUMBER generation</li>
 *   <li>Integrates with RecordEventPublisher for publishing record change events</li>
 *   <li>Adds system fields (id, createdAt, updatedAt, createdBy, updatedBy) automatically</li>
 *   <li>Logs query performance metrics</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe. All operations delegate to
 * thread-safe components (StorageAdapter, ValidationEngine).
 *
 * @since 1.0.0
 */
public class DefaultQueryEngine implements QueryEngine {

    private static final Logger logger = LoggerFactory.getLogger(DefaultQueryEngine.class);

    private static final Set<String> SYSTEM_FIELDS = Set.of("id", "createdAt", "updatedAt", "createdBy", "updatedBy");

    private final StorageAdapter storageAdapter;
    private final ValidationEngine validationEngine;
    private final FieldEncryptionService encryptionService;
    private final AutoNumberService autoNumberService;
    private final FormulaEvaluator formulaEvaluator;
    private final RollupSummaryService rollupSummaryService;
    private final CustomValidationRuleEngine customValidationRuleEngine;
    private final RecordEventPublisher recordEventPublisher;

    /**
     * Creates a new DefaultQueryEngine with all services including record event publisher.
     *
     * @param storageAdapter the storage adapter for persistence
     * @param validationEngine the validation engine for data validation (may be null)
     * @param encryptionService the encryption service for ENCRYPTED fields (may be null)
     * @param autoNumberService the auto-number service for AUTO_NUMBER fields (may be null)
     * @param formulaEvaluator the formula evaluator for FORMULA fields (may be null)
     * @param rollupSummaryService the rollup summary service for ROLLUP_SUMMARY fields (may be null)
     * @param customValidationRuleEngine the custom validation rule engine (may be null)
     * @param recordEventPublisher the publisher for record change events (may be null)
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter, ValidationEngine validationEngine,
                              FieldEncryptionService encryptionService, AutoNumberService autoNumberService,
                              FormulaEvaluator formulaEvaluator, RollupSummaryService rollupSummaryService,
                              CustomValidationRuleEngine customValidationRuleEngine,
                              RecordEventPublisher recordEventPublisher) {
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter cannot be null");
        this.validationEngine = validationEngine;
        this.encryptionService = encryptionService;
        this.autoNumberService = autoNumberService;
        this.formulaEvaluator = formulaEvaluator;
        this.rollupSummaryService = rollupSummaryService;
        this.customValidationRuleEngine = customValidationRuleEngine;
        this.recordEventPublisher = recordEventPublisher;
    }

    /**
     * Creates a new DefaultQueryEngine with all services (backward compatible, no event publisher).
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter, ValidationEngine validationEngine,
                              FieldEncryptionService encryptionService, AutoNumberService autoNumberService,
                              FormulaEvaluator formulaEvaluator, RollupSummaryService rollupSummaryService,
                              CustomValidationRuleEngine customValidationRuleEngine) {
        this(storageAdapter, validationEngine, encryptionService, autoNumberService,
             formulaEvaluator, rollupSummaryService, customValidationRuleEngine, null);
    }

    /**
     * Creates a new DefaultQueryEngine with all services (backward compatible).
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter, ValidationEngine validationEngine,
                              FieldEncryptionService encryptionService, AutoNumberService autoNumberService,
                              FormulaEvaluator formulaEvaluator, RollupSummaryService rollupSummaryService) {
        this(storageAdapter, validationEngine, encryptionService, autoNumberService,
             formulaEvaluator, rollupSummaryService, null, null);
    }

    /**
     * Creates a new DefaultQueryEngine.
     *
     * @param storageAdapter the storage adapter for persistence
     * @param validationEngine the validation engine for data validation (may be null)
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter, ValidationEngine validationEngine) {
        this(storageAdapter, validationEngine, null, null, null, null, null, null);
    }

    /**
     * Creates a new DefaultQueryEngine without validation.
     *
     * @param storageAdapter the storage adapter for persistence
     */
    public DefaultQueryEngine(StorageAdapter storageAdapter) {
        this(storageAdapter, null);
    }
    
    @Override
    public QueryResult executeQuery(CollectionDefinition definition, QueryRequest request) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        
        long startTime = System.currentTimeMillis();
        
        // Validate sort fields exist
        validateSortFields(definition, request.sorting());
        
        // Validate filter fields exist
        validateFilterFields(definition, request.filters());
        
        // Validate requested fields exist
        validateRequestedFields(definition, request.fields());
        
        // Execute query via storage adapter
        QueryResult result = storageAdapter.query(definition, request);

        // Compute formula and rollup fields on results
        computeVirtualFields(definition, result.data());

        // Decrypt encrypted fields on results
        decryptFields(definition, result.data());

        // Log query performance
        long duration = System.currentTimeMillis() - startTime;
        logger.debug("Query executed on collection '{}': {} records returned in {}ms",
            definition.name(), result.size(), duration);

        return result;
    }
    
    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        
        Optional<Map<String, Object>> result = storageAdapter.getById(definition, id);
        result.ifPresent(record -> {
            computeVirtualFields(definition, List.of(record));
            decryptFields(definition, List.of(record));
        });
        return result;
    }
    
    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        // Reject writes to read-only collections
        if (definition.readOnly()) {
            throw new ReadOnlyCollectionException(definition.name());
        }

        // Create mutable copy
        Map<String, Object> recordData = new HashMap<>(data);
        
        // Add system fields
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        recordData.put("id", id);
        recordData.put("createdAt", now);
        recordData.put("updatedAt", now);

        // Generate auto-number values for AUTO_NUMBER fields
        generateAutoNumbers(definition, recordData);

        // Encrypt ENCRYPTED field values before validation and storage
        encryptFields(definition, recordData);

        // Coerce string values to expected types (e.g., "10" → 10.0 for DOUBLE fields)
        TypeCoercionService.coerce(definition, recordData);

        // Validate data (field-level constraints)
        if (validationEngine != null) {
            ValidationResult validation = validationEngine.validate(definition, recordData, OperationType.CREATE);
            if (!validation.valid()) {
                throw new ValidationException(validation);
            }
        }

        // Evaluate custom validation rules (formula-based)
        if (customValidationRuleEngine != null) {
            customValidationRuleEngine.evaluate(definition.name(), recordData, OperationType.CREATE);
        }

        // Persist via storage adapter
        Map<String, Object> created = storageAdapter.create(definition, recordData);

        logger.debug("Created record '{}' in collection '{}'", id, definition.name());

        // Publish record change event
        publishRecordEvent(RecordChangeEvent.created(
                extractTenantId(recordData), definition.name(), id, created,
                extractUserId(recordData)));

        return created;
    }

    @Override
    public Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        // Reject writes to read-only collections
        if (definition.readOnly()) {
            throw new ReadOnlyCollectionException(definition.name());
        }

        // Check if record exists
        Optional<Map<String, Object>> existing = storageAdapter.getById(definition, id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        // Create mutable copy
        Map<String, Object> recordData = new HashMap<>(data);

        // Update timestamp
        recordData.put("updatedAt", Instant.now());

        // Don't allow changing id, createdAt, or createdBy
        recordData.remove("id");
        recordData.remove("createdAt");
        recordData.remove("createdBy");

        // Strip immutable fields from update data
        if (!definition.immutableFields().isEmpty()) {
            for (String immutableField : definition.immutableFields()) {
                if (recordData.containsKey(immutableField)) {
                    logger.debug("Stripping immutable field '{}' from update on collection '{}'",
                            immutableField, definition.name());
                    recordData.remove(immutableField);
                }
            }
        }

        // Encrypt ENCRYPTED fields before storage
        encryptFields(definition, recordData);

        // Coerce string values to expected types (e.g., "10" → 10.0 for DOUBLE fields)
        TypeCoercionService.coerce(definition, recordData);

        // Merge with existing data for validation
        Map<String, Object> mergedData = new HashMap<>(existing.get());
        mergedData.putAll(recordData);

        // Validate data (field-level constraints)
        if (validationEngine != null) {
            ValidationResult validation = validationEngine.validate(definition, mergedData, OperationType.UPDATE, id);
            if (!validation.valid()) {
                throw new ValidationException(validation);
            }
        }

        // Evaluate custom validation rules (formula-based) against the merged record
        if (customValidationRuleEngine != null) {
            customValidationRuleEngine.evaluate(definition.name(), mergedData, OperationType.UPDATE);
        }

        // Capture previous data for event publishing (before persist overwrites)
        Map<String, Object> previousData = new HashMap<>(existing.get());

        // Persist via storage adapter
        Optional<Map<String, Object>> updated = storageAdapter.update(definition, id, recordData);

        updated.ifPresent(record -> {
            logger.debug("Updated record '{}' in collection '{}'", id, definition.name());

            // Compute changed fields by comparing previous data with the update data
            List<String> changedFields = computeChangedFields(previousData, recordData);

            // Publish record change event
            publishRecordEvent(RecordChangeEvent.updated(
                    extractTenantId(mergedData), definition.name(), id, mergedData,
                    previousData, changedFields, extractUserId(mergedData)));
        });

        return updated;
    }
    
    @Override
    public boolean delete(CollectionDefinition definition, String id) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(id, "id cannot be null");

        // Reject deletes on read-only collections
        if (definition.readOnly()) {
            throw new ReadOnlyCollectionException(definition.name());
        }

        // Pre-fetch record data for the delete event (only when publisher is wired)
        Map<String, Object> recordData = null;
        if (recordEventPublisher != null) {
            recordData = storageAdapter.getById(definition, id).orElse(null);
        }

        boolean deleted = storageAdapter.delete(definition, id);

        if (deleted) {
            logger.debug("Deleted record '{}' from collection '{}'", id, definition.name());

            // Publish record change event with the pre-fetched data
            if (recordData != null) {
                publishRecordEvent(RecordChangeEvent.deleted(
                        extractTenantId(recordData), definition.name(), id, recordData,
                        extractUserId(recordData)));
            }
        }

        return deleted;
    }
    
    /**
     * Validates that all sort fields exist in the collection definition.
     */
    private void validateSortFields(CollectionDefinition definition, List<SortField> sorting) {
        for (SortField sortField : sorting) {
            if (!isValidField(definition, sortField.fieldName())) {
                throw new InvalidQueryException(sortField.fieldName(), 
                    "Sort field does not exist in collection '" + definition.name() + "'");
            }
        }
    }
    
    /**
     * Validates that all filter fields exist in the collection definition.
     */
    private void validateFilterFields(CollectionDefinition definition, List<FilterCondition> filters) {
        for (FilterCondition filter : filters) {
            if (!isValidField(definition, filter.fieldName())) {
                throw new InvalidQueryException(filter.fieldName(),
                    "Filter field does not exist in collection '" + definition.name() + "'");
            }
        }
    }
    
    /**
     * Validates that all requested fields exist in the collection definition.
     */
    private void validateRequestedFields(CollectionDefinition definition, List<String> fields) {
        for (String field : fields) {
            if (!isValidField(definition, field)) {
                throw new InvalidQueryException(field,
                    "Requested field does not exist in collection '" + definition.name() + "'");
            }
        }
    }
    
    /**
     * Checks if a field name is valid for the collection.
     * System fields (id, createdAt, updatedAt, createdBy, updatedBy) are always valid.
     */
    private boolean isValidField(CollectionDefinition definition, String fieldName) {
        if (SYSTEM_FIELDS.contains(fieldName)) {
            return true;
        }
        return definition.getField(fieldName) != null;
    }

    /**
     * Generates auto-number values for AUTO_NUMBER fields during record creation.
     */
    private void generateAutoNumbers(CollectionDefinition definition, Map<String, Object> data) {
        if (autoNumberService == null) {
            return;
        }
        for (FieldDefinition field : definition.fields()) {
            if (field.type() == FieldType.AUTO_NUMBER && !data.containsKey(field.name())) {
                String seqName = "seq_" + definition.name() + "_" + field.name();
                Map<String, Object> config = field.fieldTypeConfig();
                String prefix = "";
                int padding = 6;
                if (config != null) {
                    if (config.containsKey("prefix")) {
                        prefix = config.get("prefix").toString();
                    }
                    if (config.containsKey("padding")) {
                        padding = ((Number) config.get("padding")).intValue();
                    }
                }
                try {
                    String value = autoNumberService.generateNext(seqName, prefix, padding);
                    data.put(field.name(), value);
                } catch (Exception e) {
                    logger.warn("Failed to generate auto-number for field '{}': {}", field.name(), e.getMessage());
                }
            }
        }
    }

    /**
     * Encrypts ENCRYPTED field values before storage.
     */
    private void encryptFields(CollectionDefinition definition, Map<String, Object> data) {
        if (encryptionService == null) {
            return;
        }
        String tenantId = data.containsKey("tenantId") ? data.get("tenantId").toString() : "default";
        for (FieldDefinition field : definition.fields()) {
            if (field.type() == FieldType.ENCRYPTED && data.containsKey(field.name())) {
                Object value = data.get(field.name());
                if (value instanceof String plaintext) {
                    byte[] encrypted = encryptionService.encrypt(plaintext, tenantId);
                    data.put(field.name(), encrypted);
                }
            }
        }
    }

    /**
     * Decrypts ENCRYPTED field values after retrieval.
     */
    private void decryptFields(CollectionDefinition definition, List<Map<String, Object>> records) {
        if (encryptionService == null) {
            return;
        }
        for (Map<String, Object> record : records) {
            String tenantId = record.containsKey("tenantId") ? record.get("tenantId").toString() : "default";
            for (FieldDefinition field : definition.fields()) {
                if (field.type() == FieldType.ENCRYPTED && record.containsKey(field.name())) {
                    Object value = record.get(field.name());
                    if (value instanceof byte[] encryptedData) {
                        try {
                            String decrypted = encryptionService.decrypt(encryptedData, tenantId);
                            record.put(field.name(), decrypted);
                        } catch (Exception e) {
                            logger.warn("Failed to decrypt field '{}': {}", field.name(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Publishes a record change event if a publisher is configured.
     * Failures are handled gracefully — they are logged but do not cause the
     * main CRUD operation to fail.
     */
    private void publishRecordEvent(RecordChangeEvent event) {
        if (recordEventPublisher == null) {
            return;
        }
        try {
            recordEventPublisher.publish(event);
        } catch (Exception e) {
            logger.error("Failed to publish record change event for record '{}' in collection '{}': {}",
                event.getRecordId(), event.getCollectionName(), e.getMessage());
        }
    }

    /**
     * Computes the list of field names that changed between the previous record
     * and the update data. Only includes fields present in the update data
     * whose values differ from the previous record.
     */
    private List<String> computeChangedFields(Map<String, Object> previousData, Map<String, Object> updateData) {
        List<String> changedFields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : updateData.entrySet()) {
            String fieldName = entry.getKey();
            // Skip system fields that are always updated
            if ("updatedAt".equals(fieldName)) {
                continue;
            }
            Object newValue = entry.getValue();
            Object oldValue = previousData.get(fieldName);
            if (!Objects.equals(newValue, oldValue)) {
                changedFields.add(fieldName);
            }
        }
        return changedFields;
    }

    /**
     * Extracts the tenant ID from record data, defaulting to "default".
     */
    private String extractTenantId(Map<String, Object> data) {
        Object tenantId = data.get("tenantId");
        return tenantId != null ? tenantId.toString() : "default";
    }

    /**
     * Extracts the user ID from record data, defaulting to "system".
     */
    private String extractUserId(Map<String, Object> data) {
        Object userId = data.get("createdBy");
        if (userId == null) {
            userId = data.get("updatedBy");
        }
        return userId != null ? userId.toString() : "system";
    }

    /**
     * Computes FORMULA and ROLLUP_SUMMARY field values after retrieval.
     */
    private void computeVirtualFields(CollectionDefinition definition, List<Map<String, Object>> records) {
        for (Map<String, Object> record : records) {
            for (FieldDefinition field : definition.fields()) {
                // TODO: FORMULA and ROLLUP_SUMMARY require field-type-specific config
                // (expression, childTable, etc.) which is not yet available on FieldDefinition.
                // These will be wired once FieldDefinition carries a config map.
                if (field.type() == FieldType.FORMULA && formulaEvaluator != null) {
                    // Formula expression would come from field config; skip until available
                    logger.debug("Skipping formula field '{}' — config not available on FieldDefinition", field.name());
                } else if (field.type() == FieldType.ROLLUP_SUMMARY && rollupSummaryService != null) {
                    // Rollup config would come from field config; skip until available
                    logger.debug("Skipping rollup field '{}' — config not available on FieldDefinition", field.name());
                }
            }
        }
    }
}
