# EPIC: Phase 2 - Enhanced Object Model and Validation

> **Goal:** Transform EMF from an 8-type basic field system into an enterprise-grade data modeling platform with picklists, relationships, validation rules, record types, and field history tracking.
>
> **Outcome:** Collections support 24 field types with type-specific validation, picklist management, enforced foreign-key relationships, cross-field validation rules, record types, and auditable field history.

---

## Task Execution Order

Tasks are grouped into **work streams** that can execute in parallel where possible. Within each stream, tasks are sequential. Dependencies across streams are noted explicitly.

```
Stream A: Extended Field Types       Stream B: Picklists            Stream C: Relationships
--------------------------           ----------------               --------------------
A1  Field type migration (V14)       B1  Picklist migration (V15)   C1  Relationship migration (V16)
A2  FieldType enum extension             (blocked by A1)                (blocked by A1)
A3  FieldTypeConfig on Field entity  B2  GlobalPicklist entity      C2  ReferenceConfig extension
A4  FieldTypeValidator interface     B3  PicklistValue entity       C3  Lookup field handling
A5  Type-specific validators         B4  PicklistDependency entity  C4  MasterDetail field handling
A6  StorageAdapter type mapping      B5  PicklistService            C5  FK enforcement in storage
A7  SchemaMigration type compat      B6  PicklistController         C6  Cascade delete handling
A8  FieldService validation update   B7  Picklist validate on save  C7  IncludeResolver update
A9  AutoNumber sequence service      B8  Picklist admin UI          C8  Relationship admin UI
A10 FormulaEvaluator (shared)        B9  Picklist SDK types         C9  Relationship SDK types
A11 Formula field query rewrite
A12 RollupSummary compute engine     Stream D: Validation & Record Types
A13 Encrypted field crypto service   -----------------------------------
A14 Field type SDK + UI updates      D1  Validation rules migration (V17)
                                     D2  ValidationRule entity
Stream E: Audit & History            D3  FormulaEvaluator integration (uses A10)
---------------------                D4  ValidationRuleService
E1  Field history migration (V19)    D5  Validation on create/update
E2  FieldHistory entity              D6  ValidationRule controller
E3  FieldHistoryService              D7  Record type migration (V18)
E4  Storage adapter history hook     D8  RecordType entity
E5  track_history column (V20)       D9  RecordTypePicklist entity
E6  Field history controller         D10 ProfileRecordType entity
E7  Field history UI                 D11 RecordTypeService
E8  Field history SDK types          D12 Storage record_type_id column
E9  Setup audit AOP enhancement      D13 RecordType controller
E10 Setup audit UI enhancements      D14 Record type admin UI
                                     D15 Validation rule admin UI
                                     D16 Validation + RecordType SDK types
```

**Critical path:** A1 → A2 → A6 → B1/C1 → B5/C5 → D1 → D3 → D5

---

## Stream A: Extended Field Types

### A1: Field Type Configuration Migration

**Purpose:** Add columns to the `field` table for type-specific configuration and auto-number sequence tracking.

**Flyway file:** `V14__add_field_type_config.sql`

```sql
-- Type-specific configuration stored as JSONB
ALTER TABLE field ADD COLUMN field_type_config JSONB;

-- Sequence name for AUTO_NUMBER fields
ALTER TABLE field ADD COLUMN auto_number_sequence_name VARCHAR(100);

-- Comments documenting field_type_config schemas per type
COMMENT ON COLUMN field.field_type_config IS
'Type-specific configuration. Schema varies by field type:
  PICKLIST: {"globalPicklistId": "uuid", "restricted": true, "sorted": false}
  MULTI_PICKLIST: {"globalPicklistId": "uuid", "restricted": true, "sorted": false}
  AUTO_NUMBER: {"prefix": "TICKET-", "padding": 4, "startValue": 1}
  CURRENCY: {"precision": 2, "defaultCurrencyCode": "USD"}
  FORMULA: {"expression": "Amount * Quantity", "returnType": "DOUBLE"}
  ROLLUP_SUMMARY: {"childCollection": "line_items", "aggregateFunction": "SUM", "aggregateField": "amount", "filter": {}}
  ENCRYPTED: {"algorithm": "AES-256-GCM"}
  GEOLOCATION: {"format": "DECIMAL_DEGREES"}
';
```

**Acceptance criteria:**
- Migration runs cleanly on fresh and existing databases
- Existing field rows unaffected (new columns are nullable)
- No impact on existing FieldService operations

**Integration points:** A2 (enum extension), A3 (entity update), B1 (picklist migration references field table)

---

### A2: FieldType Enum Extension

**Purpose:** Extend the runtime-core `FieldType` enum from 8 values to 24, covering all enterprise field types. Also reconcile the control-plane's string-based type system.

**File:** `com.emf.runtime.model.FieldType` (`runtime-core/.../model/FieldType.java`)

**Current enum values:** `STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME, JSON`

```java
public enum FieldType {
    // --- Existing (Phase 0) ---
    STRING,       // Maps to TEXT in PostgreSQL
    INTEGER,      // Maps to INTEGER in PostgreSQL
    LONG,         // Maps to BIGINT in PostgreSQL
    DOUBLE,       // Maps to DOUBLE PRECISION in PostgreSQL
    BOOLEAN,      // Maps to BOOLEAN in PostgreSQL
    DATE,         // Maps to DATE in PostgreSQL
    DATETIME,     // Maps to TIMESTAMP in PostgreSQL
    JSON,         // Maps to JSONB in PostgreSQL

    // --- New: Reference & Structure (Phase 2) ---
    REFERENCE,    // Maps to VARCHAR(36) -- generic FK (backward compat with existing "reference" type)
    ARRAY,        // Maps to JSONB -- ordered list (backward compat with existing "array" type)

    // --- New: Picklist Types ---
    PICKLIST,         // Maps to VARCHAR(255) -- single-value selection
    MULTI_PICKLIST,   // Maps to TEXT[] -- PostgreSQL array of selected values

    // --- New: Numeric Specializations ---
    CURRENCY,     // Maps to NUMERIC(18,2) + companion currency_code VARCHAR(3) column
    PERCENT,      // Maps to NUMERIC(8,4) -- stored as decimal (50% = 50.0000)
    AUTO_NUMBER,  // Maps to VARCHAR(100) -- application-generated via sequence

    // --- New: Text Specializations ---
    PHONE,        // Maps to VARCHAR(40) -- validated with phone regex
    EMAIL,        // Maps to VARCHAR(320) -- validated with email regex
    URL,          // Maps to VARCHAR(2048) -- validated with URL format
    RICH_TEXT,    // Maps to TEXT -- HTML content, sanitized on save

    // --- New: Security & Identity ---
    ENCRYPTED,    // Maps to BYTEA -- AES-256-GCM encrypted at application layer
    EXTERNAL_ID,  // Maps to VARCHAR(255) + UNIQUE INDEX -- for integration upsert matching

    // --- New: Spatial ---
    GEOLOCATION,  // Maps to DOUBLE PRECISION x2 (latitude + longitude columns)

    // --- New: Relationship Types ---
    LOOKUP,           // Maps to VARCHAR(36) + FK ON DELETE SET NULL
    MASTER_DETAIL,    // Maps to VARCHAR(36) + FK ON DELETE CASCADE, NOT NULL

    // --- New: Computed Types ---
    FORMULA,          // No physical column -- computed at query time
    ROLLUP_SUMMARY;   // No physical column -- computed via aggregate subquery

    /**
     * Returns true if this type has a physical column in the database.
     * FORMULA and ROLLUP_SUMMARY are computed on read.
     */
    public boolean hasPhysicalColumn() {
        return this != FORMULA && this != ROLLUP_SUMMARY;
    }

    /**
     * Returns true if this type creates additional companion columns
     * beyond the primary column (e.g., CURRENCY adds a currency_code column,
     * GEOLOCATION adds latitude + longitude columns).
     */
    public boolean hasCompanionColumns() {
        return this == CURRENCY || this == GEOLOCATION;
    }

    /**
     * Returns true if this type is a relationship to another collection.
     */
    public boolean isRelationship() {
        return this == REFERENCE || this == LOOKUP || this == MASTER_DETAIL;
    }
}
```

**Control-plane type reconciliation:** The `FieldService.VALID_FIELD_TYPES` set currently uses strings: `"string", "number", "boolean", "date", "datetime", "reference", "array", "object"`. Update to include all 24 types using the enum names in lowercase. The existing string values are mapped for backward compatibility:

| Legacy String | Maps to FieldType |
|--------------|-------------------|
| `"string"` | `STRING` |
| `"number"` | `DOUBLE` |
| `"boolean"` | `BOOLEAN` |
| `"date"` | `DATE` |
| `"datetime"` | `DATETIME` |
| `"reference"` | `REFERENCE` |
| `"array"` | `ARRAY` |
| `"object"` | `JSON` |

**File to modify:** `com.emf.controlplane.service.FieldService` -- replace `VALID_FIELD_TYPES` set with mapping logic that accepts both legacy names and new enum names.

```java
// Map of all accepted type strings -> canonical FieldType name
private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
    // Legacy aliases
    Map.entry("string", "STRING"),
    Map.entry("number", "DOUBLE"),
    Map.entry("boolean", "BOOLEAN"),
    Map.entry("date", "DATE"),
    Map.entry("datetime", "DATETIME"),
    Map.entry("reference", "REFERENCE"),
    Map.entry("array", "ARRAY"),
    Map.entry("object", "JSON"),
    // New types (accepted as lowercase)
    Map.entry("picklist", "PICKLIST"),
    Map.entry("multi_picklist", "MULTI_PICKLIST"),
    Map.entry("auto_number", "AUTO_NUMBER"),
    Map.entry("currency", "CURRENCY"),
    Map.entry("percent", "PERCENT"),
    Map.entry("phone", "PHONE"),
    Map.entry("email", "EMAIL"),
    Map.entry("url", "URL"),
    Map.entry("rich_text", "RICH_TEXT"),
    Map.entry("encrypted", "ENCRYPTED"),
    Map.entry("external_id", "EXTERNAL_ID"),
    Map.entry("geolocation", "GEOLOCATION"),
    Map.entry("lookup", "LOOKUP"),
    Map.entry("master_detail", "MASTER_DETAIL"),
    Map.entry("formula", "FORMULA"),
    Map.entry("rollup_summary", "ROLLUP_SUMMARY"),
    Map.entry("integer", "INTEGER"),
    Map.entry("long", "LONG"),
    Map.entry("double", "DOUBLE"),
    Map.entry("json", "JSON")
);

public static String resolveFieldType(String input) {
    String canonical = TYPE_ALIASES.get(input.toLowerCase());
    if (canonical == null) {
        throw new ValidationException("Invalid field type: " + input);
    }
    return canonical;
}
```

**Acceptance criteria:**
- All 24 enum values compile and are accessible
- Existing code using `STRING`, `INTEGER`, etc. continues to work unchanged
- `hasPhysicalColumn()` returns false for FORMULA and ROLLUP_SUMMARY
- Legacy type strings from existing API calls resolve correctly

**Integration points:**
- A6 (StorageAdapter mapping uses new enum values)
- A7 (SchemaMigration type compatibility extended)
- All other streams depend on these enum values

---

### A3: FieldTypeConfig on Field Entity

**Purpose:** Add the `fieldTypeConfig` and `autoNumberSequenceName` fields to the control-plane `Field` entity, corresponding to V14 migration columns.

**File:** `com.emf.controlplane.entity.Field` (`emf-control-plane/.../entity/Field.java`)

```java
// Add to Field entity class:

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "field_type_config", columnDefinition = "jsonb")
private String fieldTypeConfig;

@Column(name = "auto_number_sequence_name", length = 100)
private String autoNumberSequenceName;

// Getters and setters
public String getFieldTypeConfig() { return fieldTypeConfig; }
public void setFieldTypeConfig(String fieldTypeConfig) { this.fieldTypeConfig = fieldTypeConfig; }

public String getAutoNumberSequenceName() { return autoNumberSequenceName; }
public void setAutoNumberSequenceName(String autoNumberSequenceName) {
    this.autoNumberSequenceName = autoNumberSequenceName;
}
```

**Conventions followed:**
- JSONB field uses `@JdbcTypeCode(SqlTypes.JSON)` -- same pattern as existing `constraints` and `defaultValue` fields
- Nullable columns (no `nullable = false`) since not all field types need config

**Integration points:**
- A8 (FieldService reads/writes fieldTypeConfig during add/update)
- B5 (PicklistService reads globalPicklistId from fieldTypeConfig)
- A9 (AutoNumberService reads prefix/padding from fieldTypeConfig)

---

### A4: FieldTypeValidator Interface

**Purpose:** Define a pluggable validation contract so each field type can validate its own `fieldTypeConfig` and field values independently.

**File:** `com.emf.controlplane.validation.FieldTypeValidator` (new)

```java
package com.emf.controlplane.validation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Validates field type-specific configuration and values.
 * One implementation per FieldType that requires type-specific validation.
 */
public interface FieldTypeValidator {

    /**
     * The field type this validator handles.
     */
    String getFieldType();

    /**
     * Validates the fieldTypeConfig JSON when a field of this type is created or updated.
     * @throws ValidationException if config is invalid
     */
    void validateConfig(JsonNode fieldTypeConfig);

    /**
     * Validates a field value during record create/update.
     * @param value the value to validate
     * @param fieldTypeConfig the field's type-specific configuration
     * @throws ValidationException if value is invalid
     */
    void validateValue(Object value, JsonNode fieldTypeConfig);
}
```

**Registry pattern:**

```java
package com.emf.controlplane.validation;

@Component
public class FieldTypeValidatorRegistry {
    private final Map<String, FieldTypeValidator> validators;

    public FieldTypeValidatorRegistry(List<FieldTypeValidator> validatorList) {
        this.validators = validatorList.stream()
            .collect(Collectors.toMap(FieldTypeValidator::getFieldType, v -> v));
    }

    public Optional<FieldTypeValidator> getValidator(String fieldType) {
        return Optional.ofNullable(validators.get(fieldType));
    }
}
```

**Integration points:**
- A5 (type-specific validator implementations)
- A8 (FieldService delegates to registry)

---

### A5: Type-Specific Validator Implementations

**Purpose:** Implement `FieldTypeValidator` for each new field type that requires config or value validation.

**Files:** All in `com.emf.controlplane.validation` package (new files)

**PicklistFieldValidator:**

```java
@Component
public class PicklistFieldValidator implements FieldTypeValidator {
    @Override
    public String getFieldType() { return "PICKLIST"; }

    @Override
    public void validateConfig(JsonNode config) {
        // Optional: globalPicklistId (UUID string)
        // Optional: restricted (boolean, default true)
        // Optional: sorted (boolean, default false)
        // If no globalPicklistId, values are managed inline via PicklistService
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof String)) {
            throw new ValidationException("PICKLIST value must be a string");
        }
        // Actual value-in-list validation done by PicklistService (B7)
    }
}
```

**AutoNumberFieldValidator:**

```java
@Component
public class AutoNumberFieldValidator implements FieldTypeValidator {
    @Override
    public String getFieldType() { return "AUTO_NUMBER"; }

    @Override
    public void validateConfig(JsonNode config) {
        // Required: prefix (string, max 50 chars)
        // Required: padding (integer, 1-10)
        // Optional: startValue (integer, default 1)
        if (config == null || !config.has("prefix")) {
            throw new ValidationException("AUTO_NUMBER requires 'prefix' in fieldTypeConfig");
        }
        if (!config.has("padding") || config.get("padding").asInt() < 1) {
            throw new ValidationException("AUTO_NUMBER requires 'padding' >= 1");
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        // AUTO_NUMBER values are system-generated, not user-provided
        // If a value is passed on create, it's ignored
    }
}
```

**CurrencyFieldValidator:**

```java
@Component
public class CurrencyFieldValidator implements FieldTypeValidator {
    @Override
    public String getFieldType() { return "CURRENCY"; }

    @Override
    public void validateConfig(JsonNode config) {
        // Optional: precision (integer, 0-6, default 2)
        // Optional: defaultCurrencyCode (ISO 4217, 3 chars)
        if (config != null && config.has("precision")) {
            int precision = config.get("precision").asInt();
            if (precision < 0 || precision > 6) {
                throw new ValidationException("CURRENCY precision must be 0-6");
            }
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null && !(value instanceof Number)) {
            throw new ValidationException("CURRENCY value must be a number");
        }
    }
}
```

**FormulaFieldValidator:**

```java
@Component
public class FormulaFieldValidator implements FieldTypeValidator {
    @Override
    public String getFieldType() { return "FORMULA"; }

    @Override
    public void validateConfig(JsonNode config) {
        // Required: expression (string)
        // Required: returnType (STRING, DOUBLE, BOOLEAN, DATE, DATETIME)
        if (config == null || !config.has("expression") || !config.has("returnType")) {
            throw new ValidationException("FORMULA requires 'expression' and 'returnType' in fieldTypeConfig");
        }
        String returnType = config.get("returnType").asText();
        if (!Set.of("STRING", "DOUBLE", "BOOLEAN", "DATE", "DATETIME").contains(returnType)) {
            throw new ValidationException("FORMULA returnType must be one of: STRING, DOUBLE, BOOLEAN, DATE, DATETIME");
        }
        // Syntax validation of expression deferred to FormulaEvaluator (A10)
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        // FORMULA fields are read-only; reject any user-provided value
        if (value != null) {
            throw new ValidationException("FORMULA fields cannot be set directly");
        }
    }
}
```

**RollupSummaryFieldValidator:**

```java
@Component
public class RollupSummaryFieldValidator implements FieldTypeValidator {
    @Override
    public String getFieldType() { return "ROLLUP_SUMMARY"; }

    @Override
    public void validateConfig(JsonNode config) {
        // Required: childCollection (string)
        // Required: aggregateFunction (COUNT, SUM, MIN, MAX, AVG)
        // Required (unless COUNT): aggregateField (string)
        // Optional: filter (JSON object with field conditions)
        if (config == null) {
            throw new ValidationException("ROLLUP_SUMMARY requires fieldTypeConfig");
        }
        if (!config.has("childCollection")) {
            throw new ValidationException("ROLLUP_SUMMARY requires 'childCollection'");
        }
        String fn = config.has("aggregateFunction") ? config.get("aggregateFunction").asText() : null;
        if (!Set.of("COUNT", "SUM", "MIN", "MAX", "AVG").contains(fn)) {
            throw new ValidationException("ROLLUP_SUMMARY aggregateFunction must be COUNT, SUM, MIN, MAX, or AVG");
        }
        if (!"COUNT".equals(fn) && !config.has("aggregateField")) {
            throw new ValidationException("ROLLUP_SUMMARY requires 'aggregateField' for " + fn);
        }
    }

    @Override
    public void validateValue(Object value, JsonNode config) {
        if (value != null) {
            throw new ValidationException("ROLLUP_SUMMARY fields cannot be set directly");
        }
    }
}
```

**Additional validators** (lighter implementations following same pattern):
- `EmailFieldValidator` -- validates email regex `^[^@]+@[^@]+\.[^@]+$`
- `PhoneFieldValidator` -- validates phone regex `^[+]?[\d\s\-().]+$`
- `UrlFieldValidator` -- validates URL format (scheme + host required)
- `EncryptedFieldValidator` -- validates config has algorithm, value is string
- `GeolocationFieldValidator` -- validates value is object with `latitude` (-90..90) and `longitude` (-180..180)
- `ExternalIdFieldValidator` -- no special config; value must be string
- `PercentFieldValidator` -- value must be number

**Acceptance criteria:**
- Each validator registered via Spring component scan
- `FieldTypeValidatorRegistry` resolves correct validator for each type
- Invalid configs throw `ValidationException` with descriptive messages

---

### A6: StorageAdapter Type Mapping

**Purpose:** Extend `PhysicalTableStorageAdapter.mapFieldTypeToSql()` to handle all 24 field types, and update `initializeCollection()` to create companion columns where needed.

**File:** `com.emf.runtime.storage.PhysicalTableStorageAdapter` (`runtime-core/.../storage/PhysicalTableStorageAdapter.java`)

**Updated `mapFieldTypeToSql()`:**

```java
private String mapFieldTypeToSql(FieldType type) {
    return switch (type) {
        // Existing
        case STRING -> "TEXT";
        case INTEGER -> "INTEGER";
        case LONG -> "BIGINT";
        case DOUBLE -> "DOUBLE PRECISION";
        case BOOLEAN -> "BOOLEAN";
        case DATE -> "DATE";
        case DATETIME -> "TIMESTAMP";
        case JSON -> "JSONB";

        // Reference & Structure
        case REFERENCE -> "VARCHAR(36)";
        case ARRAY -> "JSONB";

        // Picklist
        case PICKLIST -> "VARCHAR(255)";
        case MULTI_PICKLIST -> "TEXT[]";

        // Numeric specializations
        case CURRENCY -> "NUMERIC(18,2)";
        case PERCENT -> "NUMERIC(8,4)";
        case AUTO_NUMBER -> "VARCHAR(100)";

        // Text specializations
        case PHONE -> "VARCHAR(40)";
        case EMAIL -> "VARCHAR(320)";
        case URL -> "VARCHAR(2048)";
        case RICH_TEXT -> "TEXT";

        // Security & Identity
        case ENCRYPTED -> "BYTEA";
        case EXTERNAL_ID -> "VARCHAR(255)";

        // Spatial (primary column is latitude; longitude is companion)
        case GEOLOCATION -> "DOUBLE PRECISION";

        // Relationship types
        case LOOKUP -> "VARCHAR(36)";
        case MASTER_DETAIL -> "VARCHAR(36)";

        // Computed (no column)
        case FORMULA, ROLLUP_SUMMARY -> null;
    };
}
```

**Updated `initializeCollection()`:** After creating the base columns, iterate fields and handle companion columns:

```java
// In the column-building loop:
for (FieldDefinition field : definition.fields()) {
    if (!field.type().hasPhysicalColumn()) {
        continue; // Skip FORMULA, ROLLUP_SUMMARY
    }

    String sqlType = mapFieldTypeToSql(field.type());
    columns.add(field.name() + " " + sqlType + (field.nullable() ? "" : " NOT NULL"));

    // Companion columns
    if (field.type() == FieldType.CURRENCY) {
        columns.add(field.name() + "_currency_code VARCHAR(3)");
    }
    if (field.type() == FieldType.GEOLOCATION) {
        // Primary column is latitude; add longitude
        columns.add(field.name() + "_longitude DOUBLE PRECISION");
    }

    // Unique index for EXTERNAL_ID
    if (field.type() == FieldType.EXTERNAL_ID) {
        postCreateStatements.add(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_" + tableName + "_" + field.name()
            + " ON " + tableName + "(" + field.name() + ")"
        );
    }
}
```

**Updated `convertValueForStorage()`:**

```java
private Object convertValueForStorage(Object value, FieldType type) {
    if (value == null) return null;
    return switch (type) {
        case JSON, ARRAY -> {
            if (value instanceof Map || value instanceof List) {
                yield objectMapper.writeValueAsString(value);
            }
            yield value;
        }
        case DATE, DATETIME -> {
            // Existing Instant/LocalDate conversion logic
            ...
        }
        case MULTI_PICKLIST -> {
            if (value instanceof List<?> list) {
                yield list.toArray(new String[0]); // PostgreSQL TEXT[]
            }
            yield value;
        }
        case ENCRYPTED -> {
            // Delegate to FieldEncryptionService (A13)
            yield fieldEncryptionService.encrypt(value.toString());
        }
        case GEOLOCATION -> {
            // Value is Map with latitude/longitude; store latitude in primary column
            if (value instanceof Map<?,?> geo) {
                yield ((Number) geo.get("latitude")).doubleValue();
            }
            yield value;
        }
        default -> value;
    };
}
```

**Acceptance criteria:**
- All 24 types produce correct SQL column definitions
- CURRENCY creates dual columns (amount + currency_code)
- GEOLOCATION creates dual columns (latitude + longitude)
- EXTERNAL_ID creates unique index
- FORMULA and ROLLUP_SUMMARY skip column creation
- MULTI_PICKLIST stores as PostgreSQL TEXT[]

**Integration points:**
- A2 (depends on extended FieldType enum)
- C5 (FK enforcement for LOOKUP/MASTER_DETAIL added in Stream C)
- A13 (encrypted field storage)

---

### A7: SchemaMigration Type Compatibility

**Purpose:** Extend the `SchemaMigrationEngine` type compatibility map to handle transitions involving new field types.

**File:** `com.emf.runtime.storage.SchemaMigrationEngine` (`runtime-core/.../storage/SchemaMigrationEngine.java`)

**Current `TYPE_COMPATIBILITY` map handles:** STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME, JSON

**Extended compatibility rules:**

```java
private static final Map<FieldType, Set<FieldType>> TYPE_COMPATIBILITY = Map.ofEntries(
    // Existing rules
    Map.entry(FieldType.STRING, Set.of(FieldType.values())),  // STRING accepts any type
    Map.entry(FieldType.INTEGER, Set.of(INTEGER, LONG, DOUBLE, STRING)),
    Map.entry(FieldType.LONG, Set.of(LONG, DOUBLE, STRING)),
    Map.entry(FieldType.DOUBLE, Set.of(DOUBLE, STRING)),
    Map.entry(FieldType.BOOLEAN, Set.of(BOOLEAN, STRING)),
    Map.entry(FieldType.DATE, Set.of(DATE, DATETIME, STRING)),
    Map.entry(FieldType.DATETIME, Set.of(DATETIME, STRING)),
    Map.entry(FieldType.JSON, Set.of(JSON, STRING)),

    // New type compatibility
    Map.entry(FieldType.PICKLIST, Set.of(PICKLIST, STRING)),
    Map.entry(FieldType.MULTI_PICKLIST, Set.of(MULTI_PICKLIST, JSON, ARRAY)),
    Map.entry(FieldType.CURRENCY, Set.of(CURRENCY, DOUBLE, STRING)),
    Map.entry(FieldType.PERCENT, Set.of(PERCENT, DOUBLE, STRING)),
    Map.entry(FieldType.AUTO_NUMBER, Set.of(AUTO_NUMBER, STRING)),
    Map.entry(FieldType.PHONE, Set.of(PHONE, STRING)),
    Map.entry(FieldType.EMAIL, Set.of(EMAIL, STRING)),
    Map.entry(FieldType.URL, Set.of(URL, STRING)),
    Map.entry(FieldType.RICH_TEXT, Set.of(RICH_TEXT, STRING)),
    Map.entry(FieldType.ENCRYPTED, Set.of(ENCRYPTED, STRING)),
    Map.entry(FieldType.EXTERNAL_ID, Set.of(EXTERNAL_ID, STRING)),
    Map.entry(FieldType.GEOLOCATION, Set.of(GEOLOCATION)),
    Map.entry(FieldType.REFERENCE, Set.of(REFERENCE, LOOKUP, STRING)),
    Map.entry(FieldType.LOOKUP, Set.of(LOOKUP, REFERENCE, STRING)),
    Map.entry(FieldType.MASTER_DETAIL, Set.of(MASTER_DETAIL, LOOKUP)),
    Map.entry(FieldType.ARRAY, Set.of(ARRAY, JSON, STRING)),
    Map.entry(FieldType.FORMULA, Set.of(FORMULA)),
    Map.entry(FieldType.ROLLUP_SUMMARY, Set.of(ROLLUP_SUMMARY))
);
```

**Special handling for type changes involving companion columns:**
- CURRENCY → DOUBLE: drop companion `_currency_code` column
- DOUBLE → CURRENCY: add companion `_currency_code` column
- GEOLOCATION → any: drop companion `_longitude` column
- any → GEOLOCATION: add companion `_longitude` column

**Acceptance criteria:**
- `isTypeChangeCompatible()` works correctly for all 24×24 type pairs
- Companion column additions/removals are handled during schema migration
- Existing compatibility for 8 original types unchanged

---

### A8: FieldService Validation Update

**Purpose:** Update `FieldService.addField()` and `updateField()` to validate `fieldTypeConfig` using the `FieldTypeValidatorRegistry` and store the resolved canonical type.

**File:** `com.emf.controlplane.service.FieldService` (`emf-control-plane/.../service/FieldService.java`)

**Changes to `addField()`:**

```java
@Transactional
@CacheEvict(value = COLLECTIONS_CACHE, allEntries = true)
public Field addField(String collectionId, AddFieldRequest request) {
    Collection collection = getCollectionOrThrow(collectionId);

    // 1. Resolve type (supports legacy aliases)
    String canonicalType = resolveFieldType(request.type());

    // 2. Validate fieldTypeConfig via type-specific validator
    if (request.fieldTypeConfig() != null) {
        JsonNode configNode = objectMapper.readTree(request.fieldTypeConfig());
        fieldTypeValidatorRegistry.getValidator(canonicalType)
            .ifPresent(v -> v.validateConfig(configNode));
    } else {
        // Some types require config
        fieldTypeValidatorRegistry.getValidator(canonicalType)
            .ifPresent(v -> v.validateConfig(null));
    }

    // 3. Validate constraints (existing logic)
    validateConstraints(canonicalType, request.constraints());

    // 4. Create field entity
    Field field = new Field(request.name(), canonicalType);
    field.setCollection(collection);
    field.setFieldTypeConfig(request.fieldTypeConfig());
    // ... existing field setup ...

    // 5. For AUTO_NUMBER: create sequence
    if ("AUTO_NUMBER".equals(canonicalType)) {
        String seqName = "seq_" + collection.getName() + "_" + request.name();
        field.setAutoNumberSequenceName(seqName);
    }

    // 6. Save and version
    field = fieldRepository.save(field);
    incrementVersion(collection);
    publishEvent(collection, ChangeType.UPDATED);
    return field;
}
```

**Updated `AddFieldRequest` DTO:**

```java
public record AddFieldRequest(
    @NotBlank String name,
    @NotBlank String type,
    String displayName,
    boolean required,
    boolean unique,
    boolean indexed,
    String defaultValue,       // JSONB string
    String referenceTarget,
    Integer order,
    String constraints,        // JSONB string
    String description,
    String fieldTypeConfig     // JSONB string -- NEW
) {}
```

**Acceptance criteria:**
- Fields with `fieldTypeConfig` validated before save
- AUTO_NUMBER fields get sequence name generated
- Legacy type strings still work (backward compatible)
- Invalid `fieldTypeConfig` for a type results in 400 with clear error message

**Integration points:**
- A4 (FieldTypeValidatorRegistry dependency)
- A3 (Field entity has fieldTypeConfig column)

---

### A9: AutoNumber Sequence Service

**Purpose:** Manage PostgreSQL sequences for AUTO_NUMBER fields and generate formatted values on record creation.

**File:** `com.emf.runtime.service.AutoNumberService` (new, in runtime-core)

```java
package com.emf.runtime.service;

@Service
public class AutoNumberService {
    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a PostgreSQL sequence for an auto-number field.
     */
    public void createSequence(String sequenceName, long startValue) {
        jdbcTemplate.execute(
            "CREATE SEQUENCE IF NOT EXISTS " + sanitize(sequenceName)
            + " START WITH " + startValue
            + " INCREMENT BY 1"
        );
    }

    /**
     * Drops a sequence (when field is deleted).
     */
    public void dropSequence(String sequenceName) {
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS " + sanitize(sequenceName));
    }

    /**
     * Generates the next formatted auto-number value.
     * @param sequenceName the PostgreSQL sequence name
     * @param prefix e.g., "TICKET-"
     * @param padding number of zero-padded digits, e.g., 4 -> "0001"
     * @return formatted value, e.g., "TICKET-0001"
     */
    public String generateNext(String sequenceName, String prefix, int padding) {
        Long nextVal = jdbcTemplate.queryForObject(
            "SELECT nextval('" + sanitize(sequenceName) + "')", Long.class
        );
        String padded = String.format("%0" + padding + "d", nextVal);
        return prefix + padded;
    }

    private String sanitize(String name) {
        // Prevent SQL injection: only allow alphanumeric + underscore
        if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid sequence name: " + name);
        }
        return name;
    }
}
```

**Integration with StorageAdapter:** In `PhysicalTableStorageAdapter.create()`, before inserting a record:

```java
for (FieldDefinition field : definition.fields()) {
    if (field.type() == FieldType.AUTO_NUMBER) {
        // Read config from field's referenceConfig or fieldTypeConfig
        String seqName = field.autoNumberSequenceName();
        String prefix = field.autoNumberPrefix();
        int padding = field.autoNumberPadding();
        String value = autoNumberService.generateNext(seqName, prefix, padding);
        data.put(field.name(), value);
    }
}
```

**Acceptance criteria:**
- Creating an AUTO_NUMBER field creates a PostgreSQL sequence
- Deleting an AUTO_NUMBER field drops its sequence
- Record creation auto-generates formatted values (e.g., "TICKET-0001")
- Concurrent inserts generate unique, sequential values (PostgreSQL sequence guarantees)

---

### A10: FormulaEvaluator (Shared Engine)

**Purpose:** A shared expression evaluator used by formula fields (A11), validation rules (D3), rollup summaries (A12), and workflow criteria (Phase 4). Parses expression strings into an AST and evaluates them against a record context.

**Files:** All in `com.emf.runtime.formula` package (new, in runtime-core)

**FormulaEvaluator (main entry point):**

```java
package com.emf.runtime.formula;

@Service
public class FormulaEvaluator {
    private final FormulaParser parser;
    private final Map<String, FormulaFunction> functions;

    public FormulaEvaluator(List<FormulaFunction> functionList) {
        this.parser = new FormulaParser();
        this.functions = functionList.stream()
            .collect(Collectors.toMap(FormulaFunction::name, f -> f));
    }

    /**
     * Evaluates a formula expression against a record context.
     * @param expression the formula string, e.g., "Amount * Quantity"
     * @param context field values as a Map
     * @return the evaluation result (String, Number, Boolean, or null)
     */
    public Object evaluate(String expression, Map<String, Object> context) {
        FormulaAst ast = parser.parse(expression);
        return ast.evaluate(new FormulaContext(context, functions));
    }

    /**
     * Evaluates and returns a Boolean result. Used by validation rules.
     */
    public boolean evaluateBoolean(String expression, Map<String, Object> context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean b) return b;
        throw new FormulaException("Expression did not evaluate to Boolean: " + expression);
    }

    /**
     * Validates that an expression is syntactically correct.
     * @throws FormulaException if expression has syntax errors
     */
    public void validate(String expression) {
        parser.parse(expression); // throws on syntax error
    }
}
```

**FormulaParser (expression → AST):**

```java
public class FormulaParser {
    /**
     * Parses a formula string into an AST.
     * Supports: field references, literals (string, number, boolean),
     * operators (+, -, *, /, >, <, >=, <=, =, !=, &&, ||),
     * function calls (e.g., TODAY(), LEN(Name)), parentheses.
     */
    public FormulaAst parse(String expression) { ... }
}
```

**FormulaAst (abstract syntax tree):**

```java
public sealed interface FormulaAst {
    Object evaluate(FormulaContext context);

    record Literal(Object value) implements FormulaAst { ... }
    record FieldRef(String fieldName) implements FormulaAst { ... }
    record BinaryOp(String operator, FormulaAst left, FormulaAst right) implements FormulaAst { ... }
    record UnaryOp(String operator, FormulaAst operand) implements FormulaAst { ... }
    record FunctionCall(String functionName, List<FormulaAst> arguments) implements FormulaAst { ... }
}
```

**FormulaContext:**

```java
public record FormulaContext(
    Map<String, Object> fieldValues,
    Map<String, FormulaFunction> functions
) {
    public Object getFieldValue(String name) {
        return fieldValues.get(name);
    }
}
```

**Built-in functions** (each implements `FormulaFunction` interface):

| Function | Category | Description |
|----------|----------|-------------|
| `TODAY()` | Date | Current date |
| `NOW()` | Date | Current datetime |
| `YEAR(date)` | Date | Extract year |
| `MONTH(date)` | Date | Extract month |
| `DAY(date)` | Date | Extract day |
| `DATEDIFF(date1, date2)` | Date | Days between dates |
| `ISBLANK(value)` | Null | True if null or empty |
| `BLANKVALUE(value, default)` | Null | Return default if blank |
| `NULLVALUE(value, default)` | Null | Return default if null |
| `IF(condition, trueVal, falseVal)` | Logic | Conditional |
| `AND(...)` | Logic | All true |
| `OR(...)` | Logic | Any true |
| `NOT(value)` | Logic | Negate |
| `LEN(text)` | Text | String length |
| `CONTAINS(text, search)` | Text | Substring check |
| `BEGINS(text, prefix)` | Text | Starts-with check |
| `ENDS(text, suffix)` | Text | Ends-with check |
| `REGEX(text, pattern)` | Text | Regex match |
| `UPPER(text)` | Text | Uppercase |
| `LOWER(text)` | Text | Lowercase |
| `TRIM(text)` | Text | Strip whitespace |
| `TEXT(value)` | Conversion | To string |
| `VALUE(text)` | Conversion | To number |
| `ROUND(number, places)` | Math | Round to decimal places |
| `CEILING(number)` | Math | Round up |
| `FLOOR(number)` | Math | Round down |
| `ABS(number)` | Math | Absolute value |
| `MAX(a, b)` | Math | Maximum |
| `MIN(a, b)` | Math | Minimum |

**Acceptance criteria:**
- Simple expressions evaluate correctly: `Amount * Quantity`, `Amount > 0`
- Comparison expressions: `CloseDate < TODAY()`
- Logical expressions: `AND(Amount > 0, Stage = 'Closed Won')`
- Nested function calls: `IF(ISBLANK(Email), 'No Email', Email)`
- Syntax errors throw `FormulaException` with line/column info
- All built-in functions are registered and functional

**Integration points:**
- A11 (formula field query rewrite)
- D3 (validation rule evaluation)
- A12 (rollup summary uses formula for filters)
- Phase 4 (workflow criteria)

---

### A11: Formula Field Query Rewrite

**Purpose:** When querying records that include FORMULA fields, compute the formula value on the fly either via SQL expression or application-layer evaluation.

**File to modify:** `com.emf.runtime.storage.PhysicalTableStorageAdapter` -- `query()` and `getById()` methods

**Strategy:** For simple formulas (arithmetic, field references), translate to SQL computed columns in the SELECT clause. For complex formulas (function calls), compute in the application layer after query.

```java
// In query() method, when building SELECT clause:
for (FieldDefinition field : definition.fields()) {
    if (field.type() == FieldType.FORMULA) {
        String sqlExpr = formulaToSqlTranslator.translate(field.formulaExpression());
        if (sqlExpr != null) {
            selectColumns.add("(" + sqlExpr + ") AS " + field.name());
        }
        // If translation fails (complex formula), field computed post-query
    } else if (field.type() == FieldType.ROLLUP_SUMMARY) {
        // Skip from SELECT; computed separately via A12
    } else if (field.type().hasPhysicalColumn()) {
        selectColumns.add(field.name());
    }
}

// Post-query: compute remaining formula/rollup fields in application layer
for (Map<String, Object> row : results) {
    for (FieldDefinition field : definition.fields()) {
        if (field.type() == FieldType.FORMULA && !sqlTranslatable(field)) {
            Object value = formulaEvaluator.evaluate(field.formulaExpression(), row);
            row.put(field.name(), value);
        }
    }
}
```

**FormulaToSqlTranslator** (new class):

```java
package com.emf.runtime.formula;

@Component
public class FormulaToSqlTranslator {
    /**
     * Attempts to translate a formula expression to a SQL expression.
     * Returns null if the formula uses functions not translatable to SQL.
     *
     * Translatable: field references, +, -, *, /, comparisons, COALESCE
     * Not translatable: TODAY(), LEN(), CONTAINS(), etc.
     */
    public String translate(String formulaExpression) { ... }
}
```

**Acceptance criteria:**
- Simple arithmetic formulas computed in SQL (e.g., `Amount * Quantity`)
- Complex formulas with function calls computed post-query
- Formula fields appear in query results but are not stored in database
- Formula fields excluded from INSERT/UPDATE operations

---

### A12: RollupSummary Compute Engine

**Purpose:** Compute ROLLUP_SUMMARY field values by executing aggregate queries against child collection tables.

**File:** `com.emf.runtime.service.RollupSummaryService` (new, in runtime-core)

```java
package com.emf.runtime.service;

@Service
public class RollupSummaryService {
    private final JdbcTemplate jdbcTemplate;

    /**
     * Computes rollup summary values for a parent record.
     * @param parentTableName the parent collection's table name
     * @param parentRecordId the parent record's ID
     * @param childTableName the child collection's table name
     * @param foreignKeyField the FK field on the child table pointing to parent
     * @param aggregateFunction COUNT, SUM, MIN, MAX, or AVG
     * @param aggregateField the field to aggregate (null for COUNT)
     * @param filter optional filter criteria for child records
     * @return the computed value
     */
    public Object compute(String parentTableName, String parentRecordId,
                          String childTableName, String foreignKeyField,
                          String aggregateFunction, String aggregateField,
                          Map<String, Object> filter) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(switch (aggregateFunction) {
            case "COUNT" -> "COUNT(*)";
            case "SUM" -> "SUM(" + sanitize(aggregateField) + ")";
            case "MIN" -> "MIN(" + sanitize(aggregateField) + ")";
            case "MAX" -> "MAX(" + sanitize(aggregateField) + ")";
            case "AVG" -> "AVG(" + sanitize(aggregateField) + ")";
            default -> throw new IllegalArgumentException("Unknown function: " + aggregateFunction);
        });
        sql.append(" FROM ").append(sanitize(childTableName));
        sql.append(" WHERE ").append(sanitize(foreignKeyField)).append(" = ?");

        List<Object> params = new ArrayList<>();
        params.add(parentRecordId);

        // Apply filter if present
        if (filter != null && !filter.isEmpty()) {
            for (Map.Entry<String, Object> entry : filter.entrySet()) {
                sql.append(" AND ").append(sanitize(entry.getKey())).append(" = ?");
                params.add(entry.getValue());
            }
        }

        return jdbcTemplate.queryForObject(sql.toString(), Object.class, params.toArray());
    }
}
```

**Integration with query flow:** In `PhysicalTableStorageAdapter.getById()` and `query()`, after fetching results:

```java
for (FieldDefinition field : definition.fields()) {
    if (field.type() == FieldType.ROLLUP_SUMMARY) {
        for (Map<String, Object> row : results) {
            Object value = rollupSummaryService.compute(
                getTableName(definition),
                row.get("id").toString(),
                field.rollupChildTable(),
                field.rollupForeignKey(),
                field.rollupFunction(),
                field.rollupField(),
                field.rollupFilter()
            );
            row.put(field.name(), value);
        }
    }
}
```

**Performance note:** For list queries with rollup summaries, batch the aggregate queries or use a subquery approach to avoid N+1. For initial implementation, compute per-row; optimize with batch SQL in a follow-up.

**Acceptance criteria:**
- COUNT, SUM, MIN, MAX, AVG aggregations compute correctly
- Filter criteria applied to child records
- Rollup values appear in query results but are not stored
- Rollup fields excluded from INSERT/UPDATE operations

---

### A13: Encrypted Field Crypto Service

**Purpose:** Provide application-layer encryption for ENCRYPTED field type using AES-256-GCM with per-tenant key derivation.

**File:** `com.emf.runtime.service.FieldEncryptionService` (new, in runtime-core)

```java
package com.emf.runtime.service;

@Service
public class FieldEncryptionService {
    private final SecretKey masterKey;

    public FieldEncryptionService(@Value("${emf.encryption.master-key}") String masterKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Derives a per-tenant encryption key using HKDF.
     */
    private SecretKey deriveTenantKey(String tenantId) {
        // HKDF-SHA256 derivation: masterKey + tenantId as info → 256-bit key
        ...
    }

    /**
     * Encrypts a plaintext value for storage.
     * @return Base64-encoded ciphertext (IV prepended)
     */
    public byte[] encrypt(String plaintext, String tenantId) {
        SecretKey tenantKey = deriveTenantKey(tenantId);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12]; // 96-bit IV for GCM
        SecureRandom.getInstanceStrong().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, tenantKey, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        // Prepend IV to ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return result;
    }

    /**
     * Decrypts a stored value.
     */
    public String decrypt(byte[] encryptedData, String tenantId) {
        SecretKey tenantKey = deriveTenantKey(tenantId);
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, 12);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, 12, encryptedData.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, tenantKey, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
```

**Configuration:** `emf.encryption.master-key` set via environment variable (never in config files). Application fails to start if not set when ENCRYPTED fields exist.

**Acceptance criteria:**
- Encrypt/decrypt round-trip produces original value
- Different tenants produce different ciphertexts for same plaintext
- Encrypted values stored as BYTEA in PostgreSQL
- Master key loaded from environment variable

---

### A14: Field Type SDK and UI Updates

**Purpose:** Update the TypeScript SDK types and the field management UI to support all 24 field types.

**SDK type updates** (`emf-web/packages/sdk/src/admin/types.ts`):

```typescript
export type FieldType =
    // Existing
    | 'STRING' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'JSON'
    // Reference & Structure
    | 'REFERENCE' | 'ARRAY'
    // Picklist
    | 'PICKLIST' | 'MULTI_PICKLIST'
    // Numeric specializations
    | 'CURRENCY' | 'PERCENT' | 'AUTO_NUMBER'
    // Text specializations
    | 'PHONE' | 'EMAIL' | 'URL' | 'RICH_TEXT'
    // Security & Identity
    | 'ENCRYPTED' | 'EXTERNAL_ID'
    // Spatial
    | 'GEOLOCATION'
    // Relationship
    | 'LOOKUP' | 'MASTER_DETAIL'
    // Computed
    | 'FORMULA' | 'ROLLUP_SUMMARY';

export interface FieldTypeConfig {
    // PICKLIST / MULTI_PICKLIST
    globalPicklistId?: string;
    restricted?: boolean;
    sorted?: boolean;

    // AUTO_NUMBER
    prefix?: string;
    padding?: number;
    startValue?: number;

    // CURRENCY
    precision?: number;
    defaultCurrencyCode?: string;

    // FORMULA
    expression?: string;
    returnType?: 'STRING' | 'DOUBLE' | 'BOOLEAN' | 'DATE' | 'DATETIME';

    // ROLLUP_SUMMARY
    childCollection?: string;
    aggregateFunction?: 'COUNT' | 'SUM' | 'MIN' | 'MAX' | 'AVG';
    aggregateField?: string;
    filter?: Record<string, unknown>;

    // ENCRYPTED
    algorithm?: string;

    // GEOLOCATION
    format?: string;
}

export interface AddFieldRequest {
    name: string;
    type: FieldType;
    displayName?: string;
    required?: boolean;
    unique?: boolean;
    indexed?: boolean;
    defaultValue?: unknown;
    referenceTarget?: string;
    order?: number;
    constraints?: Record<string, unknown>;
    description?: string;
    fieldTypeConfig?: FieldTypeConfig;  // NEW
}
```

**UI updates** (`emf-ui/app/src/pages/` -- existing field management components):

- Field type selector dropdown: group types by category (Basic, Text, Numeric, Date, Picklist, Relationship, Advanced)
- Conditional config panels: show type-specific configuration form when a type is selected
  - PICKLIST: value editor with drag-to-reorder, color picker, default selection
  - AUTO_NUMBER: prefix input, padding selector, start value
  - CURRENCY: precision selector, default currency code dropdown
  - FORMULA: expression editor with syntax highlighting, field picker, return type selector
  - ROLLUP_SUMMARY: child collection selector, function selector, field selector, filter builder
  - LOOKUP/MASTER_DETAIL: target collection selector, cascade behavior toggle
- Field list display: show type icon/badge per type

**Acceptance criteria:**
- All 24 field types selectable in UI
- Type-specific config forms render correctly
- Creating a field via SDK with `fieldTypeConfig` works end-to-end
- Legacy type names still accepted by API

---

## Stream B: Picklist Management

### B1: Picklist Database Migration

**Blocked by:** A1 (field_type_config column must exist)

**Purpose:** Create tables for global picklist value sets, per-field picklist values, and dependent picklist mappings.

**Flyway file:** `V15__add_picklist_tables.sql`

```sql
-- Global picklist value set (reusable across multiple fields)
CREATE TABLE global_picklist (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    sorted        BOOLEAN      DEFAULT false,
    restricted    BOOLEAN      DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_global_picklist UNIQUE (tenant_id, name)
);

-- Picklist values (shared between field-specific and global picklists)
CREATE TABLE picklist_value (
    id                  VARCHAR(36)  PRIMARY KEY,
    picklist_source_type VARCHAR(20) NOT NULL,   -- 'FIELD' or 'GLOBAL'
    picklist_source_id  VARCHAR(36)  NOT NULL,   -- field.id or global_picklist.id
    value               VARCHAR(255) NOT NULL,
    label               VARCHAR(255) NOT NULL,
    is_default          BOOLEAN      DEFAULT false,
    is_active           BOOLEAN      DEFAULT true,
    sort_order          INTEGER      DEFAULT 0,
    color               VARCHAR(20),
    description         VARCHAR(500),
    CONSTRAINT uq_picklist_value UNIQUE (picklist_source_type, picklist_source_id, value),
    CONSTRAINT chk_source_type CHECK (picklist_source_type IN ('FIELD', 'GLOBAL'))
);
CREATE INDEX idx_picklist_source ON picklist_value(picklist_source_type, picklist_source_id);

-- Dependent picklist mapping (controlling field → dependent field)
CREATE TABLE picklist_dependency (
    id                    VARCHAR(36)  PRIMARY KEY,
    controlling_field_id  VARCHAR(36)  NOT NULL REFERENCES field(id),
    dependent_field_id    VARCHAR(36)  NOT NULL REFERENCES field(id),
    mapping               JSONB        NOT NULL,
    CONSTRAINT uq_picklist_dep UNIQUE (controlling_field_id, dependent_field_id)
);
-- mapping format: {"controlling_value_1": ["dep_value_a", "dep_value_b"], "controlling_value_2": ["dep_value_c"]}
```

**Acceptance criteria:**
- Migration runs cleanly on fresh and existing databases
- Global picklist names unique per tenant
- Picklist values unique per source (field or global picklist)
- Dependent picklist mapping enforces field FK constraints

**Integration points:** B2-B4 (entities), B5 (service)

---

### B2: GlobalPicklist Entity

**Purpose:** JPA entity for reusable picklist value sets that can be shared across multiple fields.

**File:** `com.emf.controlplane.entity.GlobalPicklist` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "global_picklist")
public class GlobalPicklist extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sorted")
    private boolean sorted = false;

    @Column(name = "restricted")
    private boolean restricted = true;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "picklist_source_id",
                referencedColumnName = "id",
                insertable = false, updatable = false)
    private List<PicklistValue> values = new ArrayList<>();

    // Constructors: GlobalPicklist(), GlobalPicklist(String tenantId, String name)
    // Getters/setters for all fields
}
```

**Conventions followed:**
- Extends `BaseEntity` (gets id, createdAt, updatedAt, equals/hashCode)
- `@EntityListeners(AuditingEntityListener.class)` inherited from BaseEntity
- UUID generated in `BaseEntity` protected constructor

**Integration points:**
- B5 (PicklistService manages CRUD)
- A5 (PicklistFieldValidator reads globalPicklistId from fieldTypeConfig)

---

### B3: PicklistValue Entity

**Purpose:** JPA entity for individual picklist values. Polymorphic source: values can belong to a specific field or to a global picklist.

**File:** `com.emf.controlplane.entity.PicklistValue` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "picklist_value")
public class PicklistValue extends BaseEntity {

    @Column(name = "picklist_source_type", nullable = false, length = 20)
    private String picklistSourceType;  // "FIELD" or "GLOBAL"

    @Column(name = "picklist_source_id", nullable = false, length = 36)
    private String picklistSourceId;    // field.id or global_picklist.id

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "description", length = 500)
    private String description;

    // Constructors, getters/setters
}
```

**Integration points:**
- B5 (PicklistService manages values)
- B7 (validation checks value against active values)

---

### B4: PicklistDependency Entity

**Purpose:** JPA entity for dependent picklist mappings between a controlling field and a dependent field.

**File:** `com.emf.controlplane.entity.PicklistDependency` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "picklist_dependency")
public class PicklistDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "controlling_field_id", nullable = false)
    private Field controllingField;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dependent_field_id", nullable = false)
    private Field dependentField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping", nullable = false, columnDefinition = "jsonb")
    private String mapping;  // JSON: {"controlling_value": ["dep_value_1", "dep_value_2"]}

    // Constructors, getters/setters
}
```

**Integration points:**
- B5 (PicklistService manages dependencies)
- B7 (validation checks dependent value against controlling field's value)

---

### B5: PicklistService

**Purpose:** CRUD operations for picklist values, global picklists, and dependent picklist mappings.

**Files:**
- `com.emf.controlplane.repository.GlobalPicklistRepository`
- `com.emf.controlplane.repository.PicklistValueRepository`
- `com.emf.controlplane.repository.PicklistDependencyRepository`
- `com.emf.controlplane.service.PicklistService`

**Repositories:**

```java
public interface GlobalPicklistRepository extends JpaRepository<GlobalPicklist, String> {
    List<GlobalPicklist> findByTenantIdOrderByNameAsc(String tenantId);
    Optional<GlobalPicklist> findByTenantIdAndName(String tenantId, String name);
    boolean existsByTenantIdAndName(String tenantId, String name);
}

public interface PicklistValueRepository extends JpaRepository<PicklistValue, String> {
    List<PicklistValue> findByPicklistSourceTypeAndPicklistSourceIdAndActiveTrueOrderBySortOrderAsc(
        String sourceType, String sourceId);
    List<PicklistValue> findByPicklistSourceTypeAndPicklistSourceId(
        String sourceType, String sourceId);
    boolean existsByPicklistSourceTypeAndPicklistSourceIdAndValue(
        String sourceType, String sourceId, String value);
    void deleteByPicklistSourceTypeAndPicklistSourceId(String sourceType, String sourceId);
}

public interface PicklistDependencyRepository extends JpaRepository<PicklistDependency, String> {
    Optional<PicklistDependency> findByControllingFieldAndDependentField(
        Field controllingField, Field dependentField);
    List<PicklistDependency> findByControllingField(Field controllingField);
    List<PicklistDependency> findByDependentField(Field dependentField);
}
```

**Service methods:**

```java
@Service
public class PicklistService {
    // Dependencies: GlobalPicklistRepository, PicklistValueRepository,
    //               PicklistDependencyRepository, FieldRepository,
    //               ObjectMapper, ConfigEventPublisher

    // --- Global Picklist Management ---

    @Transactional(readOnly = true)
    public List<GlobalPicklist> listGlobalPicklists(String tenantId)

    @Transactional
    public GlobalPicklist createGlobalPicklist(String tenantId, CreateGlobalPicklistRequest request)
    // Validates name uniqueness per tenant

    @Transactional
    public GlobalPicklist updateGlobalPicklist(String id, UpdateGlobalPicklistRequest request)

    @Transactional
    public void deleteGlobalPicklist(String id)
    // Validates no fields reference this global picklist

    // --- Picklist Value Management ---

    @Transactional(readOnly = true)
    public List<PicklistValue> getPicklistValues(String fieldId)
    // Resolves source: if field has globalPicklistId in fieldTypeConfig, use GLOBAL source;
    // otherwise use FIELD source

    @Transactional
    public List<PicklistValue> setPicklistValues(String fieldId, List<PicklistValueRequest> values)
    // Replace all values for a field's picklist
    // Validates: no in-use values removed if restricted=true
    // Re-orders by sortOrder

    @Transactional(readOnly = true)
    public List<PicklistValue> getGlobalPicklistValues(String globalPicklistId)

    @Transactional
    public List<PicklistValue> setGlobalPicklistValues(String globalPicklistId,
                                                        List<PicklistValueRequest> values)

    // --- Dependency Management ---

    @Transactional
    public PicklistDependency setDependency(String controllingFieldId,
                                            String dependentFieldId,
                                            Map<String, List<String>> mapping)
    // Validates: both fields are PICKLIST type
    // Validates: controlling values exist in controlling field's picklist
    // Validates: dependent values exist in dependent field's picklist

    @Transactional
    public void removeDependency(String controllingFieldId, String dependentFieldId)

    @Transactional(readOnly = true)
    public List<PicklistDependency> getDependencies(String fieldId)

    // --- Value Validation ---

    public boolean isValidPicklistValue(String fieldId, String value)
    // Checks value exists in field's picklist (respects active flag)

    public boolean isValidDependentValue(String controllingFieldId, String controllingValue,
                                         String dependentFieldId, String dependentValue)
    // Checks dependent value is valid given the controlling field's current value
}
```

**DTO definitions:**

```java
public record CreateGlobalPicklistRequest(
    @NotBlank String name,
    String description,
    boolean sorted,
    boolean restricted,
    List<PicklistValueRequest> values
) {}

public record PicklistValueRequest(
    @NotBlank String value,
    @NotBlank String label,
    boolean isDefault,
    boolean active,
    Integer sortOrder,
    String color,
    String description
) {}
```

**Error handling:** `DuplicateResourceException` for name conflicts, `ResourceNotFoundException` for missing picklist/field, `ValidationException` for invalid dependency mappings.

**Integration points:**
- B7 (validate on record save)
- B6 (controller exposes these methods)
- A8 (FieldService calls PicklistService when creating PICKLIST fields)

---

### B6: PicklistController

**Purpose:** REST endpoints for managing global picklists, field picklist values, and dependencies.

**File:** `com.emf.controlplane.controller.PicklistController` (new)

**Base path:** `/control/picklists`

```java
@RestController
@RequestMapping("/control/picklists")
@SecurityRequirement(name = "bearer-jwt")
public class PicklistController {

    // --- Global Picklists ---

    @GetMapping("/global")
    public List<GlobalPicklistDto> listGlobalPicklists()

    @PostMapping("/global")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<GlobalPicklistDto> createGlobalPicklist(
        @Valid @RequestBody CreateGlobalPicklistRequest request)

    @GetMapping("/global/{id}")
    public GlobalPicklistDto getGlobalPicklist(@PathVariable String id)

    @PutMapping("/global/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public GlobalPicklistDto updateGlobalPicklist(
        @PathVariable String id, @Valid @RequestBody UpdateGlobalPicklistRequest request)

    @DeleteMapping("/global/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> deleteGlobalPicklist(@PathVariable String id)

    @GetMapping("/global/{id}/values")
    public List<PicklistValueDto> getGlobalPicklistValues(@PathVariable String id)

    @PutMapping("/global/{id}/values")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public List<PicklistValueDto> setGlobalPicklistValues(
        @PathVariable String id, @Valid @RequestBody List<PicklistValueRequest> values)

    // --- Field Picklist Values ---

    @GetMapping("/fields/{fieldId}/values")
    public List<PicklistValueDto> getFieldPicklistValues(@PathVariable String fieldId)

    @PutMapping("/fields/{fieldId}/values")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public List<PicklistValueDto> setFieldPicklistValues(
        @PathVariable String fieldId, @Valid @RequestBody List<PicklistValueRequest> values)

    // --- Dependencies ---

    @GetMapping("/fields/{fieldId}/dependencies")
    public List<PicklistDependencyDto> getDependencies(@PathVariable String fieldId)

    @PutMapping("/dependencies")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public PicklistDependencyDto setDependency(@Valid @RequestBody SetDependencyRequest request)

    @DeleteMapping("/dependencies/{controllingFieldId}/{dependentFieldId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> removeDependency(
        @PathVariable String controllingFieldId, @PathVariable String dependentFieldId)
}
```

**Integration points:**
- B8 (UI calls these endpoints)
- E9 (setup audit captures picklist changes)

---

### B7: Picklist Validation on Record Save

**Purpose:** When creating or updating a record with PICKLIST or MULTI_PICKLIST fields, validate the value(s) exist in the field's picklist. For dependent picklists, validate against the controlling field's value.

**File to modify:** `com.emf.runtime.validation.DefaultValidationEngine` (`runtime-core/.../validation/DefaultValidationEngine.java`)

**Addition to validation flow:** After existing type validation (step 3 in the current flow), add picklist validation:

```java
// In the per-field validation loop, after isValidType() check:
if (field.type() == FieldType.PICKLIST) {
    String value = (String) fieldValue;
    if (value != null && !picklistValidator.isValid(field, value)) {
        errors.add(FieldError.of(field.name(),
            "Value '" + value + "' is not a valid picklist option"));
    }
}

if (field.type() == FieldType.MULTI_PICKLIST) {
    if (fieldValue instanceof List<?> values) {
        for (Object v : values) {
            if (v != null && !picklistValidator.isValid(field, v.toString())) {
                errors.add(FieldError.of(field.name(),
                    "Value '" + v + "' is not a valid picklist option"));
            }
        }
    }
}
```

**PicklistValidator** (new component injected into DefaultValidationEngine):

```java
@Component
public class PicklistValidator {
    private final PicklistValueRepository picklistValueRepository;

    /**
     * Checks if a value is valid for the given picklist field.
     * Caches picklist values per field for the duration of a validation batch.
     */
    public boolean isValid(FieldDefinition field, String value) {
        List<String> validValues = getActiveValues(field);
        return validValues.contains(value);
    }

    /**
     * Validates dependent picklist value given the controlling field's value.
     */
    public boolean isValidDependent(FieldDefinition dependentField,
                                     String dependentValue,
                                     String controllingValue) {
        // Load dependency mapping and check
        ...
    }
}
```

**Acceptance criteria:**
- Record save with invalid picklist value returns 400 with field-specific error
- MULTI_PICKLIST validates each selected value individually
- Dependent picklist validates against controlling field's current value
- Inactive picklist values are rejected

---

### B8: Picklist Administration UI

**Purpose:** Frontend pages for managing global picklists and field picklist values.

**Location:** `emf-ui/app/src/pages/PicklistManagement.tsx` (new)

**Features:**
- Global picklist list with value count badges
- Create/edit global picklist dialog (name, description, sorted, restricted)
- Value editor: drag-to-reorder, inline edit value/label, color picker, default toggle, active toggle
- Field picklist editor: accessible from field detail page
- Dependency editor: select controlling field, map controlling values to dependent values with matrix UI
- Preview: show how picklist renders in record form

**Navigation:** Accessible from "Setup > Picklists" in admin menu and from field detail page for PICKLIST/MULTI_PICKLIST fields.

---

### B9: Picklist SDK Types

**Purpose:** TypeScript types for picklist management in the frontend SDK.

**File:** `emf-web/packages/sdk/src/admin/types.ts` (additions)

```typescript
export interface GlobalPicklist {
    id: string;
    name: string;
    description?: string;
    sorted: boolean;
    restricted: boolean;
    values: PicklistValue[];
    createdAt: string;
    updatedAt: string;
}

export interface PicklistValue {
    id: string;
    value: string;
    label: string;
    isDefault: boolean;
    active: boolean;
    sortOrder: number;
    color?: string;
    description?: string;
}

export interface PicklistDependency {
    id: string;
    controllingFieldId: string;
    dependentFieldId: string;
    mapping: Record<string, string[]>;
}

export interface CreateGlobalPicklistRequest {
    name: string;
    description?: string;
    sorted?: boolean;
    restricted?: boolean;
    values?: PicklistValueRequest[];
}

export interface PicklistValueRequest {
    value: string;
    label: string;
    isDefault?: boolean;
    active?: boolean;
    sortOrder?: number;
    color?: string;
    description?: string;
}

export interface SetDependencyRequest {
    controllingFieldId: string;
    dependentFieldId: string;
    mapping: Record<string, string[]>;
}
```

**SDK client additions** (`emf-web/packages/sdk/src/admin/AdminClient.ts`):

```typescript
readonly picklists = {
    // Global picklists
    listGlobal: async (): Promise<GlobalPicklist[]> => { ... },
    createGlobal: async (request: CreateGlobalPicklistRequest): Promise<GlobalPicklist> => { ... },
    getGlobal: async (id: string): Promise<GlobalPicklist> => { ... },
    updateGlobal: async (id: string, request: Partial<CreateGlobalPicklistRequest>): Promise<GlobalPicklist> => { ... },
    deleteGlobal: async (id: string): Promise<void> => { ... },

    // Field picklist values
    getFieldValues: async (fieldId: string): Promise<PicklistValue[]> => { ... },
    setFieldValues: async (fieldId: string, values: PicklistValueRequest[]): Promise<PicklistValue[]> => { ... },

    // Dependencies
    getDependencies: async (fieldId: string): Promise<PicklistDependency[]> => { ... },
    setDependency: async (request: SetDependencyRequest): Promise<PicklistDependency> => { ... },
    removeDependency: async (controllingFieldId: string, dependentFieldId: string): Promise<void> => { ... },
};
```

---

## Stream C: Relationship Types

### C1: Relationship Database Migration

**Blocked by:** A1 (field table must have field_type_config column)

**Purpose:** Add relationship-specific columns to the `field` table to support LOOKUP and MASTER_DETAIL field types with proper foreign key enforcement.

**Flyway file:** `V16__add_relationship_columns.sql`

```sql
-- Relationship metadata on field table
ALTER TABLE field ADD COLUMN relationship_type VARCHAR(20);
ALTER TABLE field ADD COLUMN relationship_name VARCHAR(100);
ALTER TABLE field ADD COLUMN cascade_delete BOOLEAN DEFAULT false;
ALTER TABLE field ADD COLUMN reference_collection_id VARCHAR(36);

-- FK to collection table for the target collection
ALTER TABLE field ADD CONSTRAINT fk_field_ref_collection
    FOREIGN KEY (reference_collection_id) REFERENCES collection(id);

-- Index for relationship lookups
CREATE INDEX idx_field_ref_collection ON field(reference_collection_id)
    WHERE reference_collection_id IS NOT NULL;

-- Constraint on relationship_type values
ALTER TABLE field ADD CONSTRAINT chk_relationship_type
    CHECK (relationship_type IS NULL OR relationship_type IN ('LOOKUP', 'MASTER_DETAIL'));

COMMENT ON COLUMN field.relationship_type IS 'LOOKUP (optional FK, ON DELETE SET NULL) or MASTER_DETAIL (required FK, ON DELETE CASCADE)';
COMMENT ON COLUMN field.relationship_name IS 'Child relationship name used in queries, e.g., "contacts" on Account';
COMMENT ON COLUMN field.cascade_delete IS 'Whether deleting parent deletes children (true for MASTER_DETAIL)';
COMMENT ON COLUMN field.reference_collection_id IS 'FK to the target/parent collection';
```

**Acceptance criteria:**
- Migration runs cleanly on fresh and existing databases
- Existing field rows unaffected (new columns nullable)
- FK constraint to collection table valid
- CHECK constraint enforces valid relationship_type values

**Integration points:** C2 (ReferenceConfig extension), C3-C4 (field handling), C5 (FK enforcement)

---

### C2: ReferenceConfig Extension

**Purpose:** Extend the runtime-core `ReferenceConfig` record to include relationship type and relationship name metadata.

**File:** `com.emf.runtime.model.ReferenceConfig` (`runtime-core/.../model/ReferenceConfig.java`)

**Current record fields:** `targetCollection`, `targetField`, `cascadeDelete`

```java
public record ReferenceConfig(
    String targetCollection,
    String targetField,
    boolean cascadeDelete,
    String relationshipType,   // NEW: "LOOKUP" or "MASTER_DETAIL" (null for generic REFERENCE)
    String relationshipName    // NEW: child relationship name, e.g., "contacts"
) {
    // Updated compact constructor
    public ReferenceConfig {
        Objects.requireNonNull(targetCollection, "targetCollection is required");
        if (targetField == null || targetField.isBlank()) {
            targetField = "id";
        }
    }

    // Existing factories (backward compatible)
    public static ReferenceConfig toCollection(String targetCollection) {
        return new ReferenceConfig(targetCollection, "id", false, null, null);
    }

    public static ReferenceConfig toCollectionWithCascade(String targetCollection) {
        return new ReferenceConfig(targetCollection, "id", true, null, null);
    }

    public static ReferenceConfig toField(String targetCollection, String targetField,
                                           boolean cascadeDelete) {
        return new ReferenceConfig(targetCollection, targetField, cascadeDelete, null, null);
    }

    // New factories for typed relationships
    public static ReferenceConfig lookup(String targetCollection, String relationshipName) {
        return new ReferenceConfig(targetCollection, "id", false, "LOOKUP", relationshipName);
    }

    public static ReferenceConfig masterDetail(String targetCollection, String relationshipName) {
        return new ReferenceConfig(targetCollection, "id", true, "MASTER_DETAIL", relationshipName);
    }
}
```

**Acceptance criteria:**
- Existing code using `toCollection()` and `toCollectionWithCascade()` continues to work
- New `lookup()` and `masterDetail()` factories produce correct configs
- `relationshipType` and `relationshipName` accessible on the record

**Integration points:**
- C3 (LOOKUP handling uses this config)
- C4 (MASTER_DETAIL handling uses this config)
- C7 (IncludeResolver reads relationshipName)

---

### C3: Lookup Field Handling

**Purpose:** When a LOOKUP field is created, update the control-plane Field entity with relationship metadata and coordinate FK creation in the storage layer.

**File to modify:** `com.emf.controlplane.service.FieldService`

**Changes to `addField()` for LOOKUP type:**

```java
if ("LOOKUP".equals(canonicalType)) {
    // Validate referenceTarget or reference_collection_id is provided
    if (request.referenceTarget() == null && request.referenceCollectionId() == null) {
        throw new ValidationException("LOOKUP fields require a target collection");
    }

    // Resolve target collection
    String targetCollectionId = request.referenceCollectionId();
    if (targetCollectionId == null) {
        Collection target = collectionRepository
            .findByNameAndActiveTrue(request.referenceTarget())
            .orElseThrow(() -> new ResourceNotFoundException("Collection", request.referenceTarget()));
        targetCollectionId = target.getId();
    }

    field.setReferenceCollectionId(targetCollectionId);
    field.setRelationshipType("LOOKUP");
    field.setRelationshipName(request.relationshipName());
    field.setCascadeDelete(false);  // LOOKUP never cascades
}
```

**Field entity additions (from C1 migration):**

```java
// Add to Field.java entity
@Column(name = "relationship_type", length = 20)
private String relationshipType;

@Column(name = "relationship_name", length = 100)
private String relationshipName;

@Column(name = "cascade_delete")
private boolean cascadeDelete = false;

@Column(name = "reference_collection_id", length = 36)
private String referenceCollectionId;

// Getters/setters
```

**Updated `AddFieldRequest` DTO:**

```java
public record AddFieldRequest(
    // ... existing fields ...
    String referenceCollectionId,  // NEW: target collection ID for LOOKUP/MASTER_DETAIL
    String relationshipName,       // NEW: child relationship name
    String fieldTypeConfig         // from A8
) {}
```

**Acceptance criteria:**
- Creating a LOOKUP field stores relationship metadata on the Field entity
- Target collection validated to exist
- Relationship name stored for query resolution
- LOOKUP fields are nullable by default

---

### C4: MasterDetail Field Handling

**Purpose:** When a MASTER_DETAIL field is created, enforce that it is required (NOT NULL) and configure cascade delete.

**File to modify:** `com.emf.controlplane.service.FieldService`

**Changes to `addField()` for MASTER_DETAIL type:**

```java
if ("MASTER_DETAIL".equals(canonicalType)) {
    if (request.referenceTarget() == null && request.referenceCollectionId() == null) {
        throw new ValidationException("MASTER_DETAIL fields require a target collection");
    }

    String targetCollectionId = resolveTargetCollection(request);

    field.setReferenceCollectionId(targetCollectionId);
    field.setRelationshipType("MASTER_DETAIL");
    field.setRelationshipName(request.relationshipName());
    field.setCascadeDelete(true);   // MASTER_DETAIL always cascades
    field.setRequired(true);        // MASTER_DETAIL is always required (NOT NULL)
}
```

**Constraints on MASTER_DETAIL:**
- Cannot change to LOOKUP (would break cascade guarantee)
- Cannot be made optional after creation
- Maximum 2 MASTER_DETAIL fields per collection (prevents complex cascade chains)
- Self-referencing MASTER_DETAIL is not allowed

```java
// Validate MASTER_DETAIL constraints
if ("MASTER_DETAIL".equals(canonicalType)) {
    long existingMasterDetails = fieldRepository
        .countByCollectionIdAndRelationshipTypeAndActiveTrue(collectionId, "MASTER_DETAIL");
    if (existingMasterDetails >= 2) {
        throw new ValidationException(
            "Maximum 2 MASTER_DETAIL fields per collection (current: " + existingMasterDetails + ")");
    }

    if (targetCollectionId.equals(collection.getId())) {
        throw new ValidationException("MASTER_DETAIL cannot be self-referencing. Use LOOKUP instead.");
    }
}
```

**Acceptance criteria:**
- MASTER_DETAIL fields are always required (NOT NULL)
- Cascade delete is always true
- Maximum 2 MASTER_DETAIL fields per collection enforced
- Self-referencing MASTER_DETAIL rejected with clear error
- Cannot change relationship type from MASTER_DETAIL to LOOKUP

---

### C5: FK Enforcement in StorageAdapter

**Purpose:** When `PhysicalTableStorageAdapter` creates or updates collection schemas, add real PostgreSQL foreign key constraints for LOOKUP and MASTER_DETAIL fields.

**File to modify:** `com.emf.runtime.storage.PhysicalTableStorageAdapter`

**Changes to `initializeCollection()`:**

```java
// After creating the base table, add FK constraints for relationship fields:
for (FieldDefinition field : definition.fields()) {
    if (field.type() == FieldType.LOOKUP && field.referenceConfig() != null) {
        String targetTable = "tbl_" + field.referenceConfig().targetCollection();
        String fkName = "fk_" + tableName + "_" + field.name();
        jdbcTemplate.execute(
            "ALTER TABLE " + tableName
            + " ADD CONSTRAINT " + fkName
            + " FOREIGN KEY (" + field.name() + ")"
            + " REFERENCES " + targetTable + "(id)"
            + " ON DELETE SET NULL"
        );
    }

    if (field.type() == FieldType.MASTER_DETAIL && field.referenceConfig() != null) {
        String targetTable = "tbl_" + field.referenceConfig().targetCollection();
        String fkName = "fk_" + tableName + "_" + field.name();
        jdbcTemplate.execute(
            "ALTER TABLE " + tableName
            + " ADD CONSTRAINT " + fkName
            + " FOREIGN KEY (" + field.name() + ")"
            + " REFERENCES " + targetTable + "(id)"
            + " ON DELETE CASCADE"
        );
    }
}
```

**Changes to `create()` method:** For MASTER_DETAIL fields, validate that the referenced parent record exists before insert:

```java
if (field.type() == FieldType.MASTER_DETAIL) {
    String parentId = (String) data.get(field.name());
    if (parentId == null) {
        throw new ValidationException("MASTER_DETAIL field '" + field.name() + "' is required");
    }
    // FK constraint will enforce at DB level, but check early for better error message
    String targetTable = "tbl_" + field.referenceConfig().targetCollection();
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + targetTable + " WHERE id = ?",
        Integer.class, parentId);
    if (count == 0) {
        throw new ValidationException(
            "Referenced record not found: " + field.referenceConfig().targetCollection() + "/" + parentId);
    }
}
```

**Acceptance criteria:**
- LOOKUP fields create FK with ON DELETE SET NULL
- MASTER_DETAIL fields create FK with ON DELETE CASCADE
- MASTER_DETAIL column is NOT NULL
- Referencing a nonexistent parent record returns 400 with clear error
- Deleting a parent with MASTER_DETAIL children cascades the delete

---

### C6: Cascade Delete Handling

**Purpose:** Ensure cascade deletes for MASTER_DETAIL relationships are handled correctly, including field history recording and event publishing.

**File to modify:** `com.emf.runtime.storage.PhysicalTableStorageAdapter` -- `delete()` method

**Pre-delete hook:**

```java
@Override
public boolean delete(CollectionDefinition definition, String id) {
    // Before deleting, find child collections with MASTER_DETAIL relationships
    // to this collection and record the cascade for audit purposes
    List<FieldDefinition> masterDetailFields = findMasterDetailFieldsPointingTo(definition.name());

    if (!masterDetailFields.isEmpty()) {
        // Log cascade delete info (child records will be deleted by DB CASCADE)
        for (FieldDefinition mdField : masterDetailFields) {
            int childCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + getTableName(mdField.collection())
                + " WHERE " + mdField.name() + " = ?",
                Integer.class, id);
            if (childCount > 0) {
                log.info("Cascade delete: {} child records in {} will be deleted",
                    childCount, mdField.collection());
                // Publish cascade event for audit
                if (eventPublisher != null) {
                    eventPublisher.publishCascadeDelete(
                        definition.name(), id, mdField.collection(), childCount);
                }
            }
        }
    }

    // Execute delete (PostgreSQL handles CASCADE)
    String sql = "DELETE FROM " + getTableName(definition) + " WHERE id = ?";
    int rows = jdbcTemplate.update(sql, id);
    return rows > 0;
}
```

**Acceptance criteria:**
- Deleting a parent record cascades to MASTER_DETAIL children
- Cascade deletes are logged with child record counts
- LOOKUP children have their FK set to NULL (not deleted)
- Cascade events published for audit trail

---

### C7: IncludeResolver Update

**Purpose:** Update the gateway's `IncludeResolver` to use relationship name metadata for resolving JSON:API `?include=` parameters, supporting named relationships (e.g., `?include=contacts` instead of requiring the collection name).

**File to modify:** `com.emf.gateway.jsonapi.IncludeResolver` (`emf-gateway/.../jsonapi/IncludeResolver.java`)

**Current behavior:** Include parameters are treated as collection names. The resolver fetches related resources by type and ID from relationship data in the primary resource.

**Updated behavior:** Include parameters can be relationship names. The resolver looks up the relationship definition to find the actual collection name and FK field.

```java
public Mono<List<ResourceObject>> resolveIncludes(
        List<String> includeParams,
        List<ResourceObject> primaryData) {

    return Flux.fromIterable(includeParams)
        .flatMap(includeName -> {
            // Try to resolve as relationship name first
            String collectionName = resolveRelationshipName(
                primaryData.get(0).type(), includeName);

            // Fall back to treating includeName as collection name
            if (collectionName == null) {
                collectionName = includeName;
            }

            return resolveInclude(collectionName, primaryData);
        })
        .collectList()
        .map(lists -> lists.stream().flatMap(List::stream).toList());
}

/**
 * Looks up a relationship name on a collection to find the related collection.
 * E.g., "contacts" on "account" → resolves to collection "contact" via the
 * MASTER_DETAIL/LOOKUP field that has relationship_name = "contacts".
 */
private String resolveRelationshipName(String parentCollection, String relationshipName) {
    // Query Redis cache or control plane for relationship metadata
    // Key: relationship:{parentCollection}:{relationshipName}
    // Value: target collection name
    ...
}
```

**Cache population:** Control plane exposes `GET /internal/collections/{name}/relationships` returning relationship metadata. Gateway caches in Redis (key: `relationship:{collection}:{name}`, TTL: 10 min). Invalidated via `emf.config.collection.changed` Kafka event.

**Acceptance criteria:**
- `?include=contacts` resolves correctly when "contacts" is a relationship name on the parent collection
- Backward compatible: direct collection names still work
- Cache miss triggers control plane lookup
- Cache invalidated when collection schema changes

---

### C8: Relationship Administration UI

**Purpose:** Frontend components for managing LOOKUP and MASTER_DETAIL relationships within the field management UI.

**Location:** Components within existing field management pages

**Features:**
- When creating a LOOKUP or MASTER_DETAIL field:
  - Target collection selector (dropdown of all collections)
  - Relationship name input (auto-suggested from collection name, e.g., "contacts")
  - For MASTER_DETAIL: warning that cascade delete is enabled
  - For MASTER_DETAIL: display count of existing MASTER_DETAIL fields (max 2)
- Relationship diagram: visual display of all relationships for a collection
  - Shows parent collections (MASTER_DETAIL/LOOKUP from this collection)
  - Shows child collections (MASTER_DETAIL/LOOKUP to this collection)
  - Arrows indicate relationship type and direction
- Related list configuration: when a relationship exists, allow adding a "Related List" component to the parent's page layout

---

### C9: Relationship SDK Types

**Purpose:** TypeScript types for relationship management in the frontend SDK.

**File:** `emf-web/packages/sdk/src/admin/types.ts` (additions)

```typescript
export interface RelationshipInfo {
    fieldId: string;
    fieldName: string;
    relationshipType: 'LOOKUP' | 'MASTER_DETAIL';
    relationshipName: string;
    targetCollectionId: string;
    targetCollectionName: string;
    cascadeDelete: boolean;
}

export interface CollectionRelationships {
    parentRelationships: RelationshipInfo[];   // fields on THIS collection pointing to others
    childRelationships: RelationshipInfo[];    // fields on OTHER collections pointing to this one
}
```

**SDK client additions:**

```typescript
// Additions to existing collections client
readonly collections = {
    // ... existing methods ...
    getRelationships: async (collectionId: string): Promise<CollectionRelationships> => { ... },
};
```

---

## Stream D: Validation Rules and Record Types

### D1: Validation Rules Database Migration

**Purpose:** Create the `validation_rule` table for storing cross-field validation rules with formula-based error conditions.

**Flyway file:** `V17__add_validation_rules.sql`

```sql
CREATE TABLE validation_rule (
    id                      VARCHAR(36)  PRIMARY KEY,
    tenant_id               VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id           VARCHAR(36)  NOT NULL REFERENCES collection(id),
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(500),
    active                  BOOLEAN      DEFAULT true,
    error_condition_formula TEXT         NOT NULL,
    error_message           VARCHAR(1000) NOT NULL,
    error_field             VARCHAR(100),
    evaluate_on             VARCHAR(20)  DEFAULT 'CREATE_AND_UPDATE',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_validation_rule UNIQUE (tenant_id, collection_id, name),
    CONSTRAINT chk_evaluate_on CHECK (evaluate_on IN ('CREATE', 'UPDATE', 'CREATE_AND_UPDATE'))
);

CREATE INDEX idx_validation_rule_collection ON validation_rule(collection_id, active);
CREATE INDEX idx_validation_rule_tenant ON validation_rule(tenant_id);

COMMENT ON COLUMN validation_rule.error_condition_formula IS
'Formula expression that evaluates to TRUE when the record is INVALID.
 Examples:
   Amount < 0
   AND(Stage = ''Closed Won'', ISBLANK(CloseDate))
   EndDate < StartDate';

COMMENT ON COLUMN validation_rule.error_field IS
'Optional: field name to associate the error with in the UI. If null, error is record-level.';
```

**Acceptance criteria:**
- Migration runs cleanly on fresh and existing databases
- Validation rule names unique per (tenant, collection)
- evaluate_on constraint enforced

**Integration points:** D2 (entity), D4 (service)

---

### D2: ValidationRule Entity

**Purpose:** JPA entity for validation rules.

**File:** `com.emf.controlplane.entity.ValidationRule` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "validation_rule")
public class ValidationRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "error_condition_formula", nullable = false, columnDefinition = "TEXT")
    private String errorConditionFormula;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "error_field", length = 100)
    private String errorField;

    @Column(name = "evaluate_on", length = 20)
    private String evaluateOn = "CREATE_AND_UPDATE";

    // Constructors
    public ValidationRule() { super(); }
    public ValidationRule(String name, String errorConditionFormula, String errorMessage) {
        super();
        this.name = name;
        this.errorConditionFormula = errorConditionFormula;
        this.errorMessage = errorMessage;
    }

    // Getters/setters for all fields
}
```

**Conventions followed:**
- Extends `BaseEntity` (gets id, createdAt, updatedAt)
- ManyToOne to Collection (LAZY to avoid unnecessary joins)
- Same patterns as existing entities (Field, Collection)

**Integration points:**
- D4 (ValidationRuleService manages CRUD)
- D5 (validation on create/update reads active rules)

---

### D3: FormulaEvaluator Integration

**Purpose:** Wire the `FormulaEvaluator` (A10) into the validation rule evaluation pipeline so that validation rule formulas are parsed and evaluated against record data.

**Depends on:** A10 (FormulaEvaluator must exist)

**File:** `com.emf.runtime.validation.ValidationRuleEvaluator` (new, in runtime-core)

```java
package com.emf.runtime.validation;

@Component
public class ValidationRuleEvaluator {
    private final FormulaEvaluator formulaEvaluator;

    /**
     * Evaluates a validation rule's error condition against a record.
     *
     * @param errorConditionFormula the formula that returns TRUE when invalid
     * @param recordData the record's field values
     * @return true if the record FAILS validation (error condition is true)
     */
    public boolean isInvalid(String errorConditionFormula, Map<String, Object> recordData) {
        try {
            return formulaEvaluator.evaluateBoolean(errorConditionFormula, recordData);
        } catch (FormulaException e) {
            // Log the error but don't block the save for broken formulas
            log.warn("Validation rule formula evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a formula expression is syntactically correct.
     * Called when creating/updating a validation rule.
     */
    public void validateFormulaSyntax(String expression) {
        formulaEvaluator.validate(expression);
    }
}
```

**Acceptance criteria:**
- Validation rule formulas evaluate correctly against record data
- Broken formulas (syntax errors at runtime) log a warning but don't block saves
- Formula syntax validated when creating/updating a validation rule
- FormulaEvaluator correctly handles field references, comparisons, function calls

---

### D4: ValidationRuleService

**Purpose:** CRUD operations for validation rules and batch evaluation during record saves.

**Files:**
- `com.emf.controlplane.repository.ValidationRuleRepository`
- `com.emf.controlplane.service.ValidationRuleService`

**Repository:**

```java
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, String> {
    List<ValidationRule> findByCollectionIdAndActiveTrueOrderByNameAsc(String collectionId);
    List<ValidationRule> findByCollectionIdOrderByNameAsc(String collectionId);
    Optional<ValidationRule> findByTenantIdAndCollectionIdAndName(
        String tenantId, String collectionId, String name);
    boolean existsByTenantIdAndCollectionIdAndName(
        String tenantId, String collectionId, String name);
    long countByCollectionIdAndActiveTrue(String collectionId);
}
```

**Service methods:**

```java
@Service
public class ValidationRuleService {
    // Dependencies: ValidationRuleRepository, ValidationRuleEvaluator (D3),
    //               CollectionRepository, ConfigEventPublisher

    @Transactional(readOnly = true)
    public List<ValidationRule> listRules(String collectionId)

    @Transactional
    public ValidationRule createRule(String collectionId, CreateValidationRuleRequest request)
    // 1. Validate name uniqueness per (tenant, collection)
    // 2. Validate formula syntax via ValidationRuleEvaluator.validateFormulaSyntax()
    // 3. If errorField is set, validate field exists on collection
    // 4. Save rule
    // 5. Publish emf.config.collection.changed event

    @Transactional(readOnly = true)
    public ValidationRule getRule(String ruleId)

    @Transactional
    public ValidationRule updateRule(String ruleId, UpdateValidationRuleRequest request)
    // Re-validates formula syntax on change

    @Transactional
    public void deleteRule(String ruleId)

    @Transactional
    public void activateRule(String ruleId)

    @Transactional
    public void deactivateRule(String ruleId)

    /**
     * Evaluates all active validation rules for a collection against a record.
     * Called from the storage layer on create/update.
     *
     * @param collectionId the collection ID
     * @param recordData the record field values
     * @param operationType CREATE or UPDATE
     * @return list of validation errors (empty if all rules pass)
     */
    @Transactional(readOnly = true)
    public List<ValidationError> evaluate(String collectionId,
                                           Map<String, Object> recordData,
                                           OperationType operationType) {
        List<ValidationRule> rules = validationRuleRepository
            .findByCollectionIdAndActiveTrueOrderByNameAsc(collectionId);

        List<ValidationError> errors = new ArrayList<>();
        for (ValidationRule rule : rules) {
            // Check if rule applies to this operation type
            if (!appliesTo(rule.getEvaluateOn(), operationType)) continue;

            if (validationRuleEvaluator.isInvalid(rule.getErrorConditionFormula(), recordData)) {
                errors.add(new ValidationError(
                    rule.getName(),
                    rule.getErrorMessage(),
                    rule.getErrorField()
                ));
            }
        }
        return errors;
    }

    private boolean appliesTo(String evaluateOn, OperationType operationType) {
        return "CREATE_AND_UPDATE".equals(evaluateOn)
            || ("CREATE".equals(evaluateOn) && operationType == OperationType.CREATE)
            || ("UPDATE".equals(evaluateOn) && operationType == OperationType.UPDATE);
    }
}
```

**DTO definitions:**

```java
public record CreateValidationRuleRequest(
    @NotBlank String name,
    String description,
    @NotBlank String errorConditionFormula,
    @NotBlank String errorMessage,
    String errorField,
    String evaluateOn  // defaults to CREATE_AND_UPDATE
) {}

public record UpdateValidationRuleRequest(
    String name,
    String description,
    String errorConditionFormula,
    String errorMessage,
    String errorField,
    String evaluateOn,
    Boolean active
) {}

public record ValidationError(
    String ruleName,
    String errorMessage,
    String errorField  // nullable
) {}
```

**Integration points:**
- D5 (called from storage adapter on create/update)
- D3 (uses ValidationRuleEvaluator for formula evaluation)
- E9 (setup audit captures rule changes)

---

### D5: Validation on Record Create/Update

**Purpose:** Wire `ValidationRuleService.evaluate()` into the record create and update flows so that validation rules fire automatically.

**File to modify:** `com.emf.runtime.storage.PhysicalTableStorageAdapter`

**Changes to `create()` method:**

```java
@Override
public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
    // ... existing type conversion, auto-number generation ...

    // Evaluate validation rules BEFORE insert
    if (validationRuleService != null) {
        List<ValidationError> errors = validationRuleService.evaluate(
            definition.name(), data, OperationType.CREATE);
        if (!errors.isEmpty()) {
            throw new RecordValidationException(errors);
        }
    }

    // ... existing INSERT logic ...
}
```

**Changes to `update()` method:**

```java
@Override
public Optional<Map<String, Object>> update(CollectionDefinition definition,
                                             String id, Map<String, Object> data) {
    // Merge with existing record for full-record validation
    Optional<Map<String, Object>> existing = getById(definition, id);
    if (existing.isEmpty()) return Optional.empty();

    Map<String, Object> merged = new HashMap<>(existing.get());
    merged.putAll(data);

    // Evaluate validation rules against the merged record
    if (validationRuleService != null) {
        List<ValidationError> errors = validationRuleService.evaluate(
            definition.name(), merged, OperationType.UPDATE);
        if (!errors.isEmpty()) {
            throw new RecordValidationException(errors);
        }
    }

    // ... existing UPDATE logic ...
}
```

**RecordValidationException:**

```java
package com.emf.runtime.validation;

public class RecordValidationException extends RuntimeException {
    private final List<ValidationError> errors;

    public RecordValidationException(List<ValidationError> errors) {
        super("Record validation failed: " + errors.size() + " rule(s) violated");
        this.errors = errors;
    }

    public List<ValidationError> getErrors() { return errors; }
}
```

**Gateway error handling:** The gateway's error handler maps `RecordValidationException` to HTTP 400 with a JSON body containing the validation errors:

```json
{
    "error": "VALIDATION_FAILED",
    "message": "Record validation failed: 2 rule(s) violated",
    "validationErrors": [
        {
            "ruleName": "Close Date Required",
            "message": "Close Date is required when Stage is Closed Won",
            "field": "close_date"
        },
        {
            "ruleName": "Positive Amount",
            "message": "Amount must be greater than zero",
            "field": "amount"
        }
    ]
}
```

**Acceptance criteria:**
- Validation rules fire before INSERT on create
- Validation rules fire before UPDATE (against merged record)
- Multiple rule violations returned in a single response
- Rule errors include rule name, message, and optional field
- evaluate_on setting respected (CREATE only, UPDATE only, or both)

---

### D6: ValidationRule Controller

**Purpose:** REST endpoints for managing validation rules per collection.

**File:** `com.emf.controlplane.controller.ValidationRuleController` (new)

**Base path:** `/control/collections/{collectionId}/validation-rules`

```java
@RestController
@RequestMapping("/control/collections/{collectionId}/validation-rules")
@SecurityRequirement(name = "bearer-jwt")
public class ValidationRuleController {

    @GetMapping
    public List<ValidationRuleDto> listRules(@PathVariable String collectionId)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<ValidationRuleDto> createRule(
        @PathVariable String collectionId,
        @Valid @RequestBody CreateValidationRuleRequest request)

    @GetMapping("/{ruleId}")
    public ValidationRuleDto getRule(
        @PathVariable String collectionId, @PathVariable String ruleId)

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ValidationRuleDto updateRule(
        @PathVariable String collectionId,
        @PathVariable String ruleId,
        @Valid @RequestBody UpdateValidationRuleRequest request)

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> deleteRule(
        @PathVariable String collectionId, @PathVariable String ruleId)

    @PostMapping("/{ruleId}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> activateRule(
        @PathVariable String collectionId, @PathVariable String ruleId)

    @PostMapping("/{ruleId}/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> deactivateRule(
        @PathVariable String collectionId, @PathVariable String ruleId)

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public List<ValidationError> testRules(
        @PathVariable String collectionId,
        @RequestBody Map<String, Object> testRecord)
    // Evaluates all active rules against a test record without persisting
}
```

**Integration points:**
- D15 (UI calls these endpoints)
- E9 (setup audit captures rule changes)

---

### D7: Record Type Database Migration

**Purpose:** Create tables for record types, record-type-specific picklist values, and profile-to-record-type assignments.

**Flyway file:** `V18__add_record_types.sql`

```sql
CREATE TABLE record_type (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    is_active     BOOLEAN      DEFAULT true,
    is_default    BOOLEAN      DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_record_type UNIQUE (tenant_id, collection_id, name)
);

CREATE INDEX idx_record_type_collection ON record_type(collection_id, is_active);

-- Which picklist values are available per record type
CREATE TABLE record_type_picklist (
    id               VARCHAR(36)  PRIMARY KEY,
    record_type_id   VARCHAR(36)  NOT NULL REFERENCES record_type(id) ON DELETE CASCADE,
    field_id         VARCHAR(36)  NOT NULL REFERENCES field(id),
    available_values JSONB        NOT NULL,
    default_value    VARCHAR(255),
    CONSTRAINT uq_rtp UNIQUE (record_type_id, field_id)
);

-- Which profiles can use which record types
CREATE TABLE profile_record_type (
    profile_id     VARCHAR(36) NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    record_type_id VARCHAR(36) NOT NULL REFERENCES record_type(id) ON DELETE CASCADE,
    is_default     BOOLEAN     DEFAULT false,
    PRIMARY KEY (profile_id, record_type_id)
);

COMMENT ON COLUMN record_type_picklist.available_values IS
'JSON array of picklist value strings available for this record type.
 Example: ["New", "In Progress", "Closed"]
 Must be a subset of the full picklist values defined on the field.';
```

**Acceptance criteria:**
- Record type names unique per (tenant, collection)
- Record type picklist values reference existing fields
- Profile-to-record-type assignment supports default per profile

**Integration points:** D8-D10 (entities), D11 (service)

---

### D8: RecordType Entity

**Purpose:** JPA entity for record types.

**File:** `com.emf.controlplane.entity.RecordType` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "record_type")
public class RecordType extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @OneToMany(mappedBy = "recordType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecordTypePicklist> picklistOverrides = new ArrayList<>();

    // Constructors, getters/setters
}
```

---

### D9: RecordTypePicklist Entity

**Purpose:** JPA entity for record-type-specific picklist value overrides.

**File:** `com.emf.controlplane.entity.RecordTypePicklist` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "record_type_picklist")
public class RecordTypePicklist extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "record_type_id", nullable = false)
    private RecordType recordType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_values", nullable = false, columnDefinition = "jsonb")
    private String availableValues;  // JSON array: ["New", "In Progress", "Closed"]

    @Column(name = "default_value", length = 255)
    private String defaultValue;

    // Constructors, getters/setters
}
```

---

### D10: ProfileRecordType Entity

**Purpose:** JPA entity for the junction between profiles and record types (which profiles can create which record types).

**File:** `com.emf.controlplane.entity.ProfileRecordType` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "profile_record_type")
@IdClass(ProfileRecordTypeId.class)
public class ProfileRecordType {

    @Id
    @Column(name = "profile_id", length = 36)
    private String profileId;

    @Id
    @Column(name = "record_type_id", length = 36)
    private String recordTypeId;

    @Column(name = "is_default")
    private boolean isDefault = false;

    // Constructors, getters/setters
}

// Composite key class
public class ProfileRecordTypeId implements Serializable {
    private String profileId;
    private String recordTypeId;
    // equals, hashCode
}
```

---

### D11: RecordTypeService

**Purpose:** CRUD operations for record types, picklist overrides, and profile assignments.

**Files:**
- `com.emf.controlplane.repository.RecordTypeRepository`
- `com.emf.controlplane.repository.RecordTypePicklistRepository`
- `com.emf.controlplane.repository.ProfileRecordTypeRepository`
- `com.emf.controlplane.service.RecordTypeService`

**Repositories:**

```java
public interface RecordTypeRepository extends JpaRepository<RecordType, String> {
    List<RecordType> findByCollectionIdAndActiveTrueOrderByNameAsc(String collectionId);
    List<RecordType> findByCollectionIdOrderByNameAsc(String collectionId);
    Optional<RecordType> findByTenantIdAndCollectionIdAndName(
        String tenantId, String collectionId, String name);
    boolean existsByTenantIdAndCollectionIdAndName(
        String tenantId, String collectionId, String name);
    Optional<RecordType> findByCollectionIdAndIsDefaultTrue(String collectionId);
}

public interface RecordTypePicklistRepository extends JpaRepository<RecordTypePicklist, String> {
    List<RecordTypePicklist> findByRecordTypeId(String recordTypeId);
    Optional<RecordTypePicklist> findByRecordTypeIdAndFieldId(String recordTypeId, String fieldId);
}

public interface ProfileRecordTypeRepository
        extends JpaRepository<ProfileRecordType, ProfileRecordTypeId> {
    List<ProfileRecordType> findByProfileId(String profileId);
    List<ProfileRecordType> findByRecordTypeId(String recordTypeId);
    Optional<ProfileRecordType> findByProfileIdAndIsDefaultTrue(String profileId);
}
```

**Service methods:**

```java
@Service
public class RecordTypeService {
    // Dependencies: RecordTypeRepository, RecordTypePicklistRepository,
    //               ProfileRecordTypeRepository, CollectionRepository,
    //               PicklistService (B5), ConfigEventPublisher

    // --- Record Type CRUD ---

    @Transactional(readOnly = true)
    public List<RecordType> listRecordTypes(String collectionId)

    @Transactional
    public RecordType createRecordType(String collectionId, CreateRecordTypeRequest request)
    // Validates name uniqueness per (tenant, collection)
    // If is_default=true, unset any existing default

    @Transactional(readOnly = true)
    public RecordType getRecordType(String recordTypeId)

    @Transactional
    public RecordType updateRecordType(String recordTypeId, UpdateRecordTypeRequest request)

    @Transactional
    public void deleteRecordType(String recordTypeId)
    // Cannot delete if records exist with this record type

    // --- Picklist Override Management ---

    @Transactional
    public void setPicklistOverride(String recordTypeId, String fieldId,
                                     List<String> availableValues, String defaultValue)
    // Validates: field is PICKLIST or MULTI_PICKLIST
    // Validates: availableValues is subset of full picklist values (via PicklistService)

    @Transactional
    public void removePicklistOverride(String recordTypeId, String fieldId)

    @Transactional(readOnly = true)
    public List<RecordTypePicklist> getPicklistOverrides(String recordTypeId)

    // --- Profile Assignment ---

    @Transactional
    public void assignToProfile(String recordTypeId, String profileId, boolean isDefault)
    // If isDefault=true, unset other defaults for this profile+collection

    @Transactional
    public void removeFromProfile(String recordTypeId, String profileId)

    @Transactional(readOnly = true)
    public List<RecordType> getRecordTypesForProfile(String profileId, String collectionId)
    // Returns record types available to a profile for a specific collection

    /**
     * Resolves the default record type for a user creating a record.
     * Used by the storage layer on record creation.
     */
    @Transactional(readOnly = true)
    public Optional<RecordType> resolveDefaultRecordType(String profileId, String collectionId)
}
```

**DTO definitions:**

```java
public record CreateRecordTypeRequest(
    @NotBlank String name,
    String description,
    boolean isDefault
) {}

public record UpdateRecordTypeRequest(
    String name,
    String description,
    Boolean active,
    Boolean isDefault
) {}
```

**Integration points:**
- D12 (storage layer uses record_type_id)
- D13 (controller exposes these methods)

---

### D12: Storage record_type_id Column

**Purpose:** Add `record_type_id` as a system column to all collection tables created by `PhysicalTableStorageAdapter`.

**File to modify:** `com.emf.runtime.storage.PhysicalTableStorageAdapter`

**Changes to `initializeCollection()`:**

```java
// Add record_type_id as a system column (like id, created_at, updated_at)
columns.add("record_type_id VARCHAR(36)");
```

**Changes to `create()` method:**

```java
// If record_type_id not provided and a default exists, set it
if (!data.containsKey("record_type_id") || data.get("record_type_id") == null) {
    if (recordTypeService != null) {
        Optional<RecordType> defaultType = recordTypeService
            .resolveDefaultRecordType(currentUserProfileId, definition.name());
        defaultType.ifPresent(rt -> data.put("record_type_id", rt.getId()));
    }
}
```

**Acceptance criteria:**
- All new collection tables include `record_type_id VARCHAR(36)` column
- Record creation sets record_type_id to default if not provided
- Record type can be specified explicitly on create
- Existing tables: column added via schema migration when record types are enabled

---

### D13: RecordType Controller

**Purpose:** REST endpoints for managing record types per collection.

**File:** `com.emf.controlplane.controller.RecordTypeController` (new)

**Base path:** `/control/collections/{collectionId}/record-types`

```java
@RestController
@RequestMapping("/control/collections/{collectionId}/record-types")
@SecurityRequirement(name = "bearer-jwt")
public class RecordTypeController {

    @GetMapping
    public List<RecordTypeDto> listRecordTypes(@PathVariable String collectionId)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<RecordTypeDto> createRecordType(
        @PathVariable String collectionId,
        @Valid @RequestBody CreateRecordTypeRequest request)

    @GetMapping("/{recordTypeId}")
    public RecordTypeDto getRecordType(
        @PathVariable String collectionId, @PathVariable String recordTypeId)

    @PutMapping("/{recordTypeId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public RecordTypeDto updateRecordType(
        @PathVariable String collectionId,
        @PathVariable String recordTypeId,
        @Valid @RequestBody UpdateRecordTypeRequest request)

    @DeleteMapping("/{recordTypeId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> deleteRecordType(
        @PathVariable String collectionId, @PathVariable String recordTypeId)

    // --- Picklist Overrides ---

    @GetMapping("/{recordTypeId}/picklists")
    public List<RecordTypePicklistDto> getPicklistOverrides(
        @PathVariable String collectionId, @PathVariable String recordTypeId)

    @PutMapping("/{recordTypeId}/picklists/{fieldId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> setPicklistOverride(
        @PathVariable String collectionId,
        @PathVariable String recordTypeId,
        @PathVariable String fieldId,
        @Valid @RequestBody SetPicklistOverrideRequest request)

    @DeleteMapping("/{recordTypeId}/picklists/{fieldId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> removePicklistOverride(
        @PathVariable String collectionId,
        @PathVariable String recordTypeId,
        @PathVariable String fieldId)

    // --- Profile Assignment ---

    @PutMapping("/{recordTypeId}/profiles/{profileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> assignToProfile(
        @PathVariable String collectionId,
        @PathVariable String recordTypeId,
        @PathVariable String profileId,
        @RequestParam(defaultValue = "false") boolean isDefault)

    @DeleteMapping("/{recordTypeId}/profiles/{profileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CUSTOMIZE_APPLICATION')")
    public ResponseEntity<Void> removeFromProfile(
        @PathVariable String collectionId,
        @PathVariable String recordTypeId,
        @PathVariable String profileId)
}
```

---

### D14: Record Type Administration UI

**Purpose:** Frontend pages for managing record types, picklist overrides, and profile assignments.

**Location:** `emf-ui/app/src/pages/RecordTypeManagement.tsx` (new)

**Features:**
- Record type list per collection with active/default badges
- Create/edit record type dialog (name, description, active, default)
- Picklist override editor:
  - For each PICKLIST field on the collection, select which values are available for this record type
  - Checkbox list of all picklist values, with selected values highlighted
  - Default value selector (must be one of the available values)
- Profile assignment:
  - Multi-select of profiles that can use this record type
  - Default record type per profile selector
- Record type selector: when creating a record, if collection has record types, show a selector

**Navigation:** Accessible from collection detail page under "Record Types" tab.

---

### D15: Validation Rule Administration UI

**Purpose:** Frontend pages for creating and managing validation rules with a formula expression editor.

**Location:** `emf-ui/app/src/pages/ValidationRuleEditor.tsx` (new)

**Features:**
- Validation rule list per collection with active/inactive toggle
- Create/edit rule dialog:
  - Name, description
  - Formula expression editor with syntax highlighting
  - Field picker: insert field references by clicking (shows collection's fields)
  - Function picker: insert function templates by clicking
  - Return type indicator (must be Boolean for validation rules)
  - Error message input
  - Error field selector (optional, dropdown of collection's fields)
  - Evaluate on: radio buttons (Create Only, Update Only, Both)
- Test panel: enter sample field values, click "Test" to evaluate the formula
- Active/inactive toggle with confirmation

**Navigation:** Accessible from collection detail page under "Validation Rules" tab.

---

### D16: Validation and RecordType SDK Types

**Purpose:** TypeScript types for validation rules and record types in the frontend SDK.

**File:** `emf-web/packages/sdk/src/admin/types.ts` (additions)

```typescript
// --- Validation Rules ---

export interface ValidationRule {
    id: string;
    collectionId: string;
    name: string;
    description?: string;
    active: boolean;
    errorConditionFormula: string;
    errorMessage: string;
    errorField?: string;
    evaluateOn: 'CREATE' | 'UPDATE' | 'CREATE_AND_UPDATE';
    createdAt: string;
    updatedAt: string;
}

export interface CreateValidationRuleRequest {
    name: string;
    description?: string;
    errorConditionFormula: string;
    errorMessage: string;
    errorField?: string;
    evaluateOn?: 'CREATE' | 'UPDATE' | 'CREATE_AND_UPDATE';
}

export interface ValidationError {
    ruleName: string;
    message: string;
    field?: string;
}

// --- Record Types ---

export interface RecordType {
    id: string;
    collectionId: string;
    name: string;
    description?: string;
    active: boolean;
    isDefault: boolean;
    picklistOverrides: RecordTypePicklistOverride[];
    createdAt: string;
    updatedAt: string;
}

export interface RecordTypePicklistOverride {
    fieldId: string;
    fieldName: string;
    availableValues: string[];
    defaultValue?: string;
}

export interface CreateRecordTypeRequest {
    name: string;
    description?: string;
    isDefault?: boolean;
}

export interface SetPicklistOverrideRequest {
    availableValues: string[];
    defaultValue?: string;
}
```

**SDK client additions:**

```typescript
readonly validationRules = {
    list: async (collectionId: string): Promise<ValidationRule[]> => { ... },
    create: async (collectionId: string, request: CreateValidationRuleRequest): Promise<ValidationRule> => { ... },
    get: async (collectionId: string, ruleId: string): Promise<ValidationRule> => { ... },
    update: async (collectionId: string, ruleId: string, request: Partial<CreateValidationRuleRequest>): Promise<ValidationRule> => { ... },
    delete: async (collectionId: string, ruleId: string): Promise<void> => { ... },
    activate: async (collectionId: string, ruleId: string): Promise<void> => { ... },
    deactivate: async (collectionId: string, ruleId: string): Promise<void> => { ... },
    test: async (collectionId: string, testRecord: Record<string, unknown>): Promise<ValidationError[]> => { ... },
};

readonly recordTypes = {
    list: async (collectionId: string): Promise<RecordType[]> => { ... },
    create: async (collectionId: string, request: CreateRecordTypeRequest): Promise<RecordType> => { ... },
    get: async (collectionId: string, recordTypeId: string): Promise<RecordType> => { ... },
    update: async (collectionId: string, recordTypeId: string, request: Partial<CreateRecordTypeRequest>): Promise<RecordType> => { ... },
    delete: async (collectionId: string, recordTypeId: string): Promise<void> => { ... },
    getPicklistOverrides: async (collectionId: string, recordTypeId: string): Promise<RecordTypePicklistOverride[]> => { ... },
    setPicklistOverride: async (collectionId: string, recordTypeId: string, fieldId: string, request: SetPicklistOverrideRequest): Promise<void> => { ... },
    removePicklistOverride: async (collectionId: string, recordTypeId: string, fieldId: string): Promise<void> => { ... },
    assignToProfile: async (collectionId: string, recordTypeId: string, profileId: string, isDefault?: boolean): Promise<void> => { ... },
    removeFromProfile: async (collectionId: string, recordTypeId: string, profileId: string): Promise<void> => { ... },
};
```

---

## Stream E: Field History and Setup Audit Enhancement

### E1: Field History Database Migration

**Purpose:** Create the `field_history` table for tracking changes to individual field values on records.

**Flyway file:** `V19__add_field_history.sql`

```sql
CREATE TABLE field_history (
    id            VARCHAR(36)  PRIMARY KEY,
    tenant_id     VARCHAR(36)  NOT NULL REFERENCES tenant(id),
    collection_id VARCHAR(36)  NOT NULL REFERENCES collection(id),
    record_id     VARCHAR(36)  NOT NULL,
    field_name    VARCHAR(100) NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    changed_by    VARCHAR(36)  NOT NULL REFERENCES platform_user(id),
    changed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    change_source VARCHAR(20)  NOT NULL DEFAULT 'UI'
);

CREATE INDEX idx_field_history_record
    ON field_history(collection_id, record_id, changed_at DESC);
CREATE INDEX idx_field_history_field
    ON field_history(collection_id, field_name, changed_at DESC);
CREATE INDEX idx_field_history_user
    ON field_history(changed_by, changed_at DESC);

COMMENT ON COLUMN field_history.change_source IS
'Source of the change: UI, API, WORKFLOW, SYSTEM, IMPORT';
```

**Acceptance criteria:**
- Migration runs cleanly on fresh and existing databases
- Indexes support efficient record-level and field-level history queries
- change_source provides audit context

**Integration points:** E2 (entity), E3 (service), E5 (track_history column)

---

### E2: FieldHistory Entity

**Purpose:** JPA entity for field history records.

**File:** `com.emf.controlplane.entity.FieldHistory` (new)

```java
package com.emf.controlplane.entity;

@Entity
@Table(name = "field_history")
public class FieldHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "record_id", nullable = false, length = 36)
    private String recordId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 36)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "change_source", nullable = false, length = 20)
    private String changeSource = "UI";

    // Constructors, getters/setters
}
```

---

### E3: FieldHistoryService

**Purpose:** Records field-level changes and provides history queries.

**Files:**
- `com.emf.controlplane.repository.FieldHistoryRepository`
- `com.emf.controlplane.service.FieldHistoryService`

**Repository:**

```java
public interface FieldHistoryRepository extends JpaRepository<FieldHistory, String> {
    Page<FieldHistory> findByCollectionIdAndRecordIdOrderByChangedAtDesc(
        String collectionId, String recordId, Pageable pageable);

    Page<FieldHistory> findByCollectionIdAndRecordIdAndFieldNameOrderByChangedAtDesc(
        String collectionId, String recordId, String fieldName, Pageable pageable);

    Page<FieldHistory> findByCollectionIdAndFieldNameOrderByChangedAtDesc(
        String collectionId, String fieldName, Pageable pageable);

    Page<FieldHistory> findByChangedByOrderByChangedAtDesc(
        String userId, Pageable pageable);

    long countByCollectionIdAndRecordId(String collectionId, String recordId);
}
```

**Service methods:**

```java
@Service
public class FieldHistoryService {
    // Dependencies: FieldHistoryRepository, FieldRepository, ObjectMapper

    /**
     * Compares old and new record values, creates field_history entries
     * for each changed field that has track_history=true.
     *
     * Called from StorageAdapter after a successful update.
     */
    @Transactional
    public void recordChanges(String tenantId, String collectionId, String recordId,
                               Map<String, Object> oldRecord, Map<String, Object> newRecord,
                               String userId, String changeSource) {
        // 1. Load fields with track_history=true for this collection
        List<Field> trackedFields = fieldRepository
            .findByCollectionIdAndTrackHistoryTrueAndActiveTrue(collectionId);

        // 2. For each tracked field, compare old and new values
        for (Field field : trackedFields) {
            Object oldVal = oldRecord.get(field.getName());
            Object newVal = newRecord.get(field.getName());

            if (!Objects.equals(oldVal, newVal)) {
                FieldHistory history = new FieldHistory();
                history.setTenantId(tenantId);
                history.setCollectionId(collectionId);
                history.setRecordId(recordId);
                history.setFieldName(field.getName());
                history.setOldValue(serializeToJson(oldVal));
                history.setNewValue(serializeToJson(newVal));
                history.setChangedBy(userId);
                history.setChangedAt(Instant.now());
                history.setChangeSource(changeSource);
                fieldHistoryRepository.save(history);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<FieldHistory> getRecordHistory(String collectionId, String recordId,
                                                Pageable pageable)

    @Transactional(readOnly = true)
    public Page<FieldHistory> getFieldHistory(String collectionId, String recordId,
                                               String fieldName, Pageable pageable)

    @Transactional(readOnly = true)
    public Page<FieldHistory> getFieldHistoryAcrossRecords(String collectionId,
                                                            String fieldName, Pageable pageable)
    // View all changes to a specific field across all records (for audit)

    @Transactional(readOnly = true)
    public Page<FieldHistory> getUserHistory(String userId, Pageable pageable)
    // View all changes made by a specific user

    private String serializeToJson(Object value) {
        if (value == null) return null;
        return objectMapper.writeValueAsString(value);
    }
}
```

**Integration points:**
- E4 (storage adapter hook calls recordChanges)
- E6 (controller exposes history queries)

---

### E4: Storage Adapter History Hook

**Purpose:** Hook the `FieldHistoryService` into the storage adapter's `update()` method to automatically record field changes.

**File to modify:** `com.emf.runtime.storage.PhysicalTableStorageAdapter`

**Changes to `update()` method:**

```java
@Override
public Optional<Map<String, Object>> update(CollectionDefinition definition,
                                             String id, Map<String, Object> data) {
    // 1. Fetch existing record BEFORE update (for history comparison)
    Optional<Map<String, Object>> existingOpt = getById(definition, id);
    if (existingOpt.isEmpty()) return Optional.empty();
    Map<String, Object> oldRecord = existingOpt.get();

    // 2. Merge and validate (existing logic + validation rules from D5)
    ...

    // 3. Execute UPDATE
    ...

    // 4. Fetch updated record
    Optional<Map<String, Object>> updatedOpt = getById(definition, id);

    // 5. Record field history for tracked fields
    if (updatedOpt.isPresent() && fieldHistoryService != null) {
        fieldHistoryService.recordChanges(
            currentTenantId(),
            definition.name(),
            id,
            oldRecord,
            updatedOpt.get(),
            currentUserId(),
            currentChangeSource()  // "UI", "API", "WORKFLOW", etc.
        );
    }

    return updatedOpt;
}
```

**Acceptance criteria:**
- Field history recorded automatically on every update
- Only fields with `track_history=true` are tracked
- Old and new values serialized to JSON
- History recording does not affect update performance significantly (async option available)
- History recording failures logged but do not block the update

---

### E5: track_history Column Migration

**Purpose:** Add `track_history` boolean column to the `field` table so admins can enable/disable history tracking per field.

**Flyway file:** `V20__add_track_history_to_field.sql`

```sql
ALTER TABLE field ADD COLUMN track_history BOOLEAN DEFAULT false;

COMMENT ON COLUMN field.track_history IS
'When true, changes to this field are recorded in the field_history table.
 Default: false. Enable for fields that need audit trails.';
```

**Field entity addition:**

```java
// Add to Field.java entity
@Column(name = "track_history")
private boolean trackHistory = false;

public boolean isTrackHistory() { return trackHistory; }
public void setTrackHistory(boolean trackHistory) { this.trackHistory = trackHistory; }
```

**FieldService update:** Allow setting `trackHistory` in `AddFieldRequest` and `UpdateFieldRequest`.

**Acceptance criteria:**
- Admins can enable/disable history tracking per field
- New fields default to `track_history=false`
- Toggling track_history does not affect existing history records

---

### E6: Field History Controller

**Purpose:** REST endpoints for querying field history.

**File:** `com.emf.controlplane.controller.FieldHistoryController` (new)

**Base path:** `/control/collections/{collectionId}/records/{recordId}/history`

```java
@RestController
@SecurityRequirement(name = "bearer-jwt")
public class FieldHistoryController {

    @GetMapping("/control/collections/{collectionId}/records/{recordId}/history")
    public Page<FieldHistoryDto> getRecordHistory(
        @PathVariable String collectionId,
        @PathVariable String recordId,
        @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC)
        Pageable pageable)

    @GetMapping("/control/collections/{collectionId}/records/{recordId}/history/{fieldName}")
    public Page<FieldHistoryDto> getFieldHistory(
        @PathVariable String collectionId,
        @PathVariable String recordId,
        @PathVariable String fieldName,
        @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC)
        Pageable pageable)

    @GetMapping("/control/collections/{collectionId}/field-history/{fieldName}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('VIEW_ALL_DATA')")
    public Page<FieldHistoryDto> getFieldHistoryAcrossRecords(
        @PathVariable String collectionId,
        @PathVariable String fieldName,
        @PageableDefault(size = 50) Pageable pageable)
    // Admin-only: view all changes to a field across all records

    @GetMapping("/control/users/{userId}/field-history")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('VIEW_ALL_DATA')")
    public Page<FieldHistoryDto> getUserHistory(
        @PathVariable String userId,
        @PageableDefault(size = 50) Pageable pageable)
    // Admin-only: view all field changes made by a specific user
}
```

---

### E7: Field History UI

**Purpose:** Frontend components for viewing field history on record detail pages.

**Location:** Component within record detail pages, `emf-ui/app/src/components/FieldHistoryPanel.tsx` (new)

**Features:**
- "History" tab on record detail page showing chronological list of field changes
- Each entry shows: field name, old value → new value, changed by (user name), changed at (timestamp), source
- Filter by field name
- Filter by date range
- Color-coded values (red for old, green for new)
- Expandable rows for JSON/rich text values
- Admin view: link to field-level audit across all records

**Navigation:** Tab on record detail page. Also accessible from "Setup > Audit" for collection-wide field history.

---

### E8: Field History SDK Types

**Purpose:** TypeScript types for field history in the frontend SDK.

**File:** `emf-web/packages/sdk/src/admin/types.ts` (additions)

```typescript
export interface FieldHistoryEntry {
    id: string;
    collectionId: string;
    recordId: string;
    fieldName: string;
    oldValue: unknown;
    newValue: unknown;
    changedBy: string;
    changedByName?: string;  // resolved user name
    changedAt: string;
    changeSource: 'UI' | 'API' | 'WORKFLOW' | 'SYSTEM' | 'IMPORT';
}
```

**SDK client additions:**

```typescript
readonly fieldHistory = {
    getRecordHistory: async (collectionId: string, recordId: string,
                              page?: number, size?: number): Promise<Page<FieldHistoryEntry>> => { ... },
    getFieldHistory: async (collectionId: string, recordId: string,
                             fieldName: string, page?: number): Promise<Page<FieldHistoryEntry>> => { ... },
    getFieldHistoryAcrossRecords: async (collectionId: string, fieldName: string,
                                          page?: number): Promise<Page<FieldHistoryEntry>> => { ... },
    getUserHistory: async (userId: string, page?: number): Promise<Page<FieldHistoryEntry>> => { ... },
};
```

---

### E9: Setup Audit AOP Enhancement

**Purpose:** Phase 1 tasks F1/F2 create the `setup_audit_trail` table and `SetupAuditService`. This task enhances it with an AOP aspect that automatically captures before/after state for annotated service methods, eliminating manual `SetupAuditService.log()` calls.

**No new migration** -- uses the existing `setup_audit_trail` table from Phase 1 V13.

**Files:**
- `com.emf.controlplane.audit.SetupAudited` (new annotation)
- `com.emf.controlplane.audit.SetupAuditAspect` (new AOP aspect)

**Annotation:**

```java
package com.emf.controlplane.audit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SetupAudited {
    String section();            // e.g., "Collections", "Fields", "Picklists"
    String entityType();         // e.g., "Field", "ValidationRule", "RecordType"
    String action() default "";  // auto-detected if empty: "CREATED" for create*, "UPDATED" for update*, "DELETED" for delete*
}
```

**AOP Aspect:**

```java
package com.emf.controlplane.audit;

@Aspect
@Component
public class SetupAuditAspect {
    // Dependencies: SetupAuditService, ObjectMapper, TenantContextHolder

    @Around("@annotation(audited)")
    public Object auditSetupChange(ProceedingJoinPoint joinPoint, SetupAudited audited)
            throws Throwable {

        // 1. Detect action from method name if not specified
        String action = audited.action().isEmpty()
            ? detectAction(joinPoint.getSignature().getName())
            : audited.action();

        // 2. For UPDATE/DELETE: capture "before" state from first argument (entity ID)
        Object oldValue = null;
        if ("UPDATED".equals(action) || "DELETED".equals(action)) {
            oldValue = captureCurrentState(joinPoint, audited.entityType());
        }

        // 3. Execute the actual method
        Object result = joinPoint.proceed();

        // 4. Capture "after" state from return value
        Object newValue = ("DELETED".equals(action)) ? null : result;

        // 5. Extract entity ID and name from result or arguments
        String entityId = extractEntityId(result, joinPoint.getArgs());
        String entityName = extractEntityName(result, joinPoint.getArgs());

        // 6. Log to setup audit trail
        setupAuditService.log(
            action,
            audited.section(),
            audited.entityType(),
            entityId,
            entityName,
            oldValue,
            newValue
        );

        return result;
    }

    private String detectAction(String methodName) {
        if (methodName.startsWith("create") || methodName.startsWith("add")) return "CREATED";
        if (methodName.startsWith("update") || methodName.startsWith("set")) return "UPDATED";
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) return "DELETED";
        if (methodName.startsWith("activate")) return "ACTIVATED";
        if (methodName.startsWith("deactivate")) return "DEACTIVATED";
        return "MODIFIED";
    }
}
```

**Apply annotation to Phase 2 service methods:**

```java
// FieldService
@SetupAudited(section = "Fields", entityType = "Field")
public Field addField(String collectionId, AddFieldRequest request) { ... }

@SetupAudited(section = "Fields", entityType = "Field")
public Field updateField(String collectionId, String fieldId, UpdateFieldRequest request) { ... }

@SetupAudited(section = "Fields", entityType = "Field")
public void deleteField(String collectionId, String fieldId) { ... }

// PicklistService
@SetupAudited(section = "Picklists", entityType = "GlobalPicklist")
public GlobalPicklist createGlobalPicklist(...) { ... }

@SetupAudited(section = "Picklists", entityType = "PicklistValues")
public List<PicklistValue> setPicklistValues(...) { ... }

// ValidationRuleService
@SetupAudited(section = "Validation Rules", entityType = "ValidationRule")
public ValidationRule createRule(...) { ... }

// RecordTypeService
@SetupAudited(section = "Record Types", entityType = "RecordType")
public RecordType createRecordType(...) { ... }
```

**Acceptance criteria:**
- `@SetupAudited` annotation captures before/after state automatically
- Action auto-detected from method name prefix
- Old value captured before update/delete executes
- New value captured from method return value
- Audit entries include tenant ID and user ID from context
- AOP does not affect service method behavior or transactions

---

### E10: Setup Audit UI Enhancements

**Purpose:** Enhance the Phase 1 Setup Audit Trail UI (F5) with Phase 2 entity types and improved filtering.

**Location:** Modify existing `emf-ui/app/src/pages/SetupAuditTrail.tsx` (from Phase 1 F5)

**Enhancements:**
- New entity type filters: "Field", "GlobalPicklist", "PicklistValues", "ValidationRule", "RecordType", "RecordTypePicklist", "ProfileRecordType"
- New section filters: "Fields", "Picklists", "Validation Rules", "Record Types"
- Improved diff view for JSONB old/new values:
  - Side-by-side JSON diff with highlighted changes
  - For picklist value changes: show added/removed values in a list
  - For field type config changes: show config key-value diff
- Drill-down: click entity name to navigate to the entity's admin page
- Export enhancements: include all Phase 2 entity types in CSV/JSON export

---

## Completeness Review

### Verified Coverage

| Requirement | Tasks |
|-------------|-------|
| 24 field types with SQL mapping | A1-A2, A6 |
| Type-specific validation | A4-A5, A8 |
| Schema migration compatibility | A7 |
| Auto-number generation | A9 |
| Formula evaluation engine | A10 |
| Formula field query rewrite | A11 |
| Rollup summary computation | A12 |
| Encrypted field storage | A13 |
| Field type SDK + UI | A14 |
| Global picklist management | B1-B3, B5-B6, B8-B9 |
| Field-specific picklist values | B1, B3, B5-B6, B8-B9 |
| Dependent picklist mapping | B1, B4-B6, B8-B9 |
| Picklist validation on save | B7 |
| LOOKUP relationships with FK | C1-C3, C5, C7-C9 |
| MASTER_DETAIL with cascade delete | C1, C2, C4-C6, C8-C9 |
| FK enforcement in storage | C5 |
| IncludeResolver relationship support | C7 |
| Cross-field validation rules | D1-D6, D15-D16 |
| Formula-based error conditions | D3-D5 |
| Record types per collection | D7-D8, D11-D14, D16 |
| Record type picklist overrides | D7, D9, D11, D13-D14, D16 |
| Profile-to-record-type assignment | D7, D10-D11, D13-D14 |
| record_type_id on data tables | D12 |
| Field history tracking | E1-E4, E7-E8 |
| track_history per field | E5 |
| Field history UI | E7 |
| Setup audit AOP automation | E9 |
| Setup audit UI for Phase 2 entities | E10 |
| Frontend for every backend feature | A14, B8-B9, C8-C9, D14-D16, E7-E8, E10 |

### Migration Summary

| Migration | Stream | Purpose |
|-----------|--------|---------|
| V14 | A | Add field_type_config JSONB + auto_number_sequence_name to field |
| V15 | B | Create global_picklist, picklist_value, picklist_dependency tables |
| V16 | C | Add relationship_type, relationship_name, cascade_delete, reference_collection_id to field |
| V17 | D | Create validation_rule table |
| V18 | D | Create record_type, record_type_picklist, profile_record_type tables |
| V19 | E | Create field_history table |
| V20 | E | Add track_history column to field table |

### Key Files Modified (Existing)

| File | Changes |
|------|---------|
| `runtime-core/.../FieldType.java` | Add 16 new enum values + REFERENCE, ARRAY + helper methods |
| `runtime-core/.../ReferenceConfig.java` | Add relationshipType, relationshipName fields + new factories |
| `runtime-core/.../PhysicalTableStorageAdapter.java` | Extended mapFieldTypeToSql(), companion columns, FK creation, validation rule hook, field history hook |
| `runtime-core/.../SchemaMigrationEngine.java` | Extended TYPE_COMPATIBILITY for 24 types, companion column handling |
| `runtime-core/.../DefaultValidationEngine.java` | Picklist validation, new type checks for all 24 types |
| `runtime-core/.../FieldDefinition.java` | Static factories for new types, auto-number config fields |
| `control-plane/.../Field.java` | Add fieldTypeConfig, autoNumberSequenceName, relationship columns, trackHistory |
| `control-plane/.../FieldService.java` | TYPE_ALIASES mapping, FieldTypeValidator delegation, LOOKUP/MASTER_DETAIL handling |
| `gateway/.../IncludeResolver.java` | Relationship name resolution, control plane lookup |

### Key New Files (~45)

| Category | Files |
|----------|-------|
| **Entities** | GlobalPicklist, PicklistValue, PicklistDependency, ValidationRule, RecordType, RecordTypePicklist, ProfileRecordType, FieldHistory |
| **Repositories** | GlobalPicklistRepository, PicklistValueRepository, PicklistDependencyRepository, ValidationRuleRepository, RecordTypeRepository, RecordTypePicklistRepository, ProfileRecordTypeRepository, FieldHistoryRepository |
| **Services** | PicklistService, ValidationRuleService, RecordTypeService, FieldHistoryService, AutoNumberService, FieldEncryptionService, RollupSummaryService |
| **Formula Engine** | FormulaEvaluator, FormulaParser, FormulaAst, FormulaContext, FormulaFunction (interface), FormulaToSqlTranslator, FormulaException, 25+ built-in function implementations |
| **Validators** | FieldTypeValidator (interface), FieldTypeValidatorRegistry, PicklistFieldValidator, AutoNumberFieldValidator, CurrencyFieldValidator, FormulaFieldValidator, RollupSummaryFieldValidator, EmailFieldValidator, PhoneFieldValidator, UrlFieldValidator, EncryptedFieldValidator, GeolocationFieldValidator, PercentFieldValidator, ExternalIdFieldValidator |
| **Validation** | ValidationRuleEvaluator, PicklistValidator, RecordValidationException |
| **AOP** | SetupAudited (annotation), SetupAuditAspect |
| **Controllers** | PicklistController, ValidationRuleController, RecordTypeController, FieldHistoryController |
| **UI Pages** | PicklistManagement, ValidationRuleEditor, RecordTypeManagement, FieldHistoryPanel |
| **Migrations** | V14–V20 (7 migration files) |

### Potential Gaps Identified and Addressed

1. **FieldType enum vs control-plane strings** -- The control-plane uses string-based types (`"string"`, `"number"`, etc.) while runtime-core uses the `FieldType` enum. Task A2 addresses this with a `TYPE_ALIASES` mapping that accepts both legacy and new type names, storing the canonical enum name.

2. **FORMULA/ROLLUP_SUMMARY performance on list queries** -- Computing formulas and rollup summaries per-row in list queries could be slow. Task A11 mitigates this by translating simple formulas to SQL. Task A12 notes batch optimization as a follow-up. Pagination limits the impact.

3. **ENCRYPTED field searchability** -- Encrypted fields cannot be searched or indexed. This is by design (security). The UI should indicate this limitation when enabling ENCRYPTED type.

4. **CURRENCY multi-currency support** -- The dual-column approach (amount + currency_code) supports multi-currency per record. Currency conversion is out of scope for Phase 2 (Phase 5 feature).

5. **GEOLOCATION spatial queries** -- Phase 2 stores lat/lng as DOUBLE PRECISION columns. Spatial indexing (PostGIS) and distance queries are Phase 5 features. Basic storage and display is sufficient.

6. **Record type on existing records** -- When record types are first enabled on a collection, existing records have `record_type_id = null`. The service should handle this gracefully (null means "no record type assigned" rather than "default record type").

7. **Validation rule ordering** -- Multiple validation rules evaluate in name-alphabetical order. If ordering becomes important, add a `sort_order` column in a follow-up migration.

8. **Field history retention** -- No automatic cleanup of old field history records. A retention policy (e.g., keep 2 years) can be added as a governor limit in Phase 3.

9. **Cascade delete depth limit** -- MASTER_DETAIL cascades are handled by PostgreSQL's ON DELETE CASCADE. Deep hierarchies (A → B → C → D) cascade automatically. The 2-MASTER_DETAIL-per-collection limit in C4 prevents excessively deep chains.

10. **FormulaEvaluator security** -- Formula expressions are admin-configured, not user-input. However, the parser should prevent resource exhaustion (max expression length, max recursion depth, timeout on evaluation).

### Verification Checklist

After implementation:
- [ ] All 24 field types can be created via API and rendered in UI
- [ ] Picklist values validated on record create/update
- [ ] LOOKUP fields create FK ON DELETE SET NULL; MASTER_DETAIL creates FK ON DELETE CASCADE NOT NULL
- [ ] Validation rules with cross-field formulas fire on create/update
- [ ] Record types filter available picklist values per type
- [ ] Field history tracks changes to history-enabled fields
- [ ] Setup audit AOP captures all config changes automatically
- [ ] All existing tests still pass (new types don't break existing flows)
- [ ] Formula expressions evaluate correctly for all built-in functions
- [ ] Auto-number generates unique sequential values under concurrent load
- [ ] Encrypted fields round-trip correctly with per-tenant keys
- [ ] Currency fields store amount + currency code in dual columns
- [ ] GEOLOCATION fields store latitude + longitude
- [ ] EXTERNAL_ID fields enforce uniqueness via database index
- [ ] SDK types cover all new entities and operations
- [ ] Migrations V14–V20 run cleanly in sequence on fresh and existing databases
