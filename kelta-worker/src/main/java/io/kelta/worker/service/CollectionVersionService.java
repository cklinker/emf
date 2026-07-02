package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.CollectionVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema-migration planning support: snapshots a collection's current schema into
 * {@code collection_version}, and builds a <em>read-only</em> plan diffing the current live
 * schema against a stored target version.
 *
 * <p>This service is deliberately non-destructive — it never issues DDL. The diff is computed
 * inline (field name + type) so the plan/preview path can never reach the DDL-capable
 * {@code SchemaMigrationEngine}. Applying a plan (destructive {@code ALTER}/drop) is a separate,
 * guarded path.
 *
 * @since 1.0.0
 */
@Service
public class CollectionVersionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionVersionService.class);

    private final CollectionVersionRepository versionRepository;
    private final CollectionRegistry collectionRegistry;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CollectionVersionService(CollectionVersionRepository versionRepository,
                                    CollectionRegistry collectionRegistry,
                                    JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.collectionRegistry = collectionRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** A minimal serialized field, sufficient to diff schemas (name + type + nullability). */
    public record FieldSnapshot(String name, String type, boolean nullable) {
    }

    /**
     * Captures the collection's current schema as a new version snapshot.
     *
     * @param collectionId the collection id
     * @return the new version number
     * @throws IllegalArgumentException if the collection is unknown
     */
    public int snapshot(String collectionId) {
        CollectionDefinition def = requireDefinition(collectionId);
        List<FieldSnapshot> fields = toSnapshot(def);
        String json = objectMapper.writeValueAsString(fields);
        int version = versionRepository.nextVersion(collectionId);
        versionRepository.insertSnapshot(collectionId, version, json);
        log.info("Snapshotted collection {} as version {}", collectionId, version);
        return version;
    }

    /** Lists a collection's stored versions (newest first). */
    public List<Map<String, Object>> listVersions(String collectionId) {
        return versionRepository.listVersions(collectionId);
    }

    /**
     * Builds a read-only migration plan transforming the collection's CURRENT live schema into the
     * stored {@code targetVersion}. Never mutates anything.
     *
     * @param collectionId  the collection id
     * @param targetVersion the target stored version
     * @return the plan (steps + risks + estimates), or empty if the target version does not exist
     */
    public Optional<Map<String, Object>> buildPlan(String collectionId, int targetVersion) {
        CollectionDefinition def = requireDefinition(collectionId);
        Optional<String> targetJson = versionRepository.findSchema(collectionId, targetVersion);
        if (targetJson.isEmpty()) {
            return Optional.empty();
        }
        List<FieldSnapshot> current = toSnapshot(def);
        List<FieldSnapshot> target = parseSnapshot(targetJson.get());

        Map<String, FieldSnapshot> currentByName = new LinkedHashMap<>();
        current.forEach(f -> currentByName.put(f.name(), f));
        Map<String, FieldSnapshot> targetByName = new LinkedHashMap<>();
        target.forEach(f -> targetByName.put(f.name(), f));

        List<Map<String, Object>> steps = new ArrayList<>();
        List<Map<String, Object>> risks = new ArrayList<>();
        int order = 1;

        // Fields present in target but not current → ADD (low risk).
        for (FieldSnapshot t : target) {
            if (!currentByName.containsKey(t.name())) {
                steps.add(step(order++, "ADD_FIELD", Map.of("field", t.name(), "type", t.type()), true));
            }
        }
        // Fields present in current but not target → REMOVE (destructive, high risk).
        for (FieldSnapshot c : current) {
            if (!targetByName.containsKey(c.name())) {
                steps.add(step(order++, "REMOVE_FIELD", Map.of("field", c.name(), "type", c.type()), false));
                risks.add(risk("high", "Removing field '" + c.name() + "' drops its column and all its data."));
            }
        }
        // Fields in both with a changed type → MODIFY (risk depends on compatibility).
        for (FieldSnapshot c : current) {
            FieldSnapshot t = targetByName.get(c.name());
            if (t != null && !c.type().equals(t.type())) {
                steps.add(step(order++, "MODIFY_FIELD",
                        Map.of("field", c.name(), "fromType", c.type(), "toType", t.type()), false));
                risks.add(risk("high",
                        "Changing '" + c.name() + "' from " + c.type() + " to " + t.type()
                                + " may fail or lose data if values are incompatible."));
            }
        }

        long recordCount = countRecords(def);

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", collectionId + ":" + targetVersion);
        plan.put("collectionId", collectionId);
        plan.put("collectionName", def.name());
        plan.put("fromVersion", Math.max(versionRepository.nextVersion(collectionId) - 1, 0));
        plan.put("toVersion", targetVersion);
        plan.put("steps", steps);
        plan.put("estimatedDuration", steps.size()); // seconds, coarse
        plan.put("estimatedRecordsAffected", recordCount);
        plan.put("risks", risks);
        return Optional.of(plan);
    }

    // --- helpers ------------------------------------------------------------

    private CollectionDefinition requireDefinition(String collectionId) {
        String name = jdbcTemplate.query(
                "SELECT name FROM collection WHERE id = ?",
                rs -> rs.next() ? rs.getString("name") : null,
                collectionId);
        if (name == null) {
            throw new IllegalArgumentException("Collection not found: " + collectionId);
        }
        CollectionDefinition def = collectionRegistry.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Collection not loaded in registry: " + name);
        }
        return def;
    }

    private List<FieldSnapshot> toSnapshot(CollectionDefinition def) {
        List<FieldSnapshot> out = new ArrayList<>();
        if (def.fields() != null) {
            for (FieldDefinition f : def.fields()) {
                out.add(new FieldSnapshot(f.name(), f.type().name(), f.nullable()));
            }
        }
        return out;
    }

    private List<FieldSnapshot> parseSnapshot(String json) {
        return objectMapper.readValue(json, new TypeReference<List<FieldSnapshot>>() {});
    }

    private long countRecords(CollectionDefinition def) {
        // Best-effort record count; a failure here must not break planning.
        try {
            String table = def.storageConfig() != null && def.storageConfig().tableName() != null
                    ? def.storageConfig().tableName() : null;
            if (table == null || !table.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                return 0L;
            }
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            return count != null ? count : 0L;
        } catch (RuntimeException e) {
            log.debug("Record count unavailable for {}: {}", def.name(), e.getMessage());
            return 0L;
        }
    }

    private static Map<String, Object> step(int order, String operation, Map<String, Object> details,
                                            boolean reversible) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("order", order);
        s.put("operation", operation);
        s.put("details", details);
        s.put("reversible", reversible);
        return s;
    }

    private static Map<String, Object> risk(String level, String description) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("level", level);
        r.put("description", description);
        return r;
    }
}
