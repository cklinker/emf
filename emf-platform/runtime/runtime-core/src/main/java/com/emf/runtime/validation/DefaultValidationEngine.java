package com.emf.runtime.validation;

import com.emf.runtime.model.CollectionDefinition;
import com.emf.runtime.model.FieldDefinition;
import com.emf.runtime.model.FieldType;
import com.emf.runtime.model.ReferenceConfig;
import com.emf.runtime.model.ValidationRules;
import com.emf.runtime.registry.CollectionRegistry;
import com.emf.runtime.storage.StorageAdapter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Default implementation of the ValidationEngine interface.
 * 
 * <p>This implementation is stateless and thread-safe, validating data against
 * collection definitions without maintaining any mutable state. All validation
 * operations are independent and can be executed concurrently.
 * 
 * <p>The engine validates the following constraints:
 * <ol>
 *   <li><b>Nullable</b> - reject null values for non-nullable fields</li>
 *   <li><b>Type</b> - value matches expected FieldType</li>
 *   <li><b>Min/Max value</b> - for numeric fields (INTEGER, LONG, DOUBLE)</li>
 *   <li><b>Min/Max length</b> - for STRING fields</li>
 *   <li><b>Pattern</b> - regex validation for STRING fields</li>
 *   <li><b>Immutable</b> - reject updates to immutable fields</li>
 *   <li><b>Unique</b> - check via StorageAdapter.isUnique()</li>
 *   <li><b>Enum</b> - value must be in enumValues list</li>
 *   <li><b>Reference</b> - referenced record must exist in target collection</li>
 * </ol>
 * 
 * @since 1.0.0
 */
public class DefaultValidationEngine implements ValidationEngine {
    
    private final StorageAdapter storageAdapter;
    private final CollectionRegistry collectionRegistry;
    
    /**
     * Creates a new DefaultValidationEngine.
     * 
     * @param storageAdapter the storage adapter for unique and reference checks
     * @param collectionRegistry the collection registry for reference validation (may be null if reference validation not needed)
     */
    public DefaultValidationEngine(StorageAdapter storageAdapter, CollectionRegistry collectionRegistry) {
        this.storageAdapter = Objects.requireNonNull(storageAdapter, "storageAdapter cannot be null");
        this.collectionRegistry = collectionRegistry;
    }
    
    /**
     * Creates a new DefaultValidationEngine without reference validation support.
     * 
     * @param storageAdapter the storage adapter for unique checks
     */
    public DefaultValidationEngine(StorageAdapter storageAdapter) {
        this(storageAdapter, null);
    }
    
    @Override
    public ValidationResult validate(CollectionDefinition definition, Map<String, Object> data, OperationType operationType) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(operationType, "operationType cannot be null");
        
        List<FieldError> errors = new ArrayList<>();
        
        for (FieldDefinition field : definition.fields()) {
            validateField(definition, field, data, operationType, errors);
        }
        
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
    
    /**
     * Validates a single field against its definition.
     */
    private void validateField(
            CollectionDefinition definition,
            FieldDefinition field,
            Map<String, Object> data,
            OperationType operationType,
            List<FieldError> errors) {
        
        String fieldName = field.name();
        Object value = data.get(fieldName);
        boolean fieldProvided = data.containsKey(fieldName);
        
        // For UPDATE operations, skip validation of fields not provided in the data
        // (partial updates are allowed)
        if (operationType == OperationType.UPDATE && !fieldProvided) {
            return;
        }
        
        // 1. Nullable constraint check
        if (value == null) {
            if (!field.nullable()) {
                errors.add(FieldError.nullable(fieldName));
            }
            // Skip other validations if null (null is valid for nullable fields)
            return;
        }
        
        // 2. Immutable constraint check (only for updates)
        if (operationType == OperationType.UPDATE && field.immutable() && fieldProvided) {
            errors.add(FieldError.immutable(fieldName));
            // Continue with other validations even if immutable is violated
        }
        
        // 3. Type validation
        if (!isValidType(value, field.type())) {
            errors.add(FieldError.invalidType(fieldName, field.type().name()));
            // Skip further validations if type is wrong
            return;
        }
        
        // 4. Validation rules (min/max value, min/max length, pattern)
        ValidationRules rules = field.validationRules();
        if (rules != null) {
            validateRules(field, value, rules, errors);
        }
        
        // 5. Enum validation
        if (field.enumValues() != null && !field.enumValues().isEmpty()) {
            validateEnum(field, value, errors);
        }
        
        // 6. Unique constraint validation
        if (field.unique()) {
            validateUnique(definition, field, value, data, errors);
        }
        
        // 7. Reference validation
        if (field.referenceConfig() != null) {
            validateReference(field, value, errors);
        }
    }
    
    /**
     * Validates that the value matches the expected field type.
     */
    private boolean isValidType(Object value, FieldType expectedType) {
        return switch (expectedType) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || isIntegerCompatible(value);
            case LONG -> value instanceof Long || value instanceof Integer || isLongCompatible(value);
            case DOUBLE -> value instanceof Double || value instanceof Float || 
                           value instanceof Long || value instanceof Integer || isDoubleCompatible(value);
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> isValidDate(value);
            case DATETIME -> isValidDateTime(value);
            case JSON -> value instanceof Map || value instanceof List;
        };
    }
    
    /**
     * Checks if a value can be interpreted as an Integer.
     */
    private boolean isIntegerCompatible(Object value) {
        if (value instanceof Number num) {
            double d = num.doubleValue();
            return d == Math.floor(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
        }
        return false;
    }
    
    /**
     * Checks if a value can be interpreted as a Long.
     */
    private boolean isLongCompatible(Object value) {
        if (value instanceof Number num) {
            double d = num.doubleValue();
            return d == Math.floor(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE;
        }
        return false;
    }
    
    /**
     * Checks if a value can be interpreted as a Double.
     */
    private boolean isDoubleCompatible(Object value) {
        return value instanceof Number;
    }
    
    /**
     * Validates a date value (ISO-8601 format string or LocalDate).
     */
    private boolean isValidDate(Object value) {
        if (value instanceof LocalDate) {
            return true;
        }
        if (value instanceof String str) {
            try {
                LocalDate.parse(str);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Validates a datetime value (ISO-8601 format string or LocalDateTime).
     */
    private boolean isValidDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return true;
        }
        if (value instanceof java.time.Instant) {
            return true;
        }
        if (value instanceof String str) {
            try {
                // Try parsing as LocalDateTime first
                LocalDateTime.parse(str);
                return true;
            } catch (DateTimeParseException e) {
                try {
                    // Try parsing as Instant (ISO-8601 with timezone)
                    java.time.Instant.parse(str);
                    return true;
                } catch (DateTimeParseException e2) {
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Validates min/max value and min/max length constraints.
     */
    private void validateRules(FieldDefinition field, Object value, ValidationRules rules, List<FieldError> errors) {
        String fieldName = field.name();
        
        // Min/Max value validation for numeric types
        if (value instanceof Number num) {
            double numValue = num.doubleValue();
            
            if (rules.minValue() != null && numValue < rules.minValue()) {
                errors.add(FieldError.minValue(fieldName, rules.minValue()));
            }
            if (rules.maxValue() != null && numValue > rules.maxValue()) {
                errors.add(FieldError.maxValue(fieldName, rules.maxValue()));
            }
        }
        
        // Length and pattern validation for strings
        if (value instanceof String str) {
            int length = str.length();
            
            if (rules.minLength() != null && length < rules.minLength()) {
                errors.add(FieldError.minLength(fieldName, rules.minLength()));
            }
            if (rules.maxLength() != null && length > rules.maxLength()) {
                errors.add(FieldError.maxLength(fieldName, rules.maxLength()));
            }
            
            // Pattern validation
            if (rules.pattern() != null) {
                try {
                    Pattern pattern = Pattern.compile(rules.pattern());
                    if (!pattern.matcher(str).matches()) {
                        errors.add(FieldError.pattern(fieldName));
                    }
                } catch (PatternSyntaxException e) {
                    // Invalid pattern in definition - log and skip
                    // In production, this should be caught during collection definition validation
                }
            }
        }
    }
    
    /**
     * Validates that the value is in the allowed enum values list.
     */
    private void validateEnum(FieldDefinition field, Object value, List<FieldError> errors) {
        String stringValue = value.toString();
        if (!field.enumValues().contains(stringValue)) {
            errors.add(FieldError.enumViolation(field.name(), field.enumValues()));
        }
    }
    
    /**
     * Validates that the value is unique in the collection.
     */
    private void validateUnique(
            CollectionDefinition definition,
            FieldDefinition field,
            Object value,
            Map<String, Object> data,
            List<FieldError> errors) {
        
        // Get the record ID to exclude from uniqueness check (for updates)
        String excludeId = data.get("id") != null ? data.get("id").toString() : null;
        
        boolean isUnique = storageAdapter.isUnique(definition, field.name(), value, excludeId);
        if (!isUnique) {
            errors.add(FieldError.unique(field.name()));
        }
    }
    
    /**
     * Validates that the referenced record exists in the target collection.
     */
    private void validateReference(FieldDefinition field, Object value, List<FieldError> errors) {
        ReferenceConfig refConfig = field.referenceConfig();
        
        if (collectionRegistry == null) {
            // Reference validation not available without registry
            return;
        }
        
        // Get the target collection definition
        CollectionDefinition targetCollection = collectionRegistry.get(refConfig.targetCollection());
        if (targetCollection == null) {
            // Target collection doesn't exist - this is a configuration error
            errors.add(new FieldError(
                field.name(),
                "Referenced collection does not exist: " + refConfig.targetCollection(),
                "reference"
            ));
            return;
        }
        
        // Check if the referenced record exists
        String refId = value.toString();
        var referencedRecord = storageAdapter.getById(targetCollection, refId);
        
        if (referencedRecord.isEmpty()) {
            errors.add(FieldError.reference(field.name(), refConfig.targetCollection()));
        }
    }
}
