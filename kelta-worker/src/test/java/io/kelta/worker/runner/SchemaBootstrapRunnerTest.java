package io.kelta.worker.runner;

import io.kelta.worker.service.CollectionLifecycleManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaBootstrapRunner")
class SchemaBootstrapRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CollectionLifecycleManager lifecycleManager;

    @InjectMocks
    private SchemaBootstrapRunner runner;

    @Test
    @DisplayName("applies schema for every active collection")
    void appliesSchemaForEachCollection() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", "col-1", "name", "accounts"),
                Map.of("id", "col-2", "name", "contacts")
        ));

        assertDoesNotThrow(() -> runner.run(null));

        verify(lifecycleManager).initializeCollectionOrThrow("col-1", true);
        verify(lifecycleManager).initializeCollectionOrThrow("col-2", true);
    }

    @Test
    @DisplayName("throws so the migrate Job fails and the worker rollout never starts")
    void throwsOnFailureToFailTheJob() {
        // backoffLimit: 0 on the Job + PreSync hook means this exception is what stops ArgoCD
        // from rolling out workers against a schema that was never fully applied.
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", "col-1", "name", "watchlists")
        ));
        doThrow(new RuntimeException("syntax error at or near \"user\""))
                .when(lifecycleManager).initializeCollectionOrThrow("col-1", true);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertTrue(thrown.getMessage().contains("watchlists"),
                "the failure must name the offending collection: " + thrown.getMessage());
    }

    @Test
    @DisplayName("reports every broken collection in one run, not one per deploy")
    void collectsAllFailuresBeforeThrowing() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", "col-1", "name", "watchlists"),
                Map.of("id", "col-2", "name", "alerts"),
                Map.of("id", "col-3", "name", "healthy")
        ));
        doThrow(new RuntimeException("boom"))
                .when(lifecycleManager).initializeCollectionOrThrow("col-1", true);
        doThrow(new RuntimeException("boom"))
                .when(lifecycleManager).initializeCollectionOrThrow("col-2", true);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> runner.run(null));

        assertTrue(thrown.getMessage().contains("watchlists"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("alerts"), thrown.getMessage());
        // The healthy collection is still attempted rather than skipped after the first failure.
        verify(lifecycleManager).initializeCollectionOrThrow("col-3", true);
    }

    @Test
    @DisplayName("succeeds when there are no collections")
    void succeedsWithNoCollections() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        assertDoesNotThrow(() -> runner.run(null));
    }

    @Test
    @DisplayName("runs in the migrate profile, ordered before the runner that exits the JVM")
    void isOrderedBeforeMigrateShutdown() {
        // MigrateShutdownRunner calls System.exit(0) once the runners finish. If this one
        // ever sorts after it, schema bootstrap silently never executes and the Job still
        // reports success — the exact failure mode this class exists to prevent.
        org.springframework.context.annotation.Profile profile =
                SchemaBootstrapRunner.class.getAnnotation(
                        org.springframework.context.annotation.Profile.class);
        assertTrue(profile != null && List.of(profile.value()).contains("migrate"),
                "SchemaBootstrapRunner must be confined to the migrate profile");

        int bootstrapOrder = SchemaBootstrapRunner.class
                .getAnnotation(org.springframework.core.annotation.Order.class).value();
        int shutdownOrder = MigrateShutdownRunner.class
                .getAnnotation(org.springframework.core.annotation.Order.class).value();
        assertTrue(bootstrapOrder < shutdownOrder,
                "schema bootstrap (" + bootstrapOrder + ") must run before the JVM exit ("
                        + shutdownOrder + ")");
    }
}
