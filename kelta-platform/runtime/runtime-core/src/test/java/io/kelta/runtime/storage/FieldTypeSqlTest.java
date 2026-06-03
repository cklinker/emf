package io.kelta.runtime.storage;

import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldTypeSql")
class FieldTypeSqlTest {

    @Test
    @DisplayName("TEXT and RICH_TEXT both map to TEXT column type")
    void textAndRichTextMapToTextColumn() {
        assertThat(FieldTypeSql.mapFieldTypeToSql(FieldType.TEXT)).isEqualTo("TEXT");
        assertThat(FieldTypeSql.mapFieldTypeToSql(FieldType.RICH_TEXT)).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("VECTOR enum-only mapping uses default dimension")
    void vectorEnumOnlyUsesDefaultDimension() {
        assertThat(FieldTypeSql.mapFieldTypeToSql(FieldType.VECTOR))
                .isEqualTo("vector(1536)");
    }

    @Test
    @DisplayName("VECTOR field-aware mapping honours dimension from config")
    void vectorMapsConfiguredDimension() {
        FieldDefinition field = vectorField("embedding", 768);
        assertThat(FieldTypeSql.mapFieldTypeToSql(field)).isEqualTo("vector(768)");
    }

    @Test
    @DisplayName("VECTOR without dimension falls back to default")
    void vectorWithoutDimensionUsesDefault() {
        FieldDefinition field = new FieldDefinition(
                "embedding", FieldType.VECTOR, true, false, false,
                null, null, null, null, null);
        assertThat(FieldTypeSql.mapFieldTypeToSql(field)).isEqualTo("vector(1536)");
    }

    @Test
    @DisplayName("VECTOR rejects dimension below 1")
    void vectorRejectsZeroDimension() {
        FieldDefinition field = vectorField("embedding", 0);
        assertThatThrownBy(() -> FieldTypeSql.mapFieldTypeToSql(field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    @DisplayName("VECTOR rejects dimension above 16000")
    void vectorRejectsHugeDimension() {
        FieldDefinition field = vectorField("embedding", 16_001);
        assertThatThrownBy(() -> FieldTypeSql.mapFieldTypeToSql(field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    @DisplayName("VECTOR rejects non-integer dimension")
    void vectorRejectsBadDimension() {
        FieldDefinition field = new FieldDefinition(
                "embedding", FieldType.VECTOR, true, false, false,
                null, null, null, null,
                Map.of("dimension", "not-a-number"), null);
        assertThatThrownBy(() -> FieldTypeSql.mapFieldTypeToSql(field))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Non-VECTOR field-aware mapping delegates to enum mapping")
    void nonVectorFieldUsesEnumMapping() {
        FieldDefinition field = new FieldDefinition(
                "synopsis", FieldType.TEXT, true, false, false,
                null, null, null, null, null);
        assertThat(FieldTypeSql.mapFieldTypeToSql(field)).isEqualTo("TEXT");
    }

    private static FieldDefinition vectorField(String name, int dimension) {
        return new FieldDefinition(
                name, FieldType.VECTOR, true, false, false,
                null, null, null, null,
                Map.of("dimension", dimension), null);
    }
}
