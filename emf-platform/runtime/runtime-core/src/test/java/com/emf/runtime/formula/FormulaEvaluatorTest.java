package com.emf.runtime.formula;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormulaEvaluatorTest {

    private FormulaEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // Collect all built-in functions
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
        evaluator = new FormulaEvaluator(functions);
    }

    @Nested
    class Arithmetic {
        @Test
        void simpleAddition() {
            Object result = evaluator.evaluate("Amount + Tax", Map.of("Amount", 100.0, "Tax", 8.5));
            assertEquals(108.5, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void multiplication() {
            Object result = evaluator.evaluate("Amount * Quantity", Map.of("Amount", 25.0, "Quantity", 4));
            assertEquals(100.0, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void divisionByZeroThrows() {
            assertThrows(FormulaException.class, () ->
                evaluator.evaluate("Amount / Zero", Map.of("Amount", 100.0, "Zero", 0)));
        }

        @Test
        void operatorPrecedence() {
            Object result = evaluator.evaluate("2 + 3 * 4", Map.of());
            assertEquals(14.0, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void parentheses() {
            Object result = evaluator.evaluate("(2 + 3) * 4", Map.of());
            assertEquals(20.0, ((Number) result).doubleValue(), 0.001);
        }
    }

    @Nested
    class Comparison {
        @Test
        void greaterThan() {
            assertTrue(evaluator.evaluateBoolean("Amount > 0", Map.of("Amount", 50.0)));
        }

        @Test
        void equality() {
            assertTrue(evaluator.evaluateBoolean("Stage = 'Closed Won'", Map.of("Stage", "Closed Won")));
        }

        @Test
        void notEqual() {
            assertTrue(evaluator.evaluateBoolean("Status != 'Draft'", Map.of("Status", "Active")));
        }
    }

    @Nested
    class LogicalOperators {
        @Test
        void andOperator() {
            assertTrue(evaluator.evaluateBoolean("Amount > 0 && Active = true",
                Map.of("Amount", 100.0, "Active", true)));
        }

        @Test
        void orOperator() {
            assertTrue(evaluator.evaluateBoolean("Amount > 100 || Priority = 'High'",
                Map.of("Amount", 50.0, "Priority", "High")));
        }
    }

    @Nested
    class Functions {
        @Test
        void ifFunction() {
            Object result = evaluator.evaluate("IF(Amount > 100, 'Large', 'Small')",
                Map.of("Amount", 200.0));
            assertEquals("Large", result);
        }

        @Test
        void isBlankFunction() {
            assertTrue(evaluator.evaluateBoolean("ISBLANK(Email)", Map.of()));
        }

        @Test
        void blankValueFunction() {
            Object result = evaluator.evaluate("BLANKVALUE(Email, 'none@example.com')", Map.of());
            assertEquals("none@example.com", result);
        }

        @Test
        void lenFunction() {
            Object result = evaluator.evaluate("LEN(Name)", Map.of("Name", "Hello"));
            assertEquals(5, ((Number) result).intValue());
        }

        @Test
        void containsFunction() {
            assertTrue(evaluator.evaluateBoolean("CONTAINS(Name, 'World')",
                Map.of("Name", "Hello World")));
        }

        @Test
        void upperFunction() {
            assertEquals("HELLO", evaluator.evaluate("UPPER(Name)", Map.of("Name", "hello")));
        }

        @Test
        void roundFunction() {
            Object result = evaluator.evaluate("ROUND(3.14159, 2)", Map.of());
            assertEquals(3.14, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void absFunction() {
            Object result = evaluator.evaluate("ABS(-42)", Map.of());
            assertEquals(42.0, ((Number) result).doubleValue(), 0.001);
        }

        @Test
        void nestedFunctions() {
            Object result = evaluator.evaluate("IF(ISBLANK(Email), 'No Email', Email)",
                Map.of("Email", "test@example.com"));
            assertEquals("test@example.com", result);
        }

        @Test
        void andFunction() {
            assertTrue(evaluator.evaluateBoolean("AND(Amount > 0, Active = true)",
                Map.of("Amount", 100.0, "Active", true)));
        }
    }

    @Nested
    class Validation {
        @Test
        void emptryExpressionThrows() {
            assertThrows(FormulaException.class, () -> evaluator.validate(""));
        }

        @Test
        void invalidSyntaxThrows() {
            assertThrows(FormulaException.class, () -> evaluator.validate("Amount +"));
        }

        @Test
        void validExpressionPasses() {
            assertDoesNotThrow(() -> evaluator.validate("Amount * Quantity + Tax"));
        }

        @Test
        void unknownFunctionThrows() {
            assertThrows(FormulaException.class, () ->
                evaluator.evaluate("UNKNOWN_FUNC(1)", Map.of()));
        }
    }

    @Nested
    class CompilationCache {
        @Test
        void shouldCacheParsedExpression() {
            evaluator.evaluate("Amount + Tax", Map.of("Amount", 100.0, "Tax", 8.5));
            assertEquals(1, evaluator.cacheSize());

            // Same expression should be served from cache
            evaluator.evaluate("Amount + Tax", Map.of("Amount", 200.0, "Tax", 10.0));
            assertEquals(1, evaluator.cacheSize());
        }

        @Test
        void shouldCacheDifferentExpressions() {
            evaluator.evaluate("Amount + Tax", Map.of("Amount", 100.0, "Tax", 8.5));
            evaluator.evaluate("Amount * Quantity", Map.of("Amount", 25.0, "Quantity", 4));
            assertEquals(2, evaluator.cacheSize());
        }

        @Test
        void shouldReturnCorrectResultFromCache() {
            Object result1 = evaluator.evaluate("Amount + Tax", Map.of("Amount", 100.0, "Tax", 8.5));
            assertEquals(108.5, ((Number) result1).doubleValue(), 0.001);

            // Different context, same expression — should use cached AST
            Object result2 = evaluator.evaluate("Amount + Tax", Map.of("Amount", 200.0, "Tax", 20.0));
            assertEquals(220.0, ((Number) result2).doubleValue(), 0.001);
        }

        @Test
        void shouldEvictSpecificExpression() {
            evaluator.evaluate("Amount + Tax", Map.of("Amount", 100.0, "Tax", 8.5));
            evaluator.evaluate("Amount * Quantity", Map.of("Amount", 25.0, "Quantity", 4));
            assertEquals(2, evaluator.cacheSize());

            evaluator.evict("Amount + Tax");
            assertEquals(1, evaluator.cacheSize());
        }

        @Test
        void shouldClearAllCachedExpressions() {
            evaluator.evaluate("Amount + Tax", Map.of("Amount", 100.0, "Tax", 8.5));
            evaluator.evaluate("Amount * Quantity", Map.of("Amount", 25.0, "Quantity", 4));
            assertEquals(2, evaluator.cacheSize());

            evaluator.clearCache();
            assertEquals(0, evaluator.cacheSize());
        }

        @Test
        void shouldRespectMaxCacheSize() {
            // Create evaluator with small cache size
            FormulaEvaluator smallCacheEvaluator = new FormulaEvaluator(
                List.of(
                    new BuiltInFunctions.If(),
                    new BuiltInFunctions.IsBlank()
                ), 3);

            smallCacheEvaluator.evaluate("1 + 1", Map.of());
            smallCacheEvaluator.evaluate("2 + 2", Map.of());
            smallCacheEvaluator.evaluate("3 + 3", Map.of());
            assertEquals(3, smallCacheEvaluator.cacheSize());

            // Adding a 4th expression should trigger a clear
            smallCacheEvaluator.evaluate("4 + 4", Map.of());
            assertEquals(1, smallCacheEvaluator.cacheSize());
        }

        @Test
        void shouldStartWithEmptyCache() {
            assertEquals(0, evaluator.cacheSize());
        }

        @Test
        void evictNonExistentExpressionShouldNotThrow() {
            assertDoesNotThrow(() -> evaluator.evict("nonexistent"));
        }
    }
}
