package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FieldType} enum.
 * 
 * Validates: Requirements 1.5 - Field types including string, integer, long, double, boolean, date, datetime, and JSON
 */
@DisplayName("FieldType Enum Tests")
class FieldTypeTest {

    @Test
    @DisplayName("Should have all required field types")
    void shouldHaveAllRequiredFieldTypes() {
        // Verify all required types exist per Requirement 1.5
        assertNotNull(FieldType.STRING);
        assertNotNull(FieldType.INTEGER);
        assertNotNull(FieldType.LONG);
        assertNotNull(FieldType.DOUBLE);
        assertNotNull(FieldType.BOOLEAN);
        assertNotNull(FieldType.DATE);
        assertNotNull(FieldType.DATETIME);
        assertNotNull(FieldType.JSON);
    }

    @Test
    @DisplayName("Should have exactly 8 field types")
    void shouldHaveExactlyEightFieldTypes() {
        assertEquals(8, FieldType.values().length);
    }

    @Test
    @DisplayName("Should be able to convert from string")
    void shouldConvertFromString() {
        assertEquals(FieldType.STRING, FieldType.valueOf("STRING"));
        assertEquals(FieldType.INTEGER, FieldType.valueOf("INTEGER"));
        assertEquals(FieldType.LONG, FieldType.valueOf("LONG"));
        assertEquals(FieldType.DOUBLE, FieldType.valueOf("DOUBLE"));
        assertEquals(FieldType.BOOLEAN, FieldType.valueOf("BOOLEAN"));
        assertEquals(FieldType.DATE, FieldType.valueOf("DATE"));
        assertEquals(FieldType.DATETIME, FieldType.valueOf("DATETIME"));
        assertEquals(FieldType.JSON, FieldType.valueOf("JSON"));
    }
}
