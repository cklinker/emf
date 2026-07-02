package io.kelta.worker.service;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.SchemaMigrationEngine;
import io.kelta.runtime.storage.SchemaMigrationEngine.AppliedChange;
import io.kelta.runtime.storage.SchemaMigrationEngine.SchemaDiff;
import io.kelta.runtime.storage.TableRef;
import io.kelta.worker.repository.MigrationFieldRepository;
import io.kelta.worker.repository.MigrationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a schema-migration plan: transforms a collection's <em>live</em> schema into a stored
 * target version, issuing real {@code ALTER TABLE}/{@code DROP COLUMN} on live tenant data. This is
 * the destructive half of the schema-migration planner and the highest-stakes DDL path in the
 * codebase — it is reachable only from {@code MigrationController.execute}, gated on
 * {@code CUSTOMIZE_APPLICATION}.
 *
 * <p>Every execute is a three-part transaction of concerns kept consistent in order:
 * <ol>
 *   <li><b>Restore point</b> — the current live schema is snapshotted as a new version first, so the
 *       run has a real {@code from_version} and the change is itself reversible by targeting it.</li>
 *   <li><b>Metadata</b> — the {@code field} table is synced to the target (rows removed/added/retyped).</li>
 *   <li><b>Physical + registry</b> — {@link SchemaMigrationEngine#migrateSchemaDestructive} applies the
 *       DDL, then the local {@link CollectionRegistry} is re-registered with the target and a
 *       {@code kelta.config.collection.changed} event is broadcast so other pods reload.</li>
 * </ol>
 *
 * <p>Type changes are gated: an incompatible {@code TYPE_CHANGED} is rejected up front with
 * {@link io.kelta.runtime.storage.IncompatibleSchemaChangeException} unless {@code force} is set.
 * {@code dryRun} returns the plan and applies nothing.
 *
 * @since 1.0.0
 */
@Service
public class MigrationExecutionService {

    private static final Logger log = LoggerFactory.getLogger(MigrationExecutionService.class);

    private static final String COLLECTION_CHANGED_SUBJECT_PREFIX = "kelta.config.collection.changed.";
    private static final int MAX_ERROR_LEN = 2000;

    private final CollectionVersionService versionService;
    private final SchemaMigrationEngine migrationEngine;
    private final CollectionRegistry collectionRegistry;
    private final MigrationRunRepository runRepository;
    private final MigrationFieldRepository fieldRepository;
    private final PlatformEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public MigrationExecutionService(CollectionVersionService versionService,
                                     SchemaMigrationEngine migrationEngine,
                                     CollectionRegistry collectionRegistry,
                                     MigrationRunRepository runRepository,
                                     MigrationFieldRepository fieldRepository,
                                     PlatformEventPublisher eventPublisher,
                                     ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.migrationEngine = migrationEngine;
        this.collectionRegistry = collectionRegistry;
        this.runRepository = runRepository;
        this.fieldRepository = fieldRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes (or, when {@code dryRun}, previews) a migration to {@code targetVersion}.
     *
     * @param collectionId  the collection to migrate
     * @param targetVersion the stored version to transform the live schema into
     * @param dryRun        when true, return the plan and apply nothing
     * @param force         when true, apply even incompatible type changes
     * @return a result map (dry-run plan, or the run outcome with steps)
     * @throws IllegalArgumentException if the collection or target version is unknown
     * @throws io.kelta.runtime.storage.IncompatibleSchemaChangeException
     *         if a type change is incompatible and {@code force} is false
     */
    public Map<String, Object> execute(String collectionId, int targetVersion, boolean dryRun, boolean force) {
        CollectionDefinition live = versionService.liveDefinition(collectionId);
        CollectionDefinition target = versionService.targetDefinition(collectionId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No stored version " + targetVersion + " for collection " + collectionId));

        List<SchemaDiff> diffs = migrationEngine.detectDifferences(live, target);

        // Gate incompatible type changes up front (defense-in-depth: the engine re-checks).
        for (SchemaDiff diff : diffs) {
            if (diff.diffType() == SchemaDiff.DiffType.TYPE_CHANGED
                    && !force
                    && !migrationEngine.isTypeChangeCompatible(diff.oldType(), diff.newType())) {
                FieldDefinition oldField = live.getField(diff.fieldName());
                FieldDefinition newField = target.getField(diff.fieldName());
                // Throws IncompatibleSchemaChangeException (→ 400 at the controller).
                migrationEngine.validateTypeChange(live.name(), oldField, newField);
            }
        }

        if (dryRun) {
            Map<String, Object> plan = versionService.buildPlan(collectionId, targetVersion)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No stored version " + targetVersion + " for collection " + collectionId));
            plan.put("dryRun", true);
            plan.put("applied", false);
            return plan;
        }

        if (diffs.isEmpty()) {
            // Already at the target schema — nothing to apply, and from==to would break the
            // migration_run version check. Report success without a run row.
            Map<String, Object> noop = baseResult(null, collectionId, live.name(),
                    versionService.latestVersion(collectionId), targetVersion, "completed", false);
            noop.put("steps", List.of());
            return noop;
        }

        // 1. Restore point: snapshot the current live schema as a new version.
        int fromVersion = versionService.snapshot(collectionId);
        String tenantId = TenantContext.get();
        String runId = runRepository.insertRun(tenantId, collectionId, fromVersion, targetVersion, "RUNNING");
        log.info("Migration run {} started: collection {} v{} -> v{} (force={})",
                runId, collectionId, fromVersion, targetVersion, force);

        try {
            // 2. Sync field metadata to the target.
            syncFieldMetadata(collectionId, live, target, diffs);

            // 3. Apply destructive DDL, then make the registry + other pods consistent.
            TableRef tableRef = tableRefFor(live);
            List<AppliedChange> applied =
                    migrationEngine.migrateSchemaDestructive(live, target, tableRef, force);
            collectionRegistry.register(target);
            broadcastCollectionChanged(collectionId, target, tenantId);

            // 4. Record per-step progress + complete the run.
            recordSteps(runId, applied);
            runRepository.updateRunStatus(runId, "COMPLETED", null);
            log.info("Migration run {} completed: {} change(s) applied", runId, applied.size());

            Map<String, Object> result = baseResult(runId, collectionId, live.name(),
                    fromVersion, targetVersion, "completed", false);
            result.put("steps", stepsFromApplied(applied, "completed"));
            return result;
        } catch (RuntimeException e) {
            String message = truncate(e.getMessage());
            log.error("Migration run {} failed: {}", runId, message, e);
            runRepository.updateRunStatus(runId, "FAILED", message);
            runRepository.insertStep(runId, 1, "MIGRATION", "FAILED", null, message);
            Map<String, Object> result = baseResult(runId, collectionId, live.name(),
                    fromVersion, targetVersion, "failed", false);
            result.put("error", message);
            result.put("steps", List.of(Map.of("order", 1, "operation", "MIGRATION",
                    "status", "failed", "errorMessage", message)));
            return result;
        }
    }

    // --- internals ----------------------------------------------------------

    private void syncFieldMetadata(String collectionId, CollectionDefinition live,
                                   CollectionDefinition target, List<SchemaDiff> diffs) {
        int order = countFields(target);
        for (SchemaDiff diff : diffs) {
            switch (diff.diffType()) {
                case FIELD_REMOVED -> fieldRepository.deleteField(collectionId, diff.fieldName());
                case FIELD_ADDED -> {
                    FieldDefinition added = target.getField(diff.fieldName());
                    if (added != null) {
                        fieldRepository.insertField(collectionId, added, order++);
                    }
                }
                case TYPE_CHANGED -> {
                    FieldDefinition changed = target.getField(diff.fieldName());
                    if (changed != null) {
                        fieldRepository.updateFieldType(collectionId, changed);
                    }
                }
            }
        }
    }

    private void recordSteps(String runId, List<AppliedChange> applied) {
        int stepNumber = 1;
        for (AppliedChange change : applied) {
            String details = serialize(Map.of(
                    "field", change.field(),
                    "type", change.type().name(),
                    "sql", change.sql()));
            runRepository.insertStep(runId, stepNumber++, change.operation(), "COMPLETED", details, null);
        }
    }

    private List<Map<String, Object>> stepsFromApplied(List<AppliedChange> applied, String status) {
        List<Map<String, Object>> steps = new ArrayList<>();
        int order = 1;
        for (AppliedChange change : applied) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("order", order++);
            step.put("operation", change.operation());
            step.put("field", change.field());
            step.put("status", status);
            step.put("reversible", "ADD_FIELD".equals(change.operation()));
            steps.add(step);
        }
        return steps;
    }

    private void broadcastCollectionChanged(String collectionId, CollectionDefinition def, String tenantId) {
        // Local read-after-write consistency is already handled by the direct registry.register(target)
        // above; the broadcast is the cross-pod backstop (each pod reloads the field table).
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(collectionId);
        payload.setName(def.name());
        payload.setChangeType(ChangeType.UPDATED);
        payload.setActive(true);
        PlatformEvent<CollectionChangedPayload> event =
                EventFactory.createEvent("kelta.config.collection.changed", payload);
        event.setTenantId(tenantId);
        eventPublisher.publish(COLLECTION_CHANGED_SUBJECT_PREFIX + collectionId, event);
    }

    /**
     * Resolves the schema-qualified table for the collection, matching
     * {@code PhysicalTableStorageAdapter.getTableRef}: system collections live in {@code public};
     * tenant collections live in the tenant-slug schema when a slug is bound.
     */
    private TableRef tableRefFor(CollectionDefinition def) {
        String tableName = def.storageConfig() != null && def.storageConfig().tableName() != null
                ? def.storageConfig().tableName() : def.name();
        if (def.systemCollection()) {
            return TableRef.publicSchema(tableName);
        }
        String slug = TenantContext.getSlug();
        if (slug != null && !slug.isBlank()) {
            return TableRef.tenantSchema(slug, tableName);
        }
        return TableRef.publicSchema(tableName);
    }

    private int countFields(CollectionDefinition def) {
        return def.fields() == null ? 0 : def.fields().size();
    }

    private Map<String, Object> baseResult(String runId, String collectionId, String collectionName,
                                           int fromVersion, int toVersion, String status, boolean dryRun) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("id", runId);
        result.put("collectionId", collectionId);
        result.put("collectionName", collectionName);
        result.put("fromVersion", fromVersion);
        result.put("toVersion", toVersion);
        result.put("status", status);
        result.put("dryRun", dryRun);
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Migration failed";
        }
        return message.length() > MAX_ERROR_LEN ? message.substring(0, MAX_ERROR_LEN) : message;
    }
}
