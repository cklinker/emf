package io.kelta.worker.service;

import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CollectionLifecycleManager.resolveReferenceTarget (lookup display fix)")
class CollectionLifecycleManagerReferenceTargetTest {

    private JdbcTemplate jdbcTemplate;
    private CollectionLifecycleManager manager;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        manager = new CollectionLifecycleManager(
                mock(CollectionRegistry.class),
                mock(StorageAdapter.class),
                jdbcTemplate,
                new ObjectMapper());
    }

    @Test
    @DisplayName("prefers the denormalized reference_target without hitting the database")
    void prefersDenormalizedTarget() {
        String target = manager.resolveReferenceTarget("titles", "collection-id-1");

        assertThat(target).isEqualTo("titles");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("derives the target name from reference_collection_id when reference_target is null")
    void derivesFromCollectionId() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq("col-titles")))
                .thenReturn("titles");

        assertThat(manager.resolveReferenceTarget(null, "col-titles")).isEqualTo("titles");
        assertThat(manager.resolveReferenceTarget("   ", "col-titles")).isEqualTo("titles");
    }

    @Test
    @DisplayName("returns null when neither a target name nor a collection id is available")
    void nullWhenNothingToResolve() {
        assertThat(manager.resolveReferenceTarget(null, null)).isNull();
        assertThat(manager.resolveReferenceTarget("", null)).isNull();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("returns null (never throws) when the referenced collection row is missing")
    void nullWhenCollectionMissing() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(String.class), eq("ghost")))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(manager.resolveReferenceTarget(null, "ghost")).isNull();
    }
}
