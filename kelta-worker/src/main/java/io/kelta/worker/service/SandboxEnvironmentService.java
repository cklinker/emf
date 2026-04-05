package io.kelta.worker.service;

import io.kelta.worker.repository.EnvironmentRepository;
import io.kelta.worker.repository.PackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Service
public class SandboxEnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(SandboxEnvironmentService.class);
    private static final String KAFKA_TOPIC_ENV_CHANGED = "kelta.config.environment.changed";

    private final EnvironmentRepository environmentRepository;
    private final PackageRepository packageRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SandboxEnvironmentService(EnvironmentRepository environmentRepository,
                                     PackageRepository packageRepository,
                                     ObjectMapper objectMapper,
                                     KafkaTemplate<String, String> kafkaTemplate) {
        this.environmentRepository = environmentRepository;
        this.packageRepository = packageRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Map<String, Object> ensureProductionEnvironment(String tenantId, String createdBy) {
        var existing = environmentRepository.findProductionByTenant(tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        String id = environmentRepository.create(tenantId, "Production", "Default production environment",
                "PRODUCTION", null, null, createdBy);
        environmentRepository.updateStatus(id, tenantId, "ACTIVE");
        log.info("Created production environment for tenant {}: {}", tenantId, id);

        var env = environmentRepository.findByIdAndTenant(id, tenantId);
        return env.orElseThrow();
    }

    public Map<String, Object> createSandbox(String tenantId, String name, String description,
                                             String sourceEnvId, String config, String createdBy) {
        if (environmentRepository.existsByTenantAndName(tenantId, name)) {
            throw new IllegalArgumentException("Environment with name '" + name + "' already exists");
        }

        // Ensure production environment exists as source if none specified
        if (sourceEnvId == null) {
            var production = ensureProductionEnvironment(tenantId, createdBy);
            sourceEnvId = (String) production.get("id");
        } else {
            var source = environmentRepository.findByIdAndTenant(sourceEnvId, tenantId);
            if (source.isEmpty()) {
                throw new IllegalArgumentException("Source environment not found: " + sourceEnvId);
            }
        }

        String envId = environmentRepository.create(tenantId, name, description, "SANDBOX",
                sourceEnvId, config, createdBy);

        // Clone metadata from source environment asynchronously
        cloneMetadata(tenantId, sourceEnvId, envId, createdBy);

        var env = environmentRepository.findByIdAndTenant(envId, tenantId);
        publishEnvironmentEvent(tenantId, envId, "CREATED");
        return env.orElseThrow();
    }

    public Map<String, Object> createStaging(String tenantId, String name, String description,
                                             String sourceEnvId, String config, String createdBy) {
        if (environmentRepository.existsByTenantAndName(tenantId, name)) {
            throw new IllegalArgumentException("Environment with name '" + name + "' already exists");
        }

        if (sourceEnvId == null) {
            var production = ensureProductionEnvironment(tenantId, createdBy);
            sourceEnvId = (String) production.get("id");
        }

        String envId = environmentRepository.create(tenantId, name, description, "STAGING",
                sourceEnvId, config, createdBy);

        cloneMetadata(tenantId, sourceEnvId, envId, createdBy);

        var env = environmentRepository.findByIdAndTenant(envId, tenantId);
        publishEnvironmentEvent(tenantId, envId, "CREATED");
        return env.orElseThrow();
    }

    public List<Map<String, Object>> listEnvironments(String tenantId) {
        return environmentRepository.findByTenant(tenantId);
    }

    public Optional<Map<String, Object>> getEnvironment(String envId, String tenantId) {
        return environmentRepository.findByIdAndTenant(envId, tenantId);
    }

    public Map<String, Object> updateEnvironment(String envId, String tenantId,
                                                  String name, String description, String config) {
        var existing = environmentRepository.findByIdAndTenant(envId, tenantId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Environment not found: " + envId);
        }

        var env = existing.get();
        if ("PRODUCTION".equals(env.get("type"))) {
            throw new IllegalArgumentException("Cannot modify the production environment");
        }

        environmentRepository.update(envId, tenantId, name, description, config);
        publishEnvironmentEvent(tenantId, envId, "UPDATED");
        return environmentRepository.findByIdAndTenant(envId, tenantId).orElseThrow();
    }

    public void archiveEnvironment(String envId, String tenantId) {
        var existing = environmentRepository.findByIdAndTenant(envId, tenantId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Environment not found: " + envId);
        }

        var env = existing.get();
        if ("PRODUCTION".equals(env.get("type"))) {
            throw new IllegalArgumentException("Cannot archive the production environment");
        }

        environmentRepository.updateStatus(envId, tenantId, "ARCHIVED");
        publishEnvironmentEvent(tenantId, envId, "ARCHIVED");
        log.info("Archived environment {} for tenant {}", envId, tenantId);
    }

    public Map<String, Object> createSnapshot(String tenantId, String envId, String name, String createdBy) {
        var env = environmentRepository.findByIdAndTenant(envId, tenantId);
        if (env.isEmpty()) {
            throw new IllegalArgumentException("Environment not found: " + envId);
        }

        // Capture current metadata state using PackageRepository queries
        Map<String, Object> snapshotData = captureMetadata(tenantId);

        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshotData);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) snapshotData.get("items");
            int itemCount = items != null ? items.size() : 0;

            String snapshotId = environmentRepository.createSnapshot(tenantId, envId, name,
                    snapshotJson, itemCount, createdBy);

            log.info("Created metadata snapshot {} for environment {} (tenant={}, items={})",
                    snapshotId, envId, tenantId, itemCount);

            return environmentRepository.findSnapshotById(snapshotId, tenantId).orElseThrow();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create metadata snapshot", e);
        }
    }

    public List<Map<String, Object>> listSnapshots(String tenantId, String envId) {
        return environmentRepository.findSnapshotsByEnvironment(envId, tenantId);
    }

    public Map<String, Object> compareEnvironments(String tenantId, String sourceEnvId, String targetEnvId) {
        var sourceEnv = environmentRepository.findByIdAndTenant(sourceEnvId, tenantId);
        var targetEnv = environmentRepository.findByIdAndTenant(targetEnvId, tenantId);

        if (sourceEnv.isEmpty() || targetEnv.isEmpty()) {
            throw new IllegalArgumentException("One or both environments not found");
        }

        // For now, we capture metadata from the shared tenant and compare snapshots
        // In a full implementation, each environment would have its own isolated metadata
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("sourceEnvironment", Map.of(
                "id", sourceEnvId,
                "name", sourceEnv.get().get("name")
        ));
        diff.put("targetEnvironment", Map.of(
                "id", targetEnvId,
                "name", targetEnv.get().get("name")
        ));

        // Compare latest snapshots of each environment
        var sourceSnapshots = environmentRepository.findSnapshotsByEnvironment(sourceEnvId, tenantId);
        var targetSnapshots = environmentRepository.findSnapshotsByEnvironment(targetEnvId, tenantId);

        if (sourceSnapshots.isEmpty() || targetSnapshots.isEmpty()) {
            diff.put("status", "SNAPSHOTS_REQUIRED");
            diff.put("message", "Both environments need at least one snapshot for comparison");
            diff.put("changes", List.of());
            return diff;
        }

        // Use most recent snapshots
        var sourceSnapshotId = (String) sourceSnapshots.get(0).get("id");
        var targetSnapshotId = (String) targetSnapshots.get(0).get("id");

        var sourceSnapshot = environmentRepository.findSnapshotById(sourceSnapshotId, tenantId);
        var targetSnapshot = environmentRepository.findSnapshotById(targetSnapshotId, tenantId);

        diff.put("status", "COMPARED");
        diff.put("sourceSnapshotId", sourceSnapshotId);
        diff.put("targetSnapshotId", targetSnapshotId);
        diff.put("changes", computeChanges(sourceSnapshot.orElseThrow(), targetSnapshot.orElseThrow()));

        return diff;
    }

    private void cloneMetadata(String tenantId, String sourceEnvId, String targetEnvId, String createdBy) {
        try {
            // Create a snapshot of the source environment
            String snapshotName = "Clone snapshot for env " + targetEnvId;
            createSnapshot(tenantId, sourceEnvId, snapshotName, createdBy);

            environmentRepository.updateStatus(targetEnvId, tenantId, "ACTIVE");
            log.info("Cloned metadata from env {} to env {} for tenant {}", sourceEnvId, targetEnvId, tenantId);
        } catch (Exception e) {
            environmentRepository.updateStatus(targetEnvId, tenantId, "FAILED");
            log.error("Failed to clone metadata for environment {} (tenant={})", targetEnvId, tenantId, e);
        }
    }

    private Map<String, Object> captureMetadata(String tenantId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("capturedAt", Instant.now().toString());

        List<Map<String, Object>> items = new ArrayList<>();

        // Capture all collections
        var collections = environmentRepository.getJdbcTemplate().queryForList(
                "SELECT * FROM collection WHERE tenant_id = ?", tenantId);
        for (var col : collections) {
            items.add(Map.of("type", "COLLECTION", "data", sanitizeRow(col)));
        }

        // Capture all fields
        var fields = environmentRepository.getJdbcTemplate().queryForList(
                "SELECT f.* FROM field f JOIN collection c ON f.collection_id = c.id WHERE c.tenant_id = ?",
                tenantId);
        for (var field : fields) {
            items.add(Map.of("type", "FIELD", "data", sanitizeRow(field)));
        }

        // Capture roles
        var roles = environmentRepository.getJdbcTemplate().queryForList(
                "SELECT * FROM role WHERE tenant_id = ?", tenantId);
        for (var role : roles) {
            items.add(Map.of("type", "ROLE", "data", sanitizeRow(role)));
        }

        // Capture policies
        var policies = environmentRepository.getJdbcTemplate().queryForList(
                "SELECT * FROM policy WHERE tenant_id = ?", tenantId);
        for (var policy : policies) {
            items.add(Map.of("type", "POLICY", "data", sanitizeRow(policy)));
        }

        // Capture UI pages
        var pages = environmentRepository.getJdbcTemplate().queryForList(
                "SELECT * FROM ui_page WHERE tenant_id = ?", tenantId);
        for (var page : pages) {
            items.add(Map.of("type", "UI_PAGE", "data", sanitizeRow(page)));
        }

        // Capture UI menus
        var menus = environmentRepository.getJdbcTemplate().queryForList(
                "SELECT * FROM ui_menu WHERE tenant_id = ?", tenantId);
        for (var menu : menus) {
            items.add(Map.of("type", "UI_MENU", "data", sanitizeRow(menu)));
        }

        snapshot.put("items", items);
        return snapshot;
    }

    private Map<String, Object> sanitizeRow(Map<String, Object> row) {
        Map<String, Object> clean = new LinkedHashMap<>(row);
        clean.remove("tenant_id");
        return clean;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> computeChanges(Map<String, Object> sourceSnapshot,
                                                      Map<String, Object> targetSnapshot) {
        List<Map<String, Object>> changes = new ArrayList<>();

        try {
            String sourceDataStr = sourceSnapshot.get("snapshot_data") instanceof String s ? s :
                    objectMapper.writeValueAsString(sourceSnapshot.get("snapshot_data"));
            String targetDataStr = targetSnapshot.get("snapshot_data") instanceof String s ? s :
                    objectMapper.writeValueAsString(targetSnapshot.get("snapshot_data"));

            Map<String, Object> sourceData = objectMapper.readValue(sourceDataStr, Map.class);
            Map<String, Object> targetData = objectMapper.readValue(targetDataStr, Map.class);

            List<Map<String, Object>> sourceItems = (List<Map<String, Object>>) sourceData.getOrDefault("items", List.of());
            List<Map<String, Object>> targetItems = (List<Map<String, Object>>) targetData.getOrDefault("items", List.of());

            // Build lookup maps by type+id
            Map<String, Map<String, Object>> sourceMap = new LinkedHashMap<>();
            for (var item : sourceItems) {
                Map<String, Object> data = (Map<String, Object>) item.get("data");
                String key = item.get("type") + ":" + data.get("id");
                sourceMap.put(key, item);
            }

            Map<String, Map<String, Object>> targetMap = new LinkedHashMap<>();
            for (var item : targetItems) {
                Map<String, Object> data = (Map<String, Object>) item.get("data");
                String key = item.get("type") + ":" + data.get("id");
                targetMap.put(key, item);
            }

            // Items in source but not in target = additions
            for (var entry : sourceMap.entrySet()) {
                if (!targetMap.containsKey(entry.getKey())) {
                    Map<String, Object> data = (Map<String, Object>) entry.getValue().get("data");
                    changes.add(Map.of(
                            "action", "ADD",
                            "type", entry.getValue().get("type"),
                            "id", data.getOrDefault("id", ""),
                            "name", data.getOrDefault("name", "")
                    ));
                }
            }

            // Items in target but not in source = removals
            for (var entry : targetMap.entrySet()) {
                if (!sourceMap.containsKey(entry.getKey())) {
                    Map<String, Object> data = (Map<String, Object>) entry.getValue().get("data");
                    changes.add(Map.of(
                            "action", "REMOVE",
                            "type", entry.getValue().get("type"),
                            "id", data.getOrDefault("id", ""),
                            "name", data.getOrDefault("name", "")
                    ));
                }
            }

            // Items in both = potential modifications (simplified check)
            for (var entry : sourceMap.entrySet()) {
                if (targetMap.containsKey(entry.getKey())) {
                    Map<String, Object> sourceData2 = (Map<String, Object>) entry.getValue().get("data");
                    Map<String, Object> targetData2 = (Map<String, Object>) targetMap.get(entry.getKey()).get("data");
                    if (!sourceData2.equals(targetData2)) {
                        changes.add(Map.of(
                                "action", "MODIFY",
                                "type", entry.getValue().get("type"),
                                "id", sourceData2.getOrDefault("id", ""),
                                "name", sourceData2.getOrDefault("name", "")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to compute environment changes", e);
        }

        return changes;
    }

    private void publishEnvironmentEvent(String tenantId, String envId, String changeType) {
        try {
            Map<String, Object> event = Map.of(
                    "type", "environment.changed",
                    "tenantId", tenantId,
                    "environmentId", envId,
                    "changeType", changeType,
                    "timestamp", Instant.now().toString()
            );
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KAFKA_TOPIC_ENV_CHANGED, tenantId + ":" + envId, json);
            log.debug("Published environment {} event for env {} (tenant={})", changeType, envId, tenantId);
        } catch (Exception e) {
            log.error("Failed to publish environment event for {} (tenant={})", envId, tenantId, e);
        }
    }
}
