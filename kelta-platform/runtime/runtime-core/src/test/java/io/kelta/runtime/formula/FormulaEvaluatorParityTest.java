package io.kelta.runtime.formula;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity test that asserts the Java FormulaEvaluator produces results
 * identical to the TypeScript @kelta/formula evaluator. Both implementations
 * read the same fixture file. Add new edge cases to formula-parity-fixtures.json
 * when changing either side.
 */
class FormulaEvaluatorParityTest {

    private static final String FIXTURE_RESOURCE = "/formula-parity-fixtures.json";

    private FormulaEvaluator buildEvaluator() {
        List<FormulaFunction> functions = List.of(
            new BuiltInFunctions.Today(),
            new BuiltInFunctions.Now(),
            new BuiltInFunctions.IsBlank(),
            new BuiltInFunctions.BlankValue(),
            new BuiltInFunctions.If(),
            new BuiltInFunctions.And(),
            new BuiltInFunctions.Or(),
            new BuiltInFunctions.Not(),
            new BuiltInFunctions.Len(),
            new BuiltInFunctions.Contains(),
            new BuiltInFunctions.Upper(),
            new BuiltInFunctions.Lower(),
            new BuiltInFunctions.Trim(),
            new BuiltInFunctions.Text(),
            new BuiltInFunctions.Value(),
            new BuiltInFunctions.Round(),
            new BuiltInFunctions.Abs(),
            new BuiltInFunctions.Max(),
            new BuiltInFunctions.Min(),
            new BuiltInFunctions.Regex(),
            new BuiltInFunctions.DateDiff()
        );
        return new FormulaEvaluator(functions);
    }

    @TestFactory
    List<DynamicTest> sharedFixtureCases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            assertTrue(in != null, "Fixture resource not found: " + FIXTURE_RESOURCE);
            JsonNode root = mapper.readTree(in);
            JsonNode cases = root.get("cases");
            FormulaEvaluator evaluator = buildEvaluator();

            List<DynamicTest> tests = new ArrayList<>();
            for (JsonNode c : cases) {
                String name = c.get("name").asString();
                String expression = c.get("expression").asString();
                Map<String, Object> context = parseContext(c.get("context"));
                boolean throwsExpected = c.has("throws") && c.get("throws").asBoolean();
                JsonNode expectedNode = c.get("expected");
                JsonNode expectedTypeNode = c.get("expectedType");

                tests.add(DynamicTest.dynamicTest(name, () -> {
                    if (throwsExpected) {
                        assertThrows(FormulaException.class,
                            () -> evaluator.evaluate(expression, context),
                            "Expected throw for: " + expression);
                        return;
                    }
                    Object result = evaluator.evaluate(expression, context);
                    assertResultMatches(expectedNode, expectedTypeNode, result, expression);
                }));
            }
            return tests;
        }
    }

    private static Map<String, Object> parseContext(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        if (node == null || !node.isObject()) return map;
        node.propertyStream().forEach(entry -> map.put(entry.getKey(), jsonToJava(entry.getValue())));
        return map;
    }

    private static Object jsonToJava(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isInt() || n.isShort()) return n.intValue();
        if (n.isLong()) return n.longValue();
        if (n.isFloat() || n.isDouble() || n.isBigDecimal() || n.isNumber()) return n.doubleValue();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isString()) return n.asString();
        return n.toString();
    }

    private static void assertResultMatches(JsonNode expectedNode, JsonNode expectedTypeNode, Object actual, String expression) {
        if (expectedNode == null || expectedNode.isNull()) {
            assertEquals(null, actual, "Expression: " + expression);
            return;
        }
        if (expectedNode.isBoolean()) {
            assertEquals(expectedNode.asBoolean(), actual, "Expression: " + expression);
            return;
        }
        if (expectedNode.isNumber()) {
            assertTrue(actual instanceof Number, "Expected Number for " + expression + ", got " + (actual == null ? "null" : actual.getClass()));
            assertEquals(expectedNode.doubleValue(), ((Number) actual).doubleValue(), 1e-9, "Expression: " + expression);
            return;
        }
        if (expectedNode.isString()) {
            String s = expectedNode.asString();
            String actualStr = actual == null ? null : actual.toString();
            assertEquals(s, actualStr, "Expression: " + expression);
            if (expectedTypeNode != null && "string".equals(expectedTypeNode.asString())) {
                assertTrue(actual instanceof String, "Expected String for " + expression);
            }
            return;
        }
        // fallback: string compare
        assertEquals(expectedNode.toString(), actual == null ? "null" : actual.toString(), "Expression: " + expression);
    }
}
