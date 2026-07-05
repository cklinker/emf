package io.kelta.worker.service;

import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.worker.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Environment registry + metadata snapshots.
 *
 * <p>Environments are rows in the parent tenant's registry: the PRODUCTION row
 * represents the tenant itself, SANDBOX/STAGING rows point at real linked
 * sandbox tenants ({@code sandbox_tenant_id}), and remote rows describe
 * promotion targets on other clusters. Provisioning/cloning lives in
 * {@link SandboxProvisioningService}; promotion in {@link MetadataPromotionService}.
 *
 * <p>A snapshot stores a <b>full metadata package</b> (same format as
 * {@code PackageService.exportPackage}) so a promotion rollback can re-import
 * it through the standard package import path.
 */
@Service
public class SandboxEnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(SandboxEnvironmentService.class);
    private static final String SUBJECT_ENV_CHANGED = "kelta.config.environment.changed";

    private final EnvironmentRepository environmentRepository;
    private final PackageService packageService;
    private final ObjectMapper objectMapper;
    private final PlatformEventPublisher eventPublisher;

    public SandboxEnvironmentService(EnvironmentRepository environmentRepository,
                                     PackageService packageService,
                                     ObjectMapper objectMapper,
                                     PlatformEventPublisher eventPublisher) {
        this.environmentRepository = environmentRepository;
        this.packageService = packageService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
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

    /**
     * Captures the tenant's full metadata as a package-format snapshot. Must be
     * called under the tenant's own context (the export reads RLS-scoped rows).
     */
    public Map<String, Object> createSnapshot(String tenantId, String envId, String name, String createdBy) {
        var env = environmentRepository.findByIdAndTenant(envId, tenantId);
        if (env.isEmpty()) {
            throw new IllegalArgumentException("Environment not found: " + envId);
        }

        Map<String, Object> pkg = packageService.exportPackage(tenantId,
                packageService.exportAllOptions(tenantId, name, "snapshot"), false);

        try {
            String snapshotJson = objectMapper.writeValueAsString(pkg);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) pkg.get("items");
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

    /** Parses a stored snapshot's package document. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> snapshotPackage(String snapshotId, String tenantId) {
        var snapshot = environmentRepository.findSnapshotById(snapshotId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));
        Object data = snapshot.get("snapshot_data");
        try {
            String json = data instanceof String s ? s : String.valueOf(data);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot data is not a valid package document", e);
        }
    }

    private void publishEnvironmentEvent(String tenantId, String envId, String changeType) {
        try {
            Map<String, Object> payload = Map.of(
                    "environmentId", envId,
                    "changeType", changeType
            );
            PlatformEvent<Map<String, Object>> event = EventFactory.createEvent("environment.changed", payload);
            event.setTenantId(tenantId);
            String subject = SUBJECT_ENV_CHANGED + "." + tenantId + "." + envId;
            eventPublisher.publish(subject, event);
            log.debug("Published environment {} event for env {} (tenant={})", changeType, envId, tenantId);
        } catch (Exception e) {
            log.error("Failed to publish environment event for {} (tenant={})", envId, tenantId, e);
        }
    }
}
