package io.kelta.worker.listener;

import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FormulaComputeHook")
class FormulaComputeHookTest {

    @Mock
    private CollectionRegistry collectionRegistry;

    private FormulaEvaluator formulaEvaluator;
    private FormulaComputeHook hook;

    @BeforeEach
    void setUp() {
        formulaEvaluator = new FormulaEvaluator(List.of());
        hook = new FormulaComputeHook(collectionRegistry, formulaEvaluator);
    }

    private static FieldDefinition formula(String name, String expression, String returnType) {
        Map<String, Object> config = new HashMap<>();
        if (expression != null) {
            config.put("expression", expression);
        }
        if (returnType != null) {
            config.put("returnType", returnType);
        }
        return new FieldDefinition(name, FieldType.FORMULA,
                true, false, false, null, null, null, null, config, null);
    }

    private static CollectionDefinition collection(String name, FieldDefinition... fields) {
        CollectionDefinitionBuilder builder = new CollectionDefinitionBuilder().name(name)
                .addField(FieldDefinition.requiredString("_placeholder"));
        for (FieldDefinition f : fields) {
            builder.addField(f);
        }
        return builder.build();
    }

    @Test
    @DisplayName("registration: wildcard collection name and runs after other transforms")
    void wildcardAndOrder() {
        assertThat(hook.getCollectionName()).isEqualTo("*");
        assertThat(hook.getOrder()).isGreaterThan(120);
    }

    @Test
    @DisplayName("no formula fields → ok (no updates)")
    void noFormulaFields() {
        when(collectionRegistry.get("widgets")).thenReturn(
                collection("widgets", FieldDefinition.doubleField("price")));

        Map<String, Object> record = new HashMap<>(Map.of("price", 10.0));
        BeforeSaveResult result = hook.beforeCreate("widgets", record, "tenant-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFieldUpdates()).isFalse();
    }

    @Test
    @DisplayName("simple expression: computes amount * quantity into total")
    void simpleExpression() {
        when(collectionRegistry.get("invoices")).thenReturn(collection("invoices",
                FieldDefinition.doubleField("amount"),
                FieldDefinition.doubleField("quantity"),
                formula("total", "amount * quantity", "NUMBER")));

        Map<String, Object> record = new HashMap<>();
        record.put("amount", 12.5);
        record.put("quantity", 4);

        BeforeSaveResult result = hook.beforeCreate("invoices", record, "tenant-1");

        assertThat(result.hasFieldUpdates()).isTrue();
        assertThat(((Number) result.getFieldUpdates().get("total")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("multi-field dependency: dependents are evaluated after their dependencies")
    void dependencyOrdering() {
        // total = amount * quantity, tax = total * 0.1, grand = total + tax
        // Declared in reverse-dependency order to force the topo sort to reorder them.
        when(collectionRegistry.get("invoices")).thenReturn(collection("invoices",
                FieldDefinition.doubleField("amount"),
                FieldDefinition.doubleField("quantity"),
                formula("grand", "total + tax", "NUMBER"),
                formula("tax", "total * 0.1", "NUMBER"),
                formula("total", "amount * quantity", "NUMBER")));

        Map<String, Object> record = new HashMap<>();
        record.put("amount", 100.0);
        record.put("quantity", 2);

        BeforeSaveResult result = hook.beforeCreate("invoices", record, "tenant-1");

        Map<String, Object> updates = result.getFieldUpdates();
        assertThat(((Number) updates.get("total")).doubleValue()).isEqualTo(200.0);
        assertThat(((Number) updates.get("tax")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) updates.get("grand")).doubleValue()).isEqualTo(220.0);
    }

    @Test
    @DisplayName("update: previous values fill the context when payload omits referenced fields")
    void updateMergesPrevious() {
        when(collectionRegistry.get("invoices")).thenReturn(collection("invoices",
                FieldDefinition.doubleField("amount"),
                FieldDefinition.doubleField("quantity"),
                formula("total", "amount * quantity", "NUMBER")));

        Map<String, Object> record = new HashMap<>();
        record.put("quantity", 5);
        Map<String, Object> previous = new HashMap<>();
        previous.put("amount", 10.0);
        previous.put("quantity", 2);

        BeforeSaveResult result = hook.beforeUpdate("invoices", "rec-1", record, previous, "tenant-1");

        assertThat(((Number) result.getFieldUpdates().get("total")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("circular reference: cyclic formulas fall back to #ERROR (TEXT) and null (NUMBER)")
    void circularReferenceFallback() {
        // a depends on b, b depends on a → both unreachable in topo order.
        when(collectionRegistry.get("loop")).thenReturn(collection("loop",
                formula("a", "b + 1", "NUMBER"),
                formula("b", "a + 1", "TEXT")));

        Map<String, Object> record = new HashMap<>();

        BeforeSaveResult result = hook.beforeCreate("loop", record, "tenant-1");

        Map<String, Object> updates = result.getFieldUpdates();
        assertThat(updates).containsKeys("a", "b");
        assertThat(updates.get("a")).isNull();
        assertThat(updates.get("b")).isEqualTo("#ERROR");
    }

    @Test
    @DisplayName("evaluation error: division by zero falls back per return type")
    void evaluationErrorPerReturnType() {
        when(collectionRegistry.get("ratios")).thenReturn(collection("ratios",
                FieldDefinition.doubleField("numerator"),
                formula("ratio", "numerator / 0", "NUMBER"),
                formula("label", "numerator / 0", "TEXT")));

        Map<String, Object> record = new HashMap<>();
        record.put("numerator", 10.0);

        BeforeSaveResult result = hook.beforeCreate("ratios", record, "tenant-1");

        assertThat(result.getFieldUpdates().get("ratio")).isNull();
        assertThat(result.getFieldUpdates().get("label")).isEqualTo("#ERROR");
    }

    @Test
    @DisplayName("self-reference: a formula referencing itself is treated as circular")
    void selfReferenceIsCircular() {
        when(collectionRegistry.get("self")).thenReturn(collection("self",
                formula("loop", "loop + 1", "NUMBER")));

        BeforeSaveResult result = hook.beforeCreate("self", new HashMap<>(), "tenant-1");

        assertThat(result.getFieldUpdates()).containsEntry("loop", null);
    }

    @Test
    @DisplayName("blank expression: yields null without error")
    void blankExpressionYieldsNull() {
        when(collectionRegistry.get("empty")).thenReturn(collection("empty",
                formula("empty", "", "TEXT")));

        BeforeSaveResult result = hook.beforeCreate("empty", new HashMap<>(), "tenant-1");

        assertThat(result.getFieldUpdates()).containsEntry("empty", null);
    }

    @Test
    @DisplayName("unknown collection: returns ok without updates")
    void unknownCollection() {
        when(collectionRegistry.get("missing")).thenReturn(null);

        BeforeSaveResult result = hook.beforeCreate("missing", new HashMap<>(), "tenant-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasFieldUpdates()).isFalse();
    }
}
