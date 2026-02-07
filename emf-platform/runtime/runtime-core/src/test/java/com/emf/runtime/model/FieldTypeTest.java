package com.emf.runtime.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FieldType} enum.
 */
@DisplayName("FieldType Enum Tests")
class FieldTypeTest {

    @Test
    @DisplayName("Should have all original field types")
    void shouldHaveAllOriginalFieldTypes() {
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
    @DisplayName("Should have all Phase 2 field types")
    void shouldHaveAllPhase2FieldTypes() {
        assertNotNull(FieldType.REFERENCE);
        assertNotNull(FieldType.ARRAY);
        assertNotNull(FieldType.PICKLIST);
        assertNotNull(FieldType.MULTI_PICKLIST);
        assertNotNull(FieldType.CURRENCY);
        assertNotNull(FieldType.PERCENT);
        assertNotNull(FieldType.AUTO_NUMBER);
        assertNotNull(FieldType.PHONE);
        assertNotNull(FieldType.EMAIL);
        assertNotNull(FieldType.URL);
        assertNotNull(FieldType.RICH_TEXT);
        assertNotNull(FieldType.ENCRYPTED);
        assertNotNull(FieldType.EXTERNAL_ID);
        assertNotNull(FieldType.GEOLOCATION);
        assertNotNull(FieldType.LOOKUP);
        assertNotNull(FieldType.MASTER_DETAIL);
        assertNotNull(FieldType.FORMULA);
        assertNotNull(FieldType.ROLLUP_SUMMARY);
    }

    @Test
    @DisplayName("Should have exactly 26 field types")
    void shouldHaveExactly26FieldTypes() {
        assertEquals(26, FieldType.values().length);
    }

    @Test
    @DisplayName("FORMULA has no physical column")
    void formulaHasNoPhysicalColumn() {
        assertFalse(FieldType.FORMULA.hasPhysicalColumn());
    }

    @Test
    @DisplayName("ROLLUP_SUMMARY has no physical column")
    void rollupSummaryHasNoPhysicalColumn() {
        assertFalse(FieldType.ROLLUP_SUMMARY.hasPhysicalColumn());
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"FORMULA", "ROLLUP_SUMMARY"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("All non-computed types have physical columns")
    void allNonComputedTypesHavePhysicalColumn(FieldType type) {
        assertTrue(type.hasPhysicalColumn(), type + " should have a physical column");
    }

    @Test
    @DisplayName("CURRENCY has companion columns")
    void currencyHasCompanionColumns() {
        assertTrue(FieldType.CURRENCY.hasCompanionColumns());
    }

    @Test
    @DisplayName("GEOLOCATION has companion columns")
    void geolocationHasCompanionColumns() {
        assertTrue(FieldType.GEOLOCATION.hasCompanionColumns());
    }

    @Test
    @DisplayName("STRING does not have companion columns")
    void stringDoesNotHaveCompanionColumns() {
        assertFalse(FieldType.STRING.hasCompanionColumns());
    }

    @Test
    @DisplayName("Relationship types identified correctly")
    void relationshipTypes() {
        assertTrue(FieldType.REFERENCE.isRelationship());
        assertTrue(FieldType.LOOKUP.isRelationship());
        assertTrue(FieldType.MASTER_DETAIL.isRelationship());
        assertFalse(FieldType.STRING.isRelationship());
        assertFalse(FieldType.PICKLIST.isRelationship());
    }

    @Test
    @DisplayName("Should be able to convert from string")
    void shouldConvertFromString() {
        assertEquals(FieldType.STRING, FieldType.valueOf("STRING"));
        assertEquals(FieldType.PICKLIST, FieldType.valueOf("PICKLIST"));
        assertEquals(FieldType.FORMULA, FieldType.valueOf("FORMULA"));
    }
}
