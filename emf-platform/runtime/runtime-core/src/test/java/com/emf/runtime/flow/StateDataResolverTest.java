package com.emf.runtime.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StateDataResolver")
class StateDataResolverTest {

    private StateDataResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StateDataResolver(new ObjectMapper());
    }

    private Map<String, Object> stateData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", "ord-123");
        data.put("status", "ACTIVE");
        data.put("amount", 250.0);
        data.put("customer", Map.of(
            "name", "Alice",
            "email", "alice@example.com"
        ));
        data.put("items", List.of(
            Map.of("sku", "A1", "qty", 2),
            Map.of("sku", "B2", "qty", 1)
        ));
        return data;
    }

    @Nested
    @DisplayName("applyInputPath")
    class InputPath {

        @Test
        @DisplayName("null path returns entire state (default)")
        void nullPathReturnsEntireState() {
            Map<String, Object> state = stateData();
            Map<String, Object> result = resolver.applyInputPath(state, null);
            assertEquals(state, result);
        }

        @Test
        @DisplayName("$ path returns entire state")
        void rootPathReturnsEntireState() {
            Map<String, Object> state = stateData();
            Map<String, Object> result = resolver.applyInputPath(state, "$");
            assertEquals(state, result);
        }

        @Test
        @DisplayName("selects nested object")
        void selectsNestedObject() {
            Map<String, Object> result = resolver.applyInputPath(stateData(), "$.customer");
            assertEquals("Alice", result.get("name"));
            assertEquals("alice@example.com", result.get("email"));
        }

        @Test
        @DisplayName("non-map result is wrapped")
        void nonMapResultIsWrapped() {
            Map<String, Object> result = resolver.applyInputPath(stateData(), "$.orderId");
            assertEquals("ord-123", result.get("result"));
        }

        @Test
        @DisplayName("null state data returns empty map")
        void nullStateReturnsEmpty() {
            Map<String, Object> result = resolver.applyInputPath(null, "$");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("blank path treated as root")
        void blankPathTreatedAsRoot() {
            Map<String, Object> state = stateData();
            Map<String, Object> result = resolver.applyInputPath(state, "  ");
            assertEquals(state, result);
        }
    }

    @Nested
    @DisplayName("applyResultPath")
    class ResultPath {

        @Test
        @DisplayName("null/root path replaces entire state with map result")
        void rootPathReplacesWithMapResult() {
            Map<String, Object> result = Map.of("newKey", "newValue");
            Map<String, Object> updated = resolver.applyResultPath(stateData(), result, null);
            assertEquals("newValue", updated.get("newKey"));
            assertNull(updated.get("orderId")); // old data gone
        }

        @Test
        @DisplayName("root path with non-map result wraps it")
        void rootPathWrapsNonMapResult() {
            Map<String, Object> updated = resolver.applyResultPath(stateData(), "done", "$");
            assertEquals("done", updated.get("result"));
        }

        @Test
        @DisplayName("nested path places result at specified location")
        void nestedPathPlacesResult() {
            Map<String, Object> apiResponse = Map.of("code", 200, "body", "OK");
            Map<String, Object> updated = resolver.applyResultPath(stateData(), apiResponse, "$.apiResponse");

            // Original data preserved
            assertEquals("ord-123", updated.get("orderId"));
            // New data added
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) updated.get("apiResponse");
            assertEquals(200, response.get("code"));
            assertEquals("OK", response.get("body"));
        }

        @Test
        @DisplayName("deeply nested path creates intermediate maps")
        void deeplyNestedPathCreatesIntermediates() {
            Map<String, Object> updated = resolver.applyResultPath(
                stateData(), "value", "$.deep.nested.path");

            @SuppressWarnings("unchecked")
            Map<String, Object> deep = (Map<String, Object>) updated.get("deep");
            assertNotNull(deep);

            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) deep.get("nested");
            assertNotNull(nested);

            assertEquals("value", nested.get("path"));
        }

        @Test
        @DisplayName("result path does not mutate original state data")
        void doesNotMutateOriginal() {
            Map<String, Object> original = stateData();
            Map<String, Object> updated = resolver.applyResultPath(original, "new", "$.added");

            assertNull(original.get("added"));
            assertEquals("new", updated.get("added"));
        }
    }

    @Nested
    @DisplayName("applyOutputPath")
    class OutputPath {

        @Test
        @DisplayName("null path returns entire state (default)")
        void nullPathReturnsEntireState() {
            Map<String, Object> state = stateData();
            Map<String, Object> result = resolver.applyOutputPath(state, null);
            assertEquals(state, result);
        }

        @Test
        @DisplayName("selects nested object")
        void selectsNestedObject() {
            Map<String, Object> result = resolver.applyOutputPath(stateData(), "$.customer");
            assertEquals("Alice", result.get("name"));
            assertNull(result.get("orderId"));
        }

        @Test
        @DisplayName("non-map result is wrapped")
        void nonMapResultIsWrapped() {
            Map<String, Object> result = resolver.applyOutputPath(stateData(), "$.amount");
            assertEquals(250.0, result.get("result"));
        }

        @Test
        @DisplayName("null state data returns empty map")
        void nullStateReturnsEmpty() {
            Map<String, Object> result = resolver.applyOutputPath(null, "$.anything");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("readPath")
    class ReadPath {

        @Test
        @DisplayName("reads simple field")
        void readsSimpleField() {
            Object result = resolver.readPath(stateData(), "$.orderId");
            assertEquals("ord-123", result);
        }

        @Test
        @DisplayName("reads nested field")
        void readsNestedField() {
            Object result = resolver.readPath(stateData(), "$.customer.name");
            assertEquals("Alice", result);
        }

        @Test
        @DisplayName("reads array element")
        void readsArrayElement() {
            Object result = resolver.readPath(stateData(), "$.items[0].sku");
            assertEquals("A1", result);
        }

        @Test
        @DisplayName("returns null for missing path")
        void returnsNullForMissingPath() {
            Object result = resolver.readPath(stateData(), "$.missing");
            assertNull(result);
        }

        @Test
        @DisplayName("returns null for null state data")
        void returnsNullForNullState() {
            assertNull(resolver.readPath(null, "$.anything"));
        }

        @Test
        @DisplayName("returns null for null path")
        void returnsNullForNullPath() {
            assertNull(resolver.readPath(stateData(), null));
        }
    }

    @Nested
    @DisplayName("resolveTemplate")
    class ResolveTemplate {

        @Test
        @DisplayName("resolves simple template expression")
        void resolvesSimpleTemplate() {
            String result = resolver.resolveTemplate("Order ${$.orderId} is ${$.status}", stateData());
            assertEquals("Order ord-123 is ACTIVE", result);
        }

        @Test
        @DisplayName("resolves nested path template")
        void resolvesNestedTemplate() {
            String result = resolver.resolveTemplate("Hello ${$.customer.name}!", stateData());
            assertEquals("Hello Alice!", result);
        }

        @Test
        @DisplayName("missing path resolves to empty string")
        void missingPathResolvesToEmpty() {
            String result = resolver.resolveTemplate("Value: ${$.missing}", stateData());
            assertEquals("Value: ", result);
        }

        @Test
        @DisplayName("no template expressions returns original string")
        void noTemplateReturnsOriginal() {
            String result = resolver.resolveTemplate("plain text", stateData());
            assertEquals("plain text", result);
        }

        @Test
        @DisplayName("null template returns null")
        void nullTemplateReturnsNull() {
            assertNull(resolver.resolveTemplate(null, stateData()));
        }

        @Test
        @DisplayName("null state data returns original template")
        void nullStateReturnsOriginal() {
            assertEquals("${$.x}", resolver.resolveTemplate("${$.x}", null));
        }

        @Test
        @DisplayName("multiple expressions in one string")
        void multipleExpressions() {
            String result = resolver.resolveTemplate(
                "${$.customer.name} ordered ${$.orderId} for ${$.amount}",
                stateData());
            assertEquals("Alice ordered ord-123 for 250.0", result);
        }

        @Test
        @DisplayName("unclosed brace treated as literal")
        void unclosedBraceTreatedAsLiteral() {
            String result = resolver.resolveTemplate("test ${$.orderId and more", stateData());
            assertEquals("test ${$.orderId and more", result);
        }
    }

    @Nested
    @DisplayName("Full Data Flow Pipeline")
    class FullPipeline {

        @Test
        @DisplayName("InputPath → ResultPath → OutputPath full pipeline")
        void fullDataFlowPipeline() {
            Map<String, Object> state = stateData();

            // 1. InputPath: Select customer data
            Map<String, Object> input = resolver.applyInputPath(state, "$.customer");
            assertEquals("Alice", input.get("name"));

            // 2. Simulate handler producing a result
            Map<String, Object> handlerResult = Map.of("verified", true, "score", 95);

            // 3. ResultPath: Place result at $.verification
            Map<String, Object> afterResult = resolver.applyResultPath(state, handlerResult, "$.verification");
            assertEquals("ord-123", afterResult.get("orderId")); // original preserved
            assertNotNull(afterResult.get("verification")); // result added

            // 4. OutputPath: Select verification data for next state
            Map<String, Object> output = resolver.applyOutputPath(afterResult, "$.verification");
            assertEquals(true, output.get("verified"));
            assertEquals(95, output.get("score"));
            assertNull(output.get("orderId")); // filtered out
        }
    }
}
