package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.model.StorageConfig;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.IncompatibleSchemaChangeException;
import io.kelta.runtime.storage.SchemaMigrationEngine;
import io.kelta.runtime.storage.SchemaMigrationEngine.AppliedChange;
import io.kelta.runtime.storage.SchemaMigrationEngine.MigrationType;
import io.kelta.runtime.storage.SchemaMigrationEngine.SchemaDiff;
import io.kelta.runtime.storage.TableRef;
import io.kelta.worker.repository.MigrationFieldRepository;
import io.kelta.worker.repository.MigrationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MigrationExecutionService")
class MigrationExecutionServiceTest {

    private CollectionVersionService versionService;
    private SchemaMigrationEngine engine;
    private CollectionRegistry registry;
    private MigrationRunRepository runRepository;
    private MigrationFieldRepository fieldRepository;
    private CollectionLifecycleManager lifecycleManager;
    private PlatformEventPublisher eventPublisher;
    private io.kelta.runtime.router.SystemCollectionCache systemCollectionCache;
    private MigrationExecutionService service;

    @BeforeEach
    void setUp() {
        versionService = mock(CollectionVersionService.class);
        engine = mock(SchemaMigrationEngine.class);
        registry = mock(CollectionRegistry.class);
        runRepository = mock(MigrationRunRepository.class);
        fieldRepository = mock(MigrationFieldRepository.class);
        lifecycleManager = mock(CollectionLifecycleManager.class);
        eventPublisher = mock(PlatformEventPublisher.class);
        systemCollectionCache = mock(io.kelta.runtime.router.SystemCollectionCache.class);
        service = new MigrationExecutionService(versionService, engine, registry, runRepository,
                fieldRepository, lifecycleManager, eventPublisher, systemCollectionCache, new ObjectMapper());
    }

    private CollectionDefinition def(String name, List<FieldDefinition> fields) {
        return CollectionDefinition.builder()
                .name(name)
                .storageConfig(StorageConfig.physicalTable(name))
                .fields(fields)
                .build();
    }

    /** Runs the op with a bound tenant so TenantContext.get()/getSlug() are populated. */
    private <T> T withTenant(ScopedValue.CallableOp<T, RuntimeException> op) {
        return TenantContext.callWithTenant("t1", "acme", op);
    }

    @Test
    @DisplayName("dryRun returns the plan and applies nothing")
    void dryRunAppliesNothing() throws Exception {
        CollectionDefinition live = def("orders", List.of(
                FieldDefinition.string("name"), FieldDefinition.integer("amount")));
        CollectionDefinition target = def("orders", List.of(FieldDefinition.string("name")));
        when(versionService.liveDefinition("c1")).thenReturn(live);
        when(versionService.targetDefinition("c1", 1)).thenReturn(Optional.of(target));
        when(engine.detectDifferences(live, target)).thenReturn(List.of(
                new SchemaDiff(SchemaDiff.DiffType.FIELD_REMOVED, "amount", FieldType.INTEGER, null)));
        when(versionService.buildPlan("c1", 1)).thenReturn(Optional.of(new java.util.LinkedHashMap<>(
                Map.of("steps", List.of(Map.of("operation", "REMOVE_FIELD"))))));

        Map<String, Object> result = withTenant(() -> service.execute("c1", 1, true, false));

        assertThat(result).containsEntry("dryRun", true).containsEntry("applied", false);
        verify(engine, never()).migrateSchemaDestructive(any(), any(), any(), anyBoolean());
        verify(runRepository, never()).insertRun(any(), any(), anyInt(), anyInt(), any());
        verify(fieldRepository, never()).deleteField(any(), any());
    }

    @Test
    @DisplayName("rejects an incompatible type change when not forced (no run row)")
    void rejectsIncompatibleTypeChange() throws Exception {
        CollectionDefinition live = def("orders", List.of(FieldDefinition.bool("flag")));
        CollectionDefinition target = def("orders", List.of(FieldDefinition.integer("flag")));
        when(versionService.liveDefinition("c1")).thenReturn(live);
        when(versionService.targetDefinition("c1", 1)).thenReturn(Optional.of(target));
        when(engine.detectDifferences(live, target)).thenReturn(List.of(
                new SchemaDiff(SchemaDiff.DiffType.TYPE_CHANGED, "flag", FieldType.BOOLEAN, FieldType.INTEGER)));
        when(engine.isTypeChangeCompatible(FieldType.BOOLEAN, FieldType.INTEGER)).thenReturn(false);
        doThrow(new IncompatibleSchemaChangeException("orders", "flag", FieldType.BOOLEAN, FieldType.INTEGER))
                .when(engine).validateTypeChange(eq("orders"), any(), any());

        assertThatThrownBy(() -> withTenant(() -> service.execute("c1", 1, false, false)))
                .isInstanceOf(IncompatibleSchemaChangeException.class);
        verify(runRepository, never()).insertRun(any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("executes a drop: snapshots restore point, syncs field metadata, applies DDL, completes run")
    void executesDrop() throws Exception {
        CollectionDefinition live = def("orders", List.of(
                FieldDefinition.string("name"), FieldDefinition.integer("amount")));
        CollectionDefinition target = def("orders", List.of(FieldDefinition.string("name")));
        when(versionService.liveDefinition("c1")).thenReturn(live);
        when(versionService.targetDefinition("c1", 1)).thenReturn(Optional.of(target));
        when(engine.detectDifferences(live, target)).thenReturn(List.of(
                new SchemaDiff(SchemaDiff.DiffType.FIELD_REMOVED, "amount", FieldType.INTEGER, null)));
        when(versionService.snapshot("c1")).thenReturn(2);
        when(runRepository.insertRun("t1", "c1", 2, 1, "RUNNING")).thenReturn("run-1");
        when(engine.migrateSchemaDestructive(eq(live), eq(target), any(TableRef.class), eq(false)))
                .thenReturn(List.of(new AppliedChange("REMOVE_FIELD", "amount", MigrationType.DROP_COLUMN,
                        "ALTER TABLE \"acme\".\"orders\" DROP COLUMN IF EXISTS amount")));

        Map<String, Object> result = withTenant(() -> service.execute("c1", 1, false, false));

        assertThat(result).containsEntry("runId", "run-1").containsEntry("status", "completed");

        // Registry is made DB-consistent before the diff (avoids a stale-registry false no-op).
        verify(lifecycleManager).refreshOrInitializeLocally("c1");
        // Restore point captured before the destructive change.
        verify(versionService).snapshot("c1");
        // field metadata synced (row removed).
        verify(fieldRepository).deleteField("c1", "amount");
        // destructive DDL applied against the tenant-schema table.
        ArgumentCaptor<TableRef> ref = ArgumentCaptor.forClass(TableRef.class);
        verify(engine).migrateSchemaDestructive(eq(live), eq(target), ref.capture(), eq(false));
        assertThat(ref.getValue().schema()).isEqualTo("acme");
        assertThat(ref.getValue().tableName()).isEqualTo("orders");
        // registry re-registered with the target + broadcast + run completed.
        verify(registry).register(target);
        verify(eventPublisher).publish(startsWith("kelta.config.collection.changed."), any());
        // Stale system-collection list cache (GET /api/fields) is evicted after the raw metadata writes.
        verify(systemCollectionCache).evict("t1", "fields");
        // The gateway's /api/fields response cache is evicted via record.changed — the raw
        // metadata writes publish it explicitly (the CRUD path would have done it for us).
        verify(eventPublisher).publish(eq("kelta.record.changed.t1.fields"), any());
        verify(runRepository).insertStep(eq("run-1"), eq(1), eq("REMOVE_FIELD"), eq("COMPLETED"), any(), isNull());
        verify(runRepository).updateRunStatus("run-1", "COMPLETED", null);
    }

    @Test
    @DisplayName("a DDL failure marks the run FAILED and records a failed step")
    void ddlFailureMarksRunFailed() throws Exception {
        CollectionDefinition live = def("orders", List.of(
                FieldDefinition.string("name"), FieldDefinition.integer("amount")));
        CollectionDefinition target = def("orders", List.of(FieldDefinition.string("name")));
        when(versionService.liveDefinition("c1")).thenReturn(live);
        when(versionService.targetDefinition("c1", 1)).thenReturn(Optional.of(target));
        when(engine.detectDifferences(live, target)).thenReturn(List.of(
                new SchemaDiff(SchemaDiff.DiffType.FIELD_REMOVED, "amount", FieldType.INTEGER, null)));
        when(versionService.snapshot("c1")).thenReturn(2);
        when(runRepository.insertRun("t1", "c1", 2, 1, "RUNNING")).thenReturn("run-1");
        when(engine.migrateSchemaDestructive(any(), any(), any(), anyBoolean()))
                .thenThrow(new io.kelta.runtime.storage.StorageException("boom"));

        Map<String, Object> result = withTenant(() -> service.execute("c1", 1, false, false));

        assertThat(result).containsEntry("status", "failed");
        verify(runRepository).updateRunStatus(eq("run-1"), eq("FAILED"), contains("boom"));
        verify(runRepository).insertStep(eq("run-1"), eq(1), eq("MIGRATION"), eq("FAILED"), isNull(), contains("boom"));
    }
}
