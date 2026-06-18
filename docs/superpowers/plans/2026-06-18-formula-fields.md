# Formula Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `FORMULA` field type whose value is derived from an expression, materialized to a real DB column for Superset analytics.

**Architecture:** The formula engine (parser, evaluator, AST, 15+ functions) already exists and is used by validation rules and workflow filters. This wires it to a new field type via three paths: (1) `SchemaMigrationEngine` creates a typed DB column, (2) a new wildcard `BeforeSaveHook` evaluates expressions before every write, (3) `DefaultQueryEngine.computeVirtualFields()` re-evaluates on every read so the Kelta UI always shows a live value. A background job (`FormulaRecomputeService`) bulk-updates the DB column when a formula field is created or its expression changes — this keeps Superset's direct-DB queries current.

**Tech Stack:** Java 25 / Spring Boot 4.x, `FormulaEvaluator` (runtime-core), `BeforeSaveHook` wildcard mechanism, `JdbcTemplate` (JDBC batch updates), `@Async("applicationTaskExecutor")` (already configured in `EmailConfig`), React 19 / react-hook-form / Zod.

## Global Constraints

- All Java in runtime-core follows existing package structure (`io.kelta.runtime.*`)
- All Java in kelta-worker follows existing package structure (`io.kelta.worker.*`)
- New hooks go in `kelta-worker/src/main/java/io/kelta/worker/listener/`
- New services go in `kelta-worker/src/main/java/io/kelta/worker/service/`
- Hook registration goes in `FlowConfig.java` as a `@Bean` method
- `returnType` values are uppercase strings: `"TEXT"`, `"NUMBER"`, `"BOOLEAN"`
- Formula column names use `PhysicalTableStorageAdapter.toSnakeCase(fieldName)` (method is `public static`)
- No direct commits to `main` — all changes go on a branch

---

## File Map

**Modified:**
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/model/FieldType.java`
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/workflow/BeforeSaveHook.java`
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/workflow/BeforeSaveHookRegistry.java`
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/storage/SchemaMigrationEngine.java`
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/storage/PhysicalTableStorageAdapter.java`
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/query/DefaultQueryEngine.java`
- `kelta-platform/runtime/runtime-core/src/test/java/io/kelta/runtime/storage/SchemaMigrationEngineTest.java`
- `kelta-worker/src/main/java/io/kelta/worker/listener/FieldConfigEventPublisher.java`
- `kelta-worker/src/main/java/io/kelta/worker/config/FlowConfig.java`
- `kelta-ui/app/src/components/FieldEditor/FieldEditor.tsx`

**Created:**
- `kelta-worker/src/main/java/io/kelta/worker/listener/FormulaComputeHook.java`
- `kelta-worker/src/main/java/io/kelta/worker/service/FormulaRecomputeService.java`
- `kelta-worker/src/test/java/io/kelta/worker/listener/FormulaComputeHookTest.java`

---

### Task 1: Schema — FORMULA gets a physical column

**Files:**
- Modify: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/model/FieldType.java`
- Modify: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/storage/SchemaMigrationEngine.java:630-660`
- Modify: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/storage/PhysicalTableStorageAdapter.java:820-835`
- Test: `kelta-platform/runtime/runtime-core/src/test/java/io/kelta/runtime/storage/SchemaMigrationEngineTest.java`

**Interfaces:**
- Produces: `FieldType.FORMULA.hasPhysicalColumn()` returns `true`; `SchemaMigrationEngine.mapFieldTypeToSql(FORMULA, field)` returns `"TEXT"` / `"NUMERIC"` / `"BOOLEAN"` based on `field.fieldTypeConfig().get("returnType")`

- [ ] **Step 1: Write the failing test in `SchemaMigrationEngineTest.java`**

Find the inner class `ColumnTypeMappingTest` (contains `@Test` methods for `TEXT`, `RICH_TEXT`, etc.) and add these three tests:

```java
@Test
@DisplayName("FORMULA with returnType=TEXT maps to TEXT column")
void formulaTextMapsToTextColumn() {
    FieldDefinition field = new FieldDefinition("label", FieldType.FORMULA,
            null, null, null, null, Map.of("returnType", "TEXT", "expression", "name"), null);
    assertEquals("TEXT", migrationEngine.mapFieldTypeToSql(FieldType.FORMULA, field));
}

@Test
@DisplayName("FORMULA with returnType=NUMBER maps to NUMERIC column")
void formulaNumberMapsToNumericColumn() {
    FieldDefinition field = new FieldDefinition("total", FieldType.FORMULA,
            null, null, null, null, Map.of("returnType", "NUMBER", "expression", "price * qty"), null);
    assertEquals("NUMERIC", migrationEngine.mapFieldTypeToSql(FieldType.FORMULA, field));
}

@Test
@DisplayName("FORMULA with returnType=BOOLEAN maps to BOOLEAN column")
void formulaBooleanMapsToBooleanColumn() {
    FieldDefinition field = new FieldDefinition("isLarge", FieldType.FORMULA,
            null, null, null, null, Map.of("returnType", "BOOLEAN", "expression", "amount > 100"), null);
    assertEquals("BOOLEAN", migrationEngine.mapFieldTypeToSql(FieldType.FORMULA, field));
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -f kelta-platform/pom.xml -Dtest="SchemaMigrationEngineTest#formulaTextMapsToTextColumn+formulaNumberMapsToNumericColumn+formulaBooleanMapsToBooleanColumn" 2>&1 | tail -20
```

Expected: FAIL — `expected: <TEXT> but was: <null>`

- [ ] **Step 3: Fix `FieldType.java` — FORMULA now has a physical column**

In `FieldType.java`, find `hasPhysicalColumn()` (~line 124):

```java
// Before:
public boolean hasPhysicalColumn() {
    return this != FORMULA && this != ROLLUP_SUMMARY;
}

// After:
public boolean hasPhysicalColumn() {
    return this != ROLLUP_SUMMARY;
}
```

Also update the Javadoc comment on that method from "FORMULA and ROLLUP_SUMMARY are computed on read" to "ROLLUP_SUMMARY is computed on read; FORMULA fields have a materialized DB column".

- [ ] **Step 4: Fix `SchemaMigrationEngine.java` — return typed SQL for FORMULA**

In `mapFieldTypeToSql()` (~line 657), split the combined case:

```java
// Before:
case FORMULA, ROLLUP_SUMMARY -> null;

// After:
case ROLLUP_SUMMARY -> null;
case FORMULA -> {
    Map<String, Object> cfg = field.fieldTypeConfig();
    String returnType = cfg != null ? (String) cfg.get("returnType") : null;
    yield switch (returnType != null ? returnType.toUpperCase() : "TEXT") {
        case "NUMBER" -> "NUMERIC";
        case "BOOLEAN" -> "BOOLEAN";
        default -> "TEXT";
    };
}
```

- [ ] **Step 5: Fix `PhysicalTableStorageAdapter.java` — same typed SQL for FORMULA**

In `columnDefinitionSql()` (~line 826), make the same split:

```java
// Before:
case FORMULA, ROLLUP_SUMMARY -> null;

// After:
case ROLLUP_SUMMARY -> null;
case FORMULA -> {
    Map<String, Object> cfg = field.fieldTypeConfig();
    String returnType = cfg != null ? (String) cfg.get("returnType") : null;
    yield switch (returnType != null ? returnType.toUpperCase() : "TEXT") {
        case "NUMBER" -> "NUMERIC";
        case "BOOLEAN" -> "BOOLEAN";
        default -> "TEXT";
    };
}
```

- [ ] **Step 6: Run all three tests to verify they pass**

```bash
mvn test -f kelta-platform/pom.xml -Dtest="SchemaMigrationEngineTest#formulaTextMapsToTextColumn+formulaNumberMapsToNumericColumn+formulaBooleanMapsToBooleanColumn" 2>&1 | tail -20
```

Expected: 3 tests PASS.

- [ ] **Step 7: Run the full SchemaMigrationEngineTest suite**

```bash
mvn test -f kelta-platform/pom.xml -Dtest="SchemaMigrationEngineTest" 2>&1 | tail -30
```

Expected: all tests PASS. (The `hasPhysicalColumn()` change might affect tests that build tables with FORMULA fields — check and fix any that break.)

- [ ] **Step 8: Commit**

```bash
git add kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/model/FieldType.java \
        kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/storage/SchemaMigrationEngine.java \
        kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/storage/PhysicalTableStorageAdapter.java \
        kelta-platform/runtime/runtime-core/src/test/java/io/kelta/runtime/storage/SchemaMigrationEngineTest.java
git commit -m "$(cat <<'EOF'
feat(runtime): FORMULA fields get a materialized DB column

Co-Authored-By: Anna Klinker <anna@rzware.com>
EOF
)"
```

---

### Task 2: Hook contract — collection-name-aware beforeCreate/beforeUpdate

**Files:**
- Modify: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/workflow/BeforeSaveHook.java`
- Modify: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/workflow/BeforeSaveHookRegistry.java`

**Interfaces:**
- Produces: `BeforeSaveHook` has two new default methods:
  - `beforeCreate(String collectionName, Map<String, Object> record, String tenantId) → BeforeSaveResult`
  - `beforeUpdate(String collectionName, String id, Map<String, Object> record, Map<String, Object> previous, String tenantId) → BeforeSaveResult`
- Both default to delegating to the existing `beforeCreate(record, tenantId)` / `beforeUpdate(id, record, previous, tenantId)`, so all existing hooks are unaffected.
- `BeforeSaveHookRegistry.evaluateBeforeCreate()` and `evaluateBeforeUpdate()` now call the new variants.

- [ ] **Step 1: Add the two new default methods to `BeforeSaveHook.java`**

After the existing `beforeUpdate(String id, ...)` method (~line 76), insert:

```java
/**
 * Called before a new record is created, with the collection name provided.
 * This variant is used by wildcard hooks that need to know which collection
 * triggered the hook. The default implementation delegates to
 * {@link #beforeCreate(Map, String)}.
 *
 * @param collectionName the collection the record is being created in
 * @param record the record data being created
 * @param tenantId the tenant ID
 * @return the result
 */
default BeforeSaveResult beforeCreate(String collectionName, Map<String, Object> record, String tenantId) {
    return beforeCreate(record, tenantId);
}

/**
 * Called before an existing record is updated, with the collection name provided.
 * This variant is used by wildcard hooks that need to know which collection
 * triggered the hook. The default implementation delegates to
 * {@link #beforeUpdate(String, Map, Map, String)}.
 *
 * @param collectionName the collection the record belongs to
 * @param id the record ID
 * @param record the update data
 * @param previous the previous record data
 * @param tenantId the tenant ID
 * @return the result
 */
default BeforeSaveResult beforeUpdate(String collectionName, String id, Map<String, Object> record,
                                       Map<String, Object> previous, String tenantId) {
    return beforeUpdate(id, record, previous, tenantId);
}
```

- [ ] **Step 2: Update `BeforeSaveHookRegistry.java` to call the new variants**

In `evaluateBeforeCreate()` (~line 155), change the hook call:
```java
// Before:
BeforeSaveResult result = hook.beforeCreate(record, tenantId);

// After:
BeforeSaveResult result = hook.beforeCreate(collectionName, record, tenantId);
```

In `evaluateBeforeUpdate()` (~line 190), change the hook call:
```java
// Before:
BeforeSaveResult result = hook.beforeUpdate(id, record, previous, tenantId);

// After:
BeforeSaveResult result = hook.beforeUpdate(collectionName, id, record, previous, tenantId);
```

- [ ] **Step 3: Compile to confirm no regressions**

```bash
mvn compile -f kelta-platform/pom.xml 2>&1 | tail -20
```

Expected: BUILD SUCCESS. (All existing hooks default-delegate, so nothing breaks.)

- [ ] **Step 4: Run the runtime-core test suite**

```bash
mvn test -f kelta-platform/pom.xml -pl runtime/runtime-core 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/workflow/BeforeSaveHook.java \
        kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/workflow/BeforeSaveHookRegistry.java
git commit -m "$(cat <<'EOF'
feat(runtime): add collection-name-aware beforeCreate/beforeUpdate to BeforeSaveHook

Enables wildcard hooks to know which collection triggered them without
breaking any existing hook implementations.

Co-Authored-By: Anna Klinker <anna@rzware.com>
EOF
)"
```

---

### Task 3: FormulaComputeHook — evaluate formulas on every write

**Files:**
- Create: `kelta-worker/src/main/java/io/kelta/worker/listener/FormulaComputeHook.java`
- Modify: `kelta-worker/src/main/java/io/kelta/worker/config/FlowConfig.java`
- Test: `kelta-worker/src/test/java/io/kelta/worker/listener/FormulaComputeHookTest.java`

**Interfaces:**
- Consumes: `CollectionRegistry.get(String collectionName) → CollectionDefinition`, `FormulaEvaluator.evaluate(String expression, Map<String, Object> context) → Object`, `BeforeSaveResult.withFieldUpdates(Map)`, `FieldDefinition.fieldTypeConfig() → Map<String, Object>`
- Produces: A wildcard `BeforeSaveHook` that injects computed formula field values before every create/update

- [ ] **Step 1: Write the test file**

Create `kelta-worker/src/test/java/io/kelta/worker/listener/FormulaComputeHookTest.java`:

```java
package io.kelta.worker.listener;

import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FormulaComputeHookTest {

    private CollectionRegistry collectionRegistry;
    private FormulaEvaluator formulaEvaluator;
    private FormulaComputeHook hook;

    @BeforeEach
    void setUp() {
        collectionRegistry = mock(CollectionRegistry.class);
        formulaEvaluator = mock(FormulaEvaluator.class);
        hook = new FormulaComputeHook(collectionRegistry, formulaEvaluator);
    }

    @Test
    @DisplayName("getCollectionName returns wildcard")
    void wildcardCollection() {
        assertEquals("*", hook.getCollectionName());
    }

    @Test
    @DisplayName("returns ok when collection has no formula fields")
    void noFormulaFields() {
        FieldDefinition textField = new FieldDefinition("name", FieldType.TEXT,
                null, null, null, null, null, null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("orders").fields(List.of(textField)).build();
        when(collectionRegistry.get("orders")).thenReturn(def);

        BeforeSaveResult result = hook.beforeCreate("orders", Map.of("name", "Acme"), "default");

        assertTrue(result.isSuccess());
        assertFalse(result.hasFieldUpdates());
    }

    @Test
    @DisplayName("evaluates TEXT formula and injects result")
    void evaluatesTextFormula() {
        FieldDefinition formula = new FieldDefinition("fullName", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "firstName & \" \" & lastName", "returnType", "TEXT"), null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("contacts").fields(List.of(formula)).build();
        when(collectionRegistry.get("contacts")).thenReturn(def);
        when(formulaEvaluator.evaluate(eq("firstName & \" \" & lastName"), any())).thenReturn("Jane Doe");

        BeforeSaveResult result = hook.beforeCreate("contacts",
                new HashMap<>(Map.of("firstName", "Jane", "lastName", "Doe")), "default");

        assertTrue(result.isSuccess());
        assertTrue(result.hasFieldUpdates());
        assertEquals("Jane Doe", result.getFieldUpdates().get("fullName"));
    }

    @Test
    @DisplayName("injects #ERROR for TEXT formula that throws")
    void textFormulaErrorInjectsHashError() {
        FieldDefinition formula = new FieldDefinition("computed", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "bad expression", "returnType", "TEXT"), null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("items").fields(List.of(formula)).build();
        when(collectionRegistry.get("items")).thenReturn(def);
        when(formulaEvaluator.evaluate(any(), any()))
                .thenThrow(new RuntimeException("parse error"));

        BeforeSaveResult result = hook.beforeCreate("items",
                new HashMap<>(Map.of("name", "x")), "default");

        assertTrue(result.isSuccess());
        assertEquals("#ERROR", result.getFieldUpdates().get("computed"));
    }

    @Test
    @DisplayName("injects null for NUMBER formula that throws")
    void numberFormulaErrorInjectsNull() {
        FieldDefinition formula = new FieldDefinition("total", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "price * qty", "returnType", "NUMBER"), null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("orders").fields(List.of(formula)).build();
        when(collectionRegistry.get("orders")).thenReturn(def);
        when(formulaEvaluator.evaluate(any(), any()))
                .thenThrow(new RuntimeException("null ref"));

        BeforeSaveResult result = hook.beforeCreate("orders",
                new HashMap<>(Map.of("price", 10)), "default");

        assertTrue(result.isSuccess());
        assertNull(result.getFieldUpdates().get("total"));
    }

    @Test
    @DisplayName("topological sort evaluates dependency before dependent formula")
    void topologicalOrderRespected() {
        // base = price * 2; doubled = base * 2 (depends on base)
        FieldDefinition base = new FieldDefinition("base", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "price * 2", "returnType", "NUMBER"), null);
        FieldDefinition doubled = new FieldDefinition("doubled", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "base * 2", "returnType", "NUMBER"), null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("items").fields(List.of(doubled, base)).build(); // doubled listed first
        when(collectionRegistry.get("items")).thenReturn(def);
        when(formulaEvaluator.evaluate(eq("price * 2"), argThat(ctx -> ctx.containsKey("price"))))
                .thenReturn(20.0);
        when(formulaEvaluator.evaluate(eq("base * 2"), argThat(ctx -> ctx.containsKey("base"))))
                .thenReturn(40.0);

        BeforeSaveResult result = hook.beforeCreate("items",
                new HashMap<>(Map.of("price", 10)), "default");

        assertEquals(20.0, result.getFieldUpdates().get("base"));
        assertEquals(40.0, result.getFieldUpdates().get("doubled"));
    }

    @Test
    @DisplayName("circular references produce error values and log a warning")
    void circularReferencesProduceErrors() {
        // a references b, b references a
        FieldDefinition a = new FieldDefinition("a", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "b + 1", "returnType", "NUMBER"), null);
        FieldDefinition b = new FieldDefinition("b", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "a + 1", "returnType", "NUMBER"), null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("cycle").fields(List.of(a, b)).build();
        when(collectionRegistry.get("cycle")).thenReturn(def);

        BeforeSaveResult result = hook.beforeCreate("cycle",
                new HashMap<>(Map.of("x", 1)), "default");

        assertTrue(result.isSuccess());
        assertNull(result.getFieldUpdates().get("a"), "NUMBER cycle field should be null");
        assertNull(result.getFieldUpdates().get("b"), "NUMBER cycle field should be null");
        verify(formulaEvaluator, never()).evaluate(any(), any());
    }

    @Test
    @DisplayName("beforeUpdate merges previous and new values before evaluating")
    void beforeUpdateMergesPreviousAndNew() {
        FieldDefinition formula = new FieldDefinition("total", FieldType.FORMULA,
                null, null, null, null,
                Map.of("expression", "price * qty", "returnType", "NUMBER"), null);
        CollectionDefinition def = CollectionDefinition.builder()
                .name("orders").fields(List.of(formula)).build();
        when(collectionRegistry.get("orders")).thenReturn(def);
        when(formulaEvaluator.evaluate(eq("price * qty"), argThat(ctx ->
                ctx.get("price").equals(20) && ctx.get("qty").equals(5))))
                .thenReturn(100.0);

        Map<String, Object> previous = new HashMap<>(Map.of("price", 10, "qty", 5));
        Map<String, Object> record = new HashMap<>(Map.of("price", 20)); // only price changed
        BeforeSaveResult result = hook.beforeUpdate("orders", "rec-1", record, previous, "default");

        assertEquals(100.0, result.getFieldUpdates().get("total"));
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails with class-not-found**

```bash
mvn test -f kelta-worker/pom.xml -Dtest="FormulaComputeHookTest" 2>&1 | tail -15
```

Expected: FAIL — `ClassNotFoundException: FormulaComputeHook`

- [ ] **Step 3: Create `FormulaComputeHook.java`**

Create `kelta-worker/src/main/java/io/kelta/worker/listener/FormulaComputeHook.java`:

```java
package io.kelta.worker.listener;

import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Wildcard BeforeSaveHook that evaluates FORMULA fields before every record
 * create and update. Handles topological ordering (formula A depends on formula B
 * → B evaluates first) and marks cyclic fields with an error value.
 */
public class FormulaComputeHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(FormulaComputeHook.class);

    private final CollectionRegistry collectionRegistry;
    private final FormulaEvaluator formulaEvaluator;

    public FormulaComputeHook(CollectionRegistry collectionRegistry, FormulaEvaluator formulaEvaluator) {
        this.collectionRegistry = collectionRegistry;
        this.formulaEvaluator = formulaEvaluator;
    }

    @Override
    public String getCollectionName() {
        return "*";
    }

    @Override
    public BeforeSaveResult beforeCreate(String collectionName, Map<String, Object> record, String tenantId) {
        return computeFormulas(collectionName, record, record);
    }

    @Override
    public BeforeSaveResult beforeUpdate(String collectionName, String id, Map<String, Object> record,
                                          Map<String, Object> previous, String tenantId) {
        Map<String, Object> context = new HashMap<>(previous);
        context.putAll(record); // incoming values take precedence
        return computeFormulas(collectionName, context, record);
    }

    private BeforeSaveResult computeFormulas(String collectionName, Map<String, Object> context,
                                              Map<String, Object> incomingRecord) {
        CollectionDefinition def = collectionRegistry.get(collectionName);
        if (def == null) return BeforeSaveResult.ok();

        List<FieldDefinition> formulaFields = def.fields().stream()
                .filter(f -> f.type() == FieldType.FORMULA)
                .toList();
        if (formulaFields.isEmpty()) return BeforeSaveResult.ok();

        SortResult sorted = topologicalSort(formulaFields);
        Map<String, Object> updates = new LinkedHashMap<>();

        for (FieldDefinition field : sorted.ordered()) {
            Map<String, Object> cfg = field.fieldTypeConfig();
            if (cfg == null) continue;
            String expression = (String) cfg.get("expression");
            if (expression == null) continue;
            String returnType = (String) cfg.getOrDefault("returnType", "TEXT");

            if (sorted.cyclicNames().contains(field.name())) {
                log.warn("Circular formula reference detected for field '{}' on '{}' — injecting error value",
                        field.name(), collectionName);
                updates.put(field.name(), errorValue(returnType));
                continue;
            }

            Map<String, Object> evalContext = new HashMap<>(context);
            evalContext.putAll(updates); // use freshly computed values from earlier fields

            try {
                Object value = formulaEvaluator.evaluate(expression, evalContext);
                updates.put(field.name(), value);
            } catch (Exception e) {
                log.warn("Formula evaluation error for field '{}' on '{}': {}",
                        field.name(), collectionName, e.getMessage());
                updates.put(field.name(), errorValue(returnType));
            }
        }

        return updates.isEmpty() ? BeforeSaveResult.ok() : BeforeSaveResult.withFieldUpdates(updates);
    }

    private Object errorValue(String returnType) {
        return ("NUMBER".equalsIgnoreCase(returnType) || "BOOLEAN".equalsIgnoreCase(returnType))
                ? null : "#ERROR";
    }

    private record SortResult(List<FieldDefinition> ordered, Set<String> cyclicNames) {}

    private SortResult topologicalSort(List<FieldDefinition> formulaFields) {
        Map<String, FieldDefinition> byName = new LinkedHashMap<>();
        formulaFields.forEach(f -> byName.put(f.name(), f));

        Map<String, Set<String>> inDeps = new HashMap<>();
        Map<String, Set<String>> rdeps = new HashMap<>();
        formulaFields.forEach(f -> {
            inDeps.put(f.name(), new HashSet<>());
            rdeps.put(f.name(), new HashSet<>());
        });

        for (FieldDefinition f : formulaFields) {
            Map<String, Object> cfg = f.fieldTypeConfig();
            if (cfg == null) continue;
            String expr = (String) cfg.get("expression");
            if (expr == null) continue;
            for (FieldDefinition dep : formulaFields) {
                if (!dep.name().equals(f.name()) && containsFieldRef(expr, dep.name())) {
                    inDeps.get(f.name()).add(dep.name());
                    rdeps.get(dep.name()).add(f.name());
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        formulaFields.forEach(f -> { if (inDeps.get(f.name()).isEmpty()) queue.add(f.name()); });

        List<FieldDefinition> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            ordered.add(byName.get(name));
            new HashSet<>(rdeps.get(name)).forEach(rdep -> {
                inDeps.get(rdep).remove(name);
                if (inDeps.get(rdep).isEmpty()) queue.add(rdep);
            });
        }

        Set<String> cyclicNames = formulaFields.stream()
                .map(FieldDefinition::name)
                .filter(name -> ordered.stream().noneMatch(f -> f.name().equals(name)))
                .collect(Collectors.toSet());

        // Append cyclic fields after non-cyclic so they are included in the result list
        // and can be assigned error values by the caller.
        cyclicNames.forEach(name -> ordered.add(byName.get(name)));

        return new SortResult(ordered, cyclicNames);
    }

    private boolean containsFieldRef(String expression, String fieldName) {
        return Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b").matcher(expression).find();
    }
}
```

- [ ] **Step 4: Register the hook in `FlowConfig.java`**

Add a `@Bean` method after the existing `fieldConfigEventPublisher` bean (~line 310):

```java
@Bean
public FormulaComputeHook formulaComputeHook(BeforeSaveHookRegistry hookRegistry,
                                              CollectionRegistry collectionRegistry,
                                              FormulaEvaluator formulaEvaluator) {
    FormulaComputeHook hook = new FormulaComputeHook(collectionRegistry, formulaEvaluator);
    hookRegistry.register(hook);
    return hook;
}
```

Also add the import at the top of `FlowConfig.java`:
```java
import io.kelta.worker.listener.FormulaComputeHook;
```

- [ ] **Step 5: Run the tests**

```bash
mvn test -f kelta-worker/pom.xml -Dtest="FormulaComputeHookTest" 2>&1 | tail -30
```

Expected: all 7 tests PASS.

- [ ] **Step 6: Run the full kelta-worker test suite**

```bash
mvn test -f kelta-worker/pom.xml 2>&1 | tail -30
```

Expected: all tests PASS. (The hook registration in FlowConfig is tested via Spring context tests if any exist.)

- [ ] **Step 7: Commit**

```bash
git add kelta-worker/src/main/java/io/kelta/worker/listener/FormulaComputeHook.java \
        kelta-worker/src/main/java/io/kelta/worker/config/FlowConfig.java \
        kelta-worker/src/test/java/io/kelta/worker/listener/FormulaComputeHookTest.java
git commit -m "$(cat <<'EOF'
feat(worker): FormulaComputeHook evaluates formula fields before every write

Topological sort handles inter-formula dependencies. Circular references
and evaluation errors inject #ERROR (TEXT) or null (NUMBER/BOOLEAN).

Co-Authored-By: Anna Klinker <anna@rzware.com>
EOF
)"
```

---

### Task 4: Read path — wire formula evaluation in DefaultQueryEngine

**Files:**
- Modify: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/query/DefaultQueryEngine.java:763-787`

**Interfaces:**
- Consumes: `FormulaEvaluator.evaluate(String, Map<String, Object>) → Object`, `FieldDefinition.fieldTypeConfig() → Map<String, Object>`
- Produces: Every record returned by `DefaultQueryEngine` has live formula values computed at read time (stored column value is overwritten by the live evaluation result)

- [ ] **Step 1: Replace the debug stub in `computeVirtualFields()`**

Find the FORMULA block inside `computeVirtualFields()` (~line 770):

```java
// Before:
if (field.type() == FieldType.FORMULA && formulaEvaluator != null) {
    // FORMULA wiring tracked separately; same fieldTypeConfig path will host
    // the expression once the formula DSL stabilizes.
    logger.debug("Skipping formula field '{}' — config not available on FieldDefinition", field.name());
}

// After:
if (field.type() == FieldType.FORMULA && formulaEvaluator != null) {
    Map<String, Object> cfg = field.fieldTypeConfig();
    if (cfg == null) continue;
    String expression = (String) cfg.get("expression");
    if (expression == null) continue;
    String returnType = (String) cfg.getOrDefault("returnType", "TEXT");
    try {
        Object value = formulaEvaluator.evaluate(expression, record);
        record.put(field.name(), value);
    } catch (Exception e) {
        logger.warn("Formula read-path error for field '{}': {}", field.name(), e.getMessage());
        record.put(field.name(),
                ("NUMBER".equalsIgnoreCase(returnType) || "BOOLEAN".equalsIgnoreCase(returnType))
                        ? null : "#ERROR");
    }
}
```

- [ ] **Step 2: Compile the runtime-core module**

```bash
mvn compile -f kelta-platform/pom.xml -pl runtime/runtime-core 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run the DefaultQueryEngineTest suite**

```bash
mvn test -f kelta-platform/pom.xml -Dtest="DefaultQueryEngineTest" 2>&1 | tail -30
```

Expected: all tests PASS. (If any existing test checks that FORMULA fields are skipped, update those assertions — the stub is now gone.)

- [ ] **Step 4: Commit**

```bash
git add kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/query/DefaultQueryEngine.java
git commit -m "$(cat <<'EOF'
feat(runtime): wire formula evaluation in DefaultQueryEngine read path

Formula fields are now computed live on every read, ensuring Kelta UI
always shows the correct value regardless of the DB column state.

Co-Authored-By: Anna Klinker <anna@rzware.com>
EOF
)"
```

---

### Task 5: Background recompute — bulk update DB column when expression changes

**Files:**
- Create: `kelta-worker/src/main/java/io/kelta/worker/service/FormulaRecomputeService.java`
- Modify: `kelta-worker/src/main/java/io/kelta/worker/listener/FieldConfigEventPublisher.java`
- Modify: `kelta-worker/src/main/java/io/kelta/worker/config/FlowConfig.java`

**Interfaces:**
- Consumes: `JdbcTemplate`, `CollectionRegistry.get(String) → CollectionDefinition`, `FormulaEvaluator.evaluate(String, Map) → Object`, `PhysicalTableStorageAdapter.toSnakeCase(String) → String`, `TableRef.tenantSchema(slug, table).toSql()`, `@Async("applicationTaskExecutor")` (already configured in `EmailConfig`)
- Produces: `FormulaRecomputeService.recompute(collectionName, fieldName, expression, returnType, tenantId)` pages through all records in batches of 500 and issues batch UPDATEs against the physical column

- [ ] **Step 1: Create `FormulaRecomputeService.java`**

Create `kelta-worker/src/main/java/io/kelta/worker/service/FormulaRecomputeService.java`:

```java
package io.kelta.worker.service;

import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.PhysicalTableStorageAdapter;
import io.kelta.runtime.storage.TableRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bulk-recomputes a formula field's DB column for all records in a collection.
 * Runs asynchronously so the field-save API returns immediately. The Kelta UI
 * is unaffected (read path always evaluates live); this job keeps Superset's
 * direct-DB column current.
 */
@Service
public class FormulaRecomputeService {

    private static final Logger log = LoggerFactory.getLogger(FormulaRecomputeService.class);
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final CollectionRegistry collectionRegistry;
    private final FormulaEvaluator formulaEvaluator;

    public FormulaRecomputeService(JdbcTemplate jdbcTemplate,
                                    CollectionRegistry collectionRegistry,
                                    FormulaEvaluator formulaEvaluator) {
        this.jdbcTemplate = jdbcTemplate;
        this.collectionRegistry = collectionRegistry;
        this.formulaEvaluator = formulaEvaluator;
    }

    /**
     * Bulk-updates the formula column for every record in the collection.
     * Runs on the applicationTaskExecutor thread pool.
     *
     * @param collectionName the collection whose records will be updated
     * @param fieldName      the formula field API name (e.g. "totalCost")
     * @param expression     the formula expression to evaluate
     * @param returnType     "TEXT", "NUMBER", or "BOOLEAN"
     * @param tenantId       the tenant slug (used as the PostgreSQL schema name)
     */
    @Async("applicationTaskExecutor")
    public void recompute(String collectionName, String fieldName, String expression,
                           String returnType, String tenantId) {
        CollectionDefinition def = collectionRegistry.get(collectionName);
        if (def == null) {
            log.warn("FormulaRecomputeService: collection '{}' not found for tenant '{}' — skipping",
                    collectionName, tenantId);
            return;
        }

        TableRef tableRef = buildTableRef(def, collectionName, tenantId);
        String columnName = PhysicalTableStorageAdapter.toSnakeCase(fieldName);
        String selectSql = "SELECT * FROM " + tableRef.toSql() + " ORDER BY id LIMIT ? OFFSET ?";
        String updateSql = "UPDATE " + tableRef.toSql() + " SET \"" + columnName + "\" = ? WHERE id = ?";

        log.info("Starting formula recompute: field='{}', collection='{}', tenant='{}'",
                fieldName, collectionName, tenantId);
        int offset = 0;
        int totalUpdated = 0;

        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql, BATCH_SIZE, offset);
            if (rows.isEmpty()) break;

            List<Object[]> params = rows.stream()
                    .map(row -> new Object[]{ computeValue(expression, returnType, row), row.get("id") })
                    .toList();
            jdbcTemplate.batchUpdate(updateSql, params);

            totalUpdated += rows.size();
            if (rows.size() < BATCH_SIZE) break;
            offset += rows.size();
        }

        log.info("Formula recompute complete: field='{}', collection='{}', {} records updated",
                fieldName, collectionName, totalUpdated);
    }

    private Object computeValue(String expression, String returnType, Map<String, Object> row) {
        try {
            return formulaEvaluator.evaluate(expression, row);
        } catch (Exception e) {
            return ("NUMBER".equalsIgnoreCase(returnType) || "BOOLEAN".equalsIgnoreCase(returnType))
                    ? null : "#ERROR";
        }
    }

    private TableRef buildTableRef(CollectionDefinition def, String collectionName, String tenantId) {
        String tableName = (def.storageConfig() != null && def.storageConfig().tableName() != null)
                ? def.storageConfig().tableName()
                : collectionName;
        // storageConfig == null means system collection (lives in public schema)
        if (def.storageConfig() == null) {
            return TableRef.publicSchema(tableName);
        }
        return TableRef.tenantSchema(tenantId, tableName);
    }
}
```

- [ ] **Step 2: Update `FieldConfigEventPublisher.java` to inject `FormulaRecomputeService` and trigger recompute**

Add the field and update the constructor:

```java
// Add field (after the existing fields):
private final FormulaRecomputeService formulaRecomputeService;

// Update constructor:
public FieldConfigEventPublisher(PlatformEventPublisher eventPublisher,
                                  JdbcTemplate jdbcTemplate,
                                  CollectionLifecycleManager lifecycleManager,
                                  CerbosAuthorizationService cerbosAuthorizationService,
                                  FormulaRecomputeService formulaRecomputeService) {
    this.eventPublisher = eventPublisher;
    this.jdbcTemplate = jdbcTemplate;
    this.lifecycleManager = lifecycleManager;
    this.cerbosAuthorizationService = cerbosAuthorizationService;
    this.formulaRecomputeService = formulaRecomputeService;
}
```

Override `afterCreate` to detect new FORMULA fields:

```java
@Override
public void afterCreate(Map<String, Object> record, String tenantId) {
    publishCollectionUpdated(record, tenantId);
    if ("formula".equals(getString(record, "type"))) {
        triggerRecompute(record, tenantId);
    }
}
```

Override `afterUpdate` to detect expression changes:

```java
@Override
public void afterUpdate(String id, Map<String, Object> record,
                         Map<String, Object> previous, String tenantId) {
    publishCollectionUpdated(record, tenantId);
    if ("formula".equals(getString(record, "type")) && expressionChanged(record, previous)) {
        triggerRecompute(record, tenantId);
    }
}
```

Add the two helper methods (can go at the bottom of the class, before the closing brace):

```java
@SuppressWarnings("unchecked")
private void triggerRecompute(Map<String, Object> fieldRecord, String tenantId) {
    String collectionId = getString(fieldRecord, "collectionId");
    String fieldName = getString(fieldRecord, "name");
    Map<String, Object> ftc = (Map<String, Object>) fieldRecord.get("fieldTypeConfig");
    if (collectionId == null || fieldName == null || ftc == null) return;

    String expression = (String) ftc.get("expression");
    String returnType = ftc.containsKey("returnType") ? (String) ftc.get("returnType") : "TEXT";
    String collectionName = resolveCollectionName(collectionId);
    if (collectionName == null || expression == null) return;

    log.info("Triggering formula recompute for field='{}' on collection='{}' (tenant='{}')",
            fieldName, collectionName, tenantId);
    formulaRecomputeService.recompute(collectionName, fieldName, expression, returnType, tenantId);
}

@SuppressWarnings("unchecked")
private boolean expressionChanged(Map<String, Object> record, Map<String, Object> previous) {
    Map<String, Object> newFtc = (Map<String, Object>) record.get("fieldTypeConfig");
    Map<String, Object> oldFtc = (Map<String, Object>) previous.get("fieldTypeConfig");
    String newExpr = newFtc != null ? (String) newFtc.get("expression") : null;
    String oldExpr = oldFtc != null ? (String) oldFtc.get("expression") : null;
    return newExpr != null && !newExpr.equals(oldExpr);
}
```

Add the import at the top of `FieldConfigEventPublisher.java`:
```java
import io.kelta.worker.service.FormulaRecomputeService;
```

- [ ] **Step 3: Update `FlowConfig.java` to pass `FormulaRecomputeService` to `FieldConfigEventPublisher`**

Find the `fieldConfigEventPublisher` bean method and add `FormulaRecomputeService` as a parameter:

```java
// Before:
@Bean
public FieldConfigEventPublisher fieldConfigEventPublisher(
        BeforeSaveHookRegistry hookRegistry,
        PlatformEventPublisher eventPublisher,
        JdbcTemplate jdbcTemplate,
        CollectionLifecycleManager lifecycleManager,
        io.kelta.worker.service.CerbosAuthorizationService cerbosAuthorizationService) {
    FieldConfigEventPublisher publisher =
            new FieldConfigEventPublisher(eventPublisher, jdbcTemplate, lifecycleManager,
                    cerbosAuthorizationService);
    hookRegistry.register(publisher);
    return publisher;
}

// After:
@Bean
public FieldConfigEventPublisher fieldConfigEventPublisher(
        BeforeSaveHookRegistry hookRegistry,
        PlatformEventPublisher eventPublisher,
        JdbcTemplate jdbcTemplate,
        CollectionLifecycleManager lifecycleManager,
        io.kelta.worker.service.CerbosAuthorizationService cerbosAuthorizationService,
        FormulaRecomputeService formulaRecomputeService) {
    FieldConfigEventPublisher publisher =
            new FieldConfigEventPublisher(eventPublisher, jdbcTemplate, lifecycleManager,
                    cerbosAuthorizationService, formulaRecomputeService);
    hookRegistry.register(publisher);
    return publisher;
}
```

Add the import at the top of `FlowConfig.java`:
```java
import io.kelta.worker.service.FormulaRecomputeService;
```

- [ ] **Step 4: Compile kelta-worker**

```bash
mvn compile -f kelta-worker/pom.xml 2>&1 | tail -15
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Run the kelta-worker test suite**

```bash
mvn test -f kelta-worker/pom.xml 2>&1 | tail -30
```

Expected: all tests PASS. (If any test builds `FieldConfigEventPublisher` directly, update it to add `null` for the new `FormulaRecomputeService` parameter — or use `mock(FormulaRecomputeService.class)`.)

- [ ] **Step 6: Commit**

```bash
git add kelta-worker/src/main/java/io/kelta/worker/service/FormulaRecomputeService.java \
        kelta-worker/src/main/java/io/kelta/worker/listener/FieldConfigEventPublisher.java \
        kelta-worker/src/main/java/io/kelta/worker/config/FlowConfig.java
git commit -m "$(cat <<'EOF'
feat(worker): background bulk recompute for formula fields

FormulaRecomputeService pages through all records in batches of 500 and
batch-updates the formula column. FieldConfigEventPublisher triggers it
asynchronously when a formula field is created or its expression changes.

Co-Authored-By: Anna Klinker <anna@rzware.com>
EOF
)"
```

---

### Task 6: Admin UI — formula config block in FieldEditor

**Files:**
- Modify: `kelta-ui/app/src/components/FieldEditor/FieldEditor.tsx`

**Interfaces:**
- Consumes: `FieldExpressionPicker` (props: `open`, `onOpenChange`, `rootCollectionId`, `mode`, `onInsert`, `title`) — already imported and used in another part of the file; `watch('type')`, `register('formulaExpression')`, `register('formulaReturnType')`
- Produces: When `watchedType === 'formula'`, renders a config block with: (a) a return type select (disabled when editing), (b) a `<textarea>` for the expression, (c) a button that opens `FieldExpressionPicker`, (d) the field type config `{ expression, returnType }` is passed to the backend on submit

- [ ] **Step 1: Add Zod fields and defaults**

In the Zod schema object (~line 277), after `rollupField`:

```typescript
formulaExpression: z.string().optional().or(z.literal('')),
formulaReturnType: z.enum(['TEXT', 'NUMBER', 'BOOLEAN']).optional(),
```

In the form `defaultValues` object (find where `rollupChildCollection: ''` is set), add:

```typescript
formulaExpression: '',
formulaReturnType: undefined,
```

In the `reset(...)` call used when `field` prop changes (find `rollupChildCollection: (parsedConfig.childCollection as string) ?? ''`), add:

```typescript
formulaExpression: (parsedConfig.expression as string) ?? '',
formulaReturnType: (parsedConfig.returnType as 'TEXT' | 'NUMBER' | 'BOOLEAN') ?? undefined,
```

- [ ] **Step 2: Add formula fieldTypeConfig building**

In the `onSubmit` handler, inside the `fieldTypeConfig` building block (~line 667), after the `rollup_summary` branch:

```typescript
} else if (data.type === 'formula') {
  fieldTypeConfig = {
    expression: data.formulaExpression ?? '',
    returnType: data.formulaReturnType ?? 'TEXT',
  }
}
```

- [ ] **Step 3: Add a `useState` for the expression picker open state**

Near the other `useState` declarations in the component, add:

```typescript
const [formulaPickerOpen, setFormulaPickerOpen] = useState(false)
```

- [ ] **Step 4: Add the formula config block JSX**

Find the end of the rollup_summary block (the closing `)}` after the rollup config div, ~line 1230). Directly after it, add:

```tsx
{/* Formula Config */}
{watchedType === 'formula' && (
  <div
    className="flex flex-col gap-4 p-4 bg-secondary border border-border rounded-md"
    data-testid="formula-config"
  >
    <h4 className="m-0 text-base font-medium text-foreground">
      {t('fieldEditor.formula.title')}
    </h4>

    {/* Return type */}
    <div className="flex flex-col gap-1">
      <label
        htmlFor="field-formula-return-type"
        className="flex items-center gap-1 text-sm font-medium text-foreground"
      >
        {t('fieldEditor.formula.returnType')}
        <span className="text-destructive font-semibold" aria-hidden="true">*</span>
      </label>
      <select
        id="field-formula-return-type"
        className={cn(selectClasses, errors.formulaReturnType && errorInputClasses)}
        disabled={isSubmitting || !!field}
        aria-required="true"
        aria-invalid={!!errors.formulaReturnType}
        data-testid="field-formula-return-type-select"
        {...register('formulaReturnType')}
      >
        <option value="">{t('fieldEditor.selectOption')}</option>
        <option value="TEXT">{t('fieldEditor.formula.returnTypeText')}</option>
        <option value="NUMBER">{t('fieldEditor.formula.returnTypeNumber')}</option>
        <option value="BOOLEAN">{t('fieldEditor.formula.returnTypeBoolean')}</option>
      </select>
      {!!field && (
        <span className="text-xs text-muted-foreground mt-1">
          {t('fieldEditor.formula.returnTypeImmutable')}
        </span>
      )}
      {errors.formulaReturnType && (
        <span
          className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['⚠'] before:text-xs"
          role="alert"
          data-testid="field-formula-return-type-error"
        >
          {getErrorMessage(errors.formulaReturnType.message)}
        </span>
      )}
    </div>

    {/* Expression */}
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between">
        <label
          htmlFor="field-formula-expression"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('fieldEditor.formula.expression')}
          <span className="text-destructive font-semibold" aria-hidden="true">*</span>
        </label>
        <button
          type="button"
          className="text-xs text-primary underline"
          onClick={() => setFormulaPickerOpen(true)}
          data-testid="field-formula-picker-button"
        >
          {t('fieldEditor.formula.insertField')}
        </button>
      </div>
      <textarea
        id="field-formula-expression"
        className={cn(
          'w-full min-h-[80px] rounded-md border border-input bg-background px-3 py-2 text-sm font-mono',
          'placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring',
          errors.formulaExpression && errorInputClasses,
        )}
        disabled={isSubmitting}
        aria-required="true"
        aria-invalid={!!errors.formulaExpression}
        placeholder={t('fieldEditor.formula.expressionPlaceholder')}
        data-testid="field-formula-expression-textarea"
        {...register('formulaExpression')}
      />
      {errors.formulaExpression && (
        <span
          className="flex items-center gap-1 text-sm text-destructive mt-1 before:content-['⚠'] before:text-xs"
          role="alert"
          data-testid="field-formula-expression-error"
        >
          {getErrorMessage(errors.formulaExpression.message)}
        </span>
      )}
      <span className="text-xs text-muted-foreground mt-1">
        {t('fieldEditor.formula.expressionHint')}
      </span>
    </div>

    <FieldExpressionPicker
      open={formulaPickerOpen}
      onOpenChange={setFormulaPickerOpen}
      rootCollectionId={collectionId ?? null}
      mode="expression"
      title={t('fieldEditor.formula.pickerTitle')}
      onInsert={(token) => {
        const textarea = document.getElementById('field-formula-expression') as HTMLTextAreaElement | null
        if (!textarea) return
        const start = textarea.selectionStart ?? textarea.value.length
        const end = textarea.selectionEnd ?? textarea.value.length
        const current = textarea.value
        const next = current.slice(0, start) + token + current.slice(end)
        setValue('formulaExpression', next, { shouldValidate: true, shouldDirty: true })
        // Restore focus and cursor after React re-render
        requestAnimationFrame(() => {
          textarea.focus()
          textarea.setSelectionRange(start + token.length, start + token.length)
        })
      }}
      data-testid="field-formula-picker"
    />
  </div>
)}
```

- [ ] **Step 5: Add i18n keys**

Find the translation file used by `FieldEditor` (search for an existing key like `fieldEditor.rollup.title` in `kelta-ui/app/src/` to locate the i18n JSON file). Add:

```json
"formula": {
  "title": "Formula",
  "returnType": "Return type",
  "returnTypeText": "Text",
  "returnTypeNumber": "Number",
  "returnTypeBoolean": "Boolean",
  "returnTypeImmutable": "Return type cannot be changed after the field is created.",
  "expression": "Expression",
  "expressionPlaceholder": "e.g. price * (1 + tax_rate)",
  "expressionHint": "Reference other fields by name. Use the Insert Field button to browse available fields.",
  "insertField": "Insert field",
  "pickerTitle": "Insert field into expression"
}
```

under the existing `fieldEditor` key.

- [ ] **Step 6: Run the frontend type-check**

```bash
cd kelta-ui/app && npx tsc --noEmit 2>&1 | tail -20
```

Expected: no errors. (If `formulaReturnType` type errors appear, ensure the Zod enum type is correctly inferred by `z.infer<typeof schema>`.)

- [ ] **Step 7: Run the frontend tests**

```bash
cd kelta-ui/app && npx vitest run --reporter=verbose 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 8: Commit**

```bash
git add kelta-ui/app/src/components/FieldEditor/FieldEditor.tsx
# Also add the i18n file(s) modified in step 5
git commit -m "$(cat <<'EOF'
feat(ui): formula field config block in FieldEditor

Adds return type selector (immutable on edit), expression textarea,
and FieldExpressionPicker click-to-insert integration.

Co-Authored-By: Anna Klinker <anna@rzware.com>
EOF
)"
```

---

## Post-implementation verification

After all tasks are complete, run the full build and test suite:

```bash
mvn verify -f kelta-platform/pom.xml 2>&1 | tail -20
mvn verify -f kelta-worker/pom.xml 2>&1 | tail -20
cd kelta-ui/app && npx tsc --noEmit && npx vitest run 2>&1 | tail -20
```

Then use `/verify` to run the full CI check before opening a PR.
