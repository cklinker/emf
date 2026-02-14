package com.emf.runtime.validation;

import com.emf.runtime.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TypeCoercionService}.
 */
@DisplayName("TypeCoercionService Tests")
class TypeCoercionServiceTest {

    private CollectionDefinition createTestCollection(FieldDefinition... fields) {
        return new CollectionDefinition(
            "test_collection",
            "Test Collection",
            "A test collection",
            List.of(fields),
            new StorageConfig(StorageMode.PHYSICAL_TABLES, "tbl_test", Map.of()),
            new ApiConfig(true, true, true, true, true, "/api/collections/test"),
            new AuthzConfig(false, List.of(), List.of()),
            new EventsConfig(false, "emf", List.of()),
            1L,
            Instant.now(),
            Instant.now()
        );
    }

    @Nested
    @DisplayName("Integer Coercion")
    class IntegerCoercionTests {

        @Test
        @DisplayName("Should coerce string to integer")
        void shouldCoerceStringToInteger() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", "42");

            TypeCoercionService.coerce(def, data);

            assertEquals(42, data.get("count"));
            assertInstanceOf(Integer.class, data.get("count"));
        }

        @Test
        @DisplayName("Should coerce string with whitespace to integer")
        void shouldCoerceStringWithWhitespaceToInteger() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", " 42 ");

            TypeCoercionService.coerce(def, data);

            assertEquals(42, data.get("count"));
        }

        @Test
        @DisplayName("Should coerce string '10.0' to integer")
        void shouldCoerceDecimalStringToInteger() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", "10.0");

            TypeCoercionService.coerce(def, data);

            assertEquals(10, data.get("count"));
            assertInstanceOf(Integer.class, data.get("count"));
        }

        @Test
        @DisplayName("Should leave non-numeric string unchanged for integer field")
        void shouldLeaveNonNumericStringUnchanged() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", "abc");

            TypeCoercionService.coerce(def, data);

            assertEquals("abc", data.get("count"));
        }

        @Test
        @DisplayName("Should leave Integer value unchanged")
        void shouldLeaveIntegerUnchanged() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", 42);

            TypeCoercionService.coerce(def, data);

            assertEquals(42, data.get("count"));
            assertInstanceOf(Integer.class, data.get("count"));
        }

        @Test
        @DisplayName("Should coerce Long to Integer when in range")
        void shouldCoerceLongToIntegerInRange() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", 42L);

            TypeCoercionService.coerce(def, data);

            assertEquals(42, data.get("count"));
            assertInstanceOf(Integer.class, data.get("count"));
        }

        @Test
        @DisplayName("Should coerce negative string to integer")
        void shouldCoerceNegativeStringToInteger() {
            CollectionDefinition def = createTestCollection(FieldDefinition.integer("count"));
            Map<String, Object> data = new HashMap<>();
            data.put("count", "-5");

            TypeCoercionService.coerce(def, data);

            assertEquals(-5, data.get("count"));
            assertInstanceOf(Integer.class, data.get("count"));
        }
    }

    @Nested
    @DisplayName("Long Coercion")
    class LongCoercionTests {

        @Test
        @DisplayName("Should coerce string to long")
        void shouldCoerceStringToLong() {
            CollectionDefinition def = createTestCollection(FieldDefinition.longField("bigNum"));
            Map<String, Object> data = new HashMap<>();
            data.put("bigNum", "9999999999");

            TypeCoercionService.coerce(def, data);

            assertEquals(9999999999L, data.get("bigNum"));
            assertInstanceOf(Long.class, data.get("bigNum"));
        }

        @Test
        @DisplayName("Should coerce Integer to Long")
        void shouldCoerceIntegerToLong() {
            CollectionDefinition def = createTestCollection(FieldDefinition.longField("bigNum"));
            Map<String, Object> data = new HashMap<>();
            data.put("bigNum", 42);

            TypeCoercionService.coerce(def, data);

            assertEquals(42L, data.get("bigNum"));
            assertInstanceOf(Long.class, data.get("bigNum"));
        }

        @Test
        @DisplayName("Should leave Long value unchanged")
        void shouldLeaveLongUnchanged() {
            CollectionDefinition def = createTestCollection(FieldDefinition.longField("bigNum"));
            Map<String, Object> data = new HashMap<>();
            data.put("bigNum", 42L);

            TypeCoercionService.coerce(def, data);

            assertEquals(42L, data.get("bigNum"));
            assertInstanceOf(Long.class, data.get("bigNum"));
        }
    }

    @Nested
    @DisplayName("Double Coercion")
    class DoubleCoercionTests {

        @Test
        @DisplayName("Should coerce string to double")
        void shouldCoerceStringToDouble() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", "10");

            TypeCoercionService.coerce(def, data);

            assertEquals(10.0, data.get("price"));
            assertInstanceOf(Double.class, data.get("price"));
        }

        @Test
        @DisplayName("Should coerce decimal string to double")
        void shouldCoerceDecimalStringToDouble() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", "99.99");

            TypeCoercionService.coerce(def, data);

            assertEquals(99.99, data.get("price"));
            assertInstanceOf(Double.class, data.get("price"));
        }

        @Test
        @DisplayName("Should coerce Integer to Double")
        void shouldCoerceIntegerToDouble() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", 10);

            TypeCoercionService.coerce(def, data);

            assertEquals(10.0, data.get("price"));
            assertInstanceOf(Double.class, data.get("price"));
        }

        @Test
        @DisplayName("Should coerce Long to Double")
        void shouldCoerceLongToDouble() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", 100L);

            TypeCoercionService.coerce(def, data);

            assertEquals(100.0, data.get("price"));
            assertInstanceOf(Double.class, data.get("price"));
        }

        @Test
        @DisplayName("Should leave Double unchanged")
        void shouldLeaveDoubleUnchanged() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", 99.99);

            TypeCoercionService.coerce(def, data);

            assertEquals(99.99, data.get("price"));
            assertInstanceOf(Double.class, data.get("price"));
        }

        @Test
        @DisplayName("Should leave non-numeric string unchanged for double field")
        void shouldLeaveNonNumericStringUnchangedForDouble() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", "not-a-number");

            TypeCoercionService.coerce(def, data);

            assertEquals("not-a-number", data.get("price"));
        }
    }

    @Nested
    @DisplayName("Currency and Percent Coercion")
    class CurrencyPercentCoercionTests {

        @Test
        @DisplayName("Should coerce string to double for CURRENCY field")
        void shouldCoerceStringForCurrencyField() {
            FieldDefinition field = new FieldDefinition(
                "amount", FieldType.CURRENCY, true, false, false, null, null, null, null, null
            );
            CollectionDefinition def = createTestCollection(field);
            Map<String, Object> data = new HashMap<>();
            data.put("amount", "199.50");

            TypeCoercionService.coerce(def, data);

            assertEquals(199.50, data.get("amount"));
            assertInstanceOf(Double.class, data.get("amount"));
        }

        @Test
        @DisplayName("Should coerce string to double for PERCENT field")
        void shouldCoerceStringForPercentField() {
            FieldDefinition field = new FieldDefinition(
                "rate", FieldType.PERCENT, true, false, false, null, null, null, null, null
            );
            CollectionDefinition def = createTestCollection(field);
            Map<String, Object> data = new HashMap<>();
            data.put("rate", "75.5");

            TypeCoercionService.coerce(def, data);

            assertEquals(75.5, data.get("rate"));
            assertInstanceOf(Double.class, data.get("rate"));
        }
    }

    @Nested
    @DisplayName("Boolean Coercion")
    class BooleanCoercionTests {

        @Test
        @DisplayName("Should coerce 'true' string to Boolean.TRUE")
        void shouldCoerceTrueString() {
            CollectionDefinition def = createTestCollection(FieldDefinition.bool("active"));
            Map<String, Object> data = new HashMap<>();
            data.put("active", "true");

            TypeCoercionService.coerce(def, data);

            assertEquals(Boolean.TRUE, data.get("active"));
            assertInstanceOf(Boolean.class, data.get("active"));
        }

        @Test
        @DisplayName("Should coerce 'false' string to Boolean.FALSE")
        void shouldCoerceFalseString() {
            CollectionDefinition def = createTestCollection(FieldDefinition.bool("active"));
            Map<String, Object> data = new HashMap<>();
            data.put("active", "false");

            TypeCoercionService.coerce(def, data);

            assertEquals(Boolean.FALSE, data.get("active"));
            assertInstanceOf(Boolean.class, data.get("active"));
        }

        @Test
        @DisplayName("Should coerce 'TRUE' string (case-insensitive)")
        void shouldCoerceTrueStringCaseInsensitive() {
            CollectionDefinition def = createTestCollection(FieldDefinition.bool("active"));
            Map<String, Object> data = new HashMap<>();
            data.put("active", "TRUE");

            TypeCoercionService.coerce(def, data);

            assertEquals(Boolean.TRUE, data.get("active"));
        }

        @Test
        @DisplayName("Should leave Boolean value unchanged")
        void shouldLeaveBooleanUnchanged() {
            CollectionDefinition def = createTestCollection(FieldDefinition.bool("active"));
            Map<String, Object> data = new HashMap<>();
            data.put("active", true);

            TypeCoercionService.coerce(def, data);

            assertEquals(Boolean.TRUE, data.get("active"));
            assertInstanceOf(Boolean.class, data.get("active"));
        }

        @Test
        @DisplayName("Should leave non-boolean string unchanged")
        void shouldLeaveNonBooleanStringUnchanged() {
            CollectionDefinition def = createTestCollection(FieldDefinition.bool("active"));
            Map<String, Object> data = new HashMap<>();
            data.put("active", "yes");

            TypeCoercionService.coerce(def, data);

            assertEquals("yes", data.get("active"));
        }
    }

    @Nested
    @DisplayName("String Fields — No Coercion")
    class StringFieldTests {

        @Test
        @DisplayName("Should not coerce string field values")
        void shouldNotCoerceStringFields() {
            CollectionDefinition def = createTestCollection(FieldDefinition.string("name"));
            Map<String, Object> data = new HashMap<>();
            data.put("name", "Hello");

            TypeCoercionService.coerce(def, data);

            assertEquals("Hello", data.get("name"));
        }

        @Test
        @DisplayName("Should not coerce numeric values in string fields")
        void shouldNotCoerceNumericValuesInStringFields() {
            CollectionDefinition def = createTestCollection(FieldDefinition.string("name"));
            Map<String, Object> data = new HashMap<>();
            data.put("name", 123);

            TypeCoercionService.coerce(def, data);

            assertEquals(123, data.get("name"));
        }
    }

    @Nested
    @DisplayName("Null and Missing Values")
    class NullAndMissingTests {

        @Test
        @DisplayName("Should skip null values")
        void shouldSkipNullValues() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", null);

            TypeCoercionService.coerce(def, data);

            assertNull(data.get("price"));
        }

        @Test
        @DisplayName("Should skip missing fields")
        void shouldSkipMissingFields() {
            CollectionDefinition def = createTestCollection(
                FieldDefinition.doubleField("price"),
                FieldDefinition.string("name")
            );
            Map<String, Object> data = new HashMap<>();
            data.put("name", "test");

            TypeCoercionService.coerce(def, data);

            assertFalse(data.containsKey("price"));
            assertEquals("test", data.get("name"));
        }
    }

    @Nested
    @DisplayName("Multi-Field Coercion")
    class MultiFieldTests {

        @Test
        @DisplayName("Should coerce multiple fields in one call")
        void shouldCoerceMultipleFields() {
            CollectionDefinition def = createTestCollection(
                FieldDefinition.string("name"),
                FieldDefinition.doubleField("price"),
                FieldDefinition.integer("quantity"),
                FieldDefinition.bool("active")
            );
            Map<String, Object> data = new HashMap<>();
            data.put("name", "test product");
            data.put("price", "10");
            data.put("quantity", "5");
            data.put("active", "true");

            TypeCoercionService.coerce(def, data);

            assertEquals("test product", data.get("name"));
            assertEquals(10.0, data.get("price"));
            assertInstanceOf(Double.class, data.get("price"));
            assertEquals(5, data.get("quantity"));
            assertInstanceOf(Integer.class, data.get("quantity"));
            assertEquals(Boolean.TRUE, data.get("active"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should throw NullPointerException when definition is null")
        void shouldThrowWhenDefinitionIsNull() {
            assertThrows(NullPointerException.class, () ->
                TypeCoercionService.coerce(null, new HashMap<>())
            );
        }

        @Test
        @DisplayName("Should throw NullPointerException when data is null")
        void shouldThrowWhenDataIsNull() {
            CollectionDefinition def = createTestCollection(FieldDefinition.string("name"));
            assertThrows(NullPointerException.class, () ->
                TypeCoercionService.coerce(def, null)
            );
        }

        @Test
        @DisplayName("Should handle empty data map")
        void shouldHandleEmptyDataMap() {
            CollectionDefinition def = createTestCollection(FieldDefinition.string("name"));
            Map<String, Object> data = new HashMap<>();

            TypeCoercionService.coerce(def, data);

            assertTrue(data.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty string for numeric field")
        void shouldHandleEmptyStringForNumericField() {
            CollectionDefinition def = createTestCollection(FieldDefinition.doubleField("price"));
            Map<String, Object> data = new HashMap<>();
            data.put("price", "");

            TypeCoercionService.coerce(def, data);

            // Empty string can't be parsed, left unchanged for validation to catch
            assertEquals("", data.get("price"));
        }
    }
}
