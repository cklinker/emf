package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.CollectionDefinitionBuilder;
import io.kelta.runtime.model.FieldDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The captureGeo flag flip must ensure the geo system columns via an idempotent
 * {@code ADD COLUMN IF NOT EXISTS} (JSONB is Postgres-only DDL, so this trigger logic is
 * verified against a mocked JdbcTemplate; the real-database round-trip is covered by the
 * kelta-test-harness GeoCaptureScenarioTest).
 */
@DisplayName("SchemaMigrationEngine captureGeo Tests")
class SchemaMigrationEngineGeoTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SchemaMigrationEngine engine = new SchemaMigrationEngine(jdbcTemplate);

    private CollectionDefinition def(boolean captureGeo) {
        return new CollectionDefinitionBuilder()
                .name("things")
                .displayName("Things")
                .captureGeo(captureGeo)
                .addField(FieldDefinition.requiredString("name"))
                .build();
    }

    @Test
    @DisplayName("flipping captureGeo on issues the idempotent geo-column ALTER")
    void flagFlipEnsuresGeoColumns() {
        engine.migrateSchema(def(false), def(true));

        verify(jdbcTemplate).execute(contains("ADD COLUMN IF NOT EXISTS created_geo JSONB"));
        verify(jdbcTemplate).execute(contains("ADD COLUMN IF NOT EXISTS updated_geo JSONB"));
    }

    @Test
    @DisplayName("no ALTER when the flag is unchanged or turned off")
    void noAlterWithoutFlip() {
        engine.migrateSchema(def(false), def(false));
        engine.migrateSchema(def(true), def(true));
        engine.migrateSchema(def(true), def(false));

        verify(jdbcTemplate, never()).execute(contains("created_geo"));
    }

    @Test
    @DisplayName("event redelivery re-runs the ALTER harmlessly (IF NOT EXISTS)")
    void redeliveryIsIdempotent() {
        engine.migrateSchema(def(false), def(true));
        engine.migrateSchema(def(false), def(true));

        verify(jdbcTemplate, times(2)).execute(contains("ADD COLUMN IF NOT EXISTS created_geo"));
    }
}
