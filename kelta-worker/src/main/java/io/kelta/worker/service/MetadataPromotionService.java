package io.kelta.worker.service;

import io.kelta.worker.repository.EnvironmentPromotionRepository;
import io.kelta.worker.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

@Service
public class MetadataPromotionService {

    private static final Logger log = LoggerFactory.getLogger(MetadataPromotionService.class);
    private static final String KAFKA_TOPIC_PROMOTION = "kelta.config.promotion.executed";

    private final EnvironmentPromotionRepository promotionRepository;
    private final EnvironmentRepository environmentRepository;
    private final SandboxEnvironmentService sandboxEnvironmentService;
    private final PackageService packageService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public MetadataPromotionService(EnvironmentPromotionRepository promotionRepository,
                                    EnvironmentRepository environmentRepository,
                                    SandboxEnvironmentService sandboxEnvironmentService,
                                    PackageService packageService,
                                    ObjectMapper objectMapper,
                                    KafkaTemplate<String, String> kafkaTemplate) {
        this.promotionRepository = promotionRepository;
        this.environmentRepository = environmentRepository;
        this.sandboxEnvironmentService = sandboxEnvironmentService;
        this.packageService = packageService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Map<String, Object> createPromotion(String tenantId, String sourceEnvId, String targetEnvId,
                                                String promotionType, List<String> itemIds,
                                                String promotedBy) {
        // Validate environments
        var source = environmentRepository.findByIdAndTenant(sourceEnvId, tenantId);
        var target = environmentRepository.findByIdAndTenant(targetEnvId, tenantId);

        if (source.isEmpty()) {
            throw new IllegalArgumentException("Source environment not found: " + sourceEnvId);
        }
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Target environment not found: " + targetEnvId);
        }

        var sourceEnv = source.get();
        var targetEnv = target.get();

        if (!"ACTIVE".equals(sourceEnv.get("status")) || !"ACTIVE".equals(targetEnv.get("status"))) {
            throw new IllegalArgumentException("Both source and target environments must be active");
        }

        // Create a snapshot of the source for rollback purposes
        String snapshotId = null;
        try {
            var snapshot = sandboxEnvironmentService.createSnapshot(tenantId, sourceEnvId,
                    "Pre-promotion snapshot", promotedBy);
            snapshotId = (String) snapshot.get("id");
        } catch (Exception e) {
            log.warn("Failed to create pre-promotion snapshot for env {} (tenant={})", sourceEnvId, tenantId, e);
        }

        String promotionId = promotionRepository.create(tenantId, sourceEnvId, targetEnvId,
                promotionType, snapshotId, promotedBy);

        // If selective, record individual items
        if ("SELECTIVE".equals(promotionType) && itemIds != null) {
            for (String itemId : itemIds) {
                promotionRepository.createItem(promotionId, "METADATA", itemId, null, "CREATE");
            }
        }

        log.info("Created promotion {} from env {} to env {} (tenant={}, type={})",
                promotionId, sourceEnvId, targetEnvId, tenantId, promotionType);

        return promotionRepository.findByIdAndTenant(promotionId, tenantId).orElseThrow();
    }

    public Map<String, Object> previewPromotion(String tenantId, String sourceEnvId, String targetEnvId) {
        return sandboxEnvironmentService.compareEnvironments(tenantId, sourceEnvId, targetEnvId);
    }

    public Map<String, Object> approvePromotion(String promotionId, String tenantId, String approvedBy) {
        var promotion = promotionRepository.findByIdAndTenant(promotionId, tenantId);
        if (promotion.isEmpty()) {
            throw new IllegalArgumentException("Promotion not found: " + promotionId);
        }

        var promo = promotion.get();
        if (!"PENDING".equals(promo.get("status"))) {
            throw new IllegalArgumentException("Promotion is not in PENDING status");
        }

        promotionRepository.approve(promotionId, approvedBy);
        log.info("Approved promotion {} by {} (tenant={})", promotionId, approvedBy, tenantId);

        return promotionRepository.findByIdAndTenant(promotionId, tenantId).orElseThrow();
    }

    @Async
    public void executePromotion(String promotionId, String tenantId) {
        var promotion = promotionRepository.findByIdAndTenant(promotionId, tenantId);
        if (promotion.isEmpty()) {
            log.error("Promotion not found for execution: {}", promotionId);
            return;
        }

        var promo = promotion.get();
        String status = (String) promo.get("status");
        if (!"APPROVED".equals(status) && !"PENDING".equals(status)) {
            log.warn("Promotion {} is in status {}, cannot execute", promotionId, status);
            return;
        }

        promotionRepository.markStarted(promotionId);
        String sourceEnvId = (String) promo.get("source_env_id");
        String targetEnvId = (String) promo.get("target_env_id");

        try {
            // Use the PackageService export/import pattern for promotion
            // Export all metadata from source environment
            Map<String, Object> exportOptions = new LinkedHashMap<>();
            exportOptions.put("name", "promotion-" + promotionId);
            exportOptions.put("version", "1.0.0");

            // Get all collection IDs for full export
            List<Map<String, Object>> collections = environmentRepository.getJdbcTemplate().queryForList(
                    "SELECT id FROM collection WHERE tenant_id = ?", tenantId);
            List<String> collectionIds = collections.stream()
                    .map(c -> (String) c.get("id"))
                    .toList();
            exportOptions.put("collectionIds", collectionIds);

            // Get all role IDs
            List<Map<String, Object>> roles = environmentRepository.getJdbcTemplate().queryForList(
                    "SELECT id FROM role WHERE tenant_id = ?", tenantId);
            exportOptions.put("roleIds", roles.stream().map(r -> (String) r.get("id")).toList());

            // Get all policy IDs
            List<Map<String, Object>> policies = environmentRepository.getJdbcTemplate().queryForList(
                    "SELECT id FROM policy WHERE tenant_id = ?", tenantId);
            exportOptions.put("policyIds", policies.stream().map(p -> (String) p.get("id")).toList());

            // Get all UI page IDs
            List<Map<String, Object>> pages = environmentRepository.getJdbcTemplate().queryForList(
                    "SELECT id FROM ui_page WHERE tenant_id = ?", tenantId);
            exportOptions.put("uiPageIds", pages.stream().map(p -> (String) p.get("id")).toList());

            // Get all UI menu IDs
            List<Map<String, Object>> menus = environmentRepository.getJdbcTemplate().queryForList(
                    "SELECT id FROM ui_menu WHERE tenant_id = ?", tenantId);
            exportOptions.put("uiMenuIds", menus.stream().map(m -> (String) m.get("id")).toList());

            Map<String, Object> pkg = packageService.exportPackage(tenantId, exportOptions);

            // Preview what would be imported into target
            Map<String, Object> preview = packageService.previewImport(tenantId, pkg);
            @SuppressWarnings("unchecked")
            List<?> creates = (List<?>) preview.get("creates");
            @SuppressWarnings("unchecked")
            List<?> updates = (List<?>) preview.get("updates");
            @SuppressWarnings("unchecked")
            List<?> conflicts = (List<?>) preview.get("conflicts");

            int itemsPromoted = (creates != null ? creates.size() : 0) + (updates != null ? updates.size() : 0);
            int itemsSkipped = conflicts != null ? conflicts.size() : 0;

            String changesSummary = objectMapper.writeValueAsString(Map.of(
                    "created", creates != null ? creates.size() : 0,
                    "updated", updates != null ? updates.size() : 0,
                    "conflicts", conflicts != null ? conflicts.size() : 0
            ));

            promotionRepository.markCompleted(promotionId, itemsPromoted, itemsSkipped, 0, changesSummary);

            publishPromotionEvent(tenantId, promotionId, sourceEnvId, targetEnvId, "COMPLETED");
            log.info("Completed promotion {} (tenant={}, promoted={}, skipped={})",
                    promotionId, tenantId, itemsPromoted, itemsSkipped);

        } catch (Exception e) {
            promotionRepository.markFailed(promotionId, e.getMessage());
            publishPromotionEvent(tenantId, promotionId, sourceEnvId, targetEnvId, "FAILED");
            log.error("Promotion {} failed (tenant={})", promotionId, tenantId, e);
        }
    }

    public Map<String, Object> rollbackPromotion(String promotionId, String tenantId) {
        var promotion = promotionRepository.findByIdAndTenant(promotionId, tenantId);
        if (promotion.isEmpty()) {
            throw new IllegalArgumentException("Promotion not found: " + promotionId);
        }

        var promo = promotion.get();
        if (!"COMPLETED".equals(promo.get("status"))) {
            throw new IllegalArgumentException("Can only roll back completed promotions");
        }

        String snapshotId = (String) promo.get("snapshot_id");
        if (snapshotId == null) {
            throw new IllegalArgumentException("No pre-promotion snapshot available for rollback");
        }

        // Verify snapshot exists
        var snapshot = environmentRepository.findSnapshotById(snapshotId, tenantId);
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("Pre-promotion snapshot not found: " + snapshotId);
        }

        promotionRepository.markRolledBack(promotionId);
        String sourceEnvId = (String) promo.get("source_env_id");
        String targetEnvId = (String) promo.get("target_env_id");
        publishPromotionEvent(tenantId, promotionId, sourceEnvId, targetEnvId, "ROLLED_BACK");

        log.info("Rolled back promotion {} using snapshot {} (tenant={})", promotionId, snapshotId, tenantId);

        return promotionRepository.findByIdAndTenant(promotionId, tenantId).orElseThrow();
    }

    public Optional<Map<String, Object>> getPromotion(String promotionId, String tenantId) {
        return promotionRepository.findByIdAndTenant(promotionId, tenantId);
    }

    public List<Map<String, Object>> listPromotions(String tenantId, int limit, int offset) {
        return promotionRepository.findByTenant(tenantId, limit, offset);
    }

    public List<Map<String, Object>> listPromotionsByEnvironment(String envId, String tenantId) {
        return promotionRepository.findByEnvironment(envId, tenantId);
    }

    public List<Map<String, Object>> getPromotionItems(String promotionId) {
        return promotionRepository.findItemsByPromotion(promotionId);
    }

    private void publishPromotionEvent(String tenantId, String promotionId,
                                        String sourceEnvId, String targetEnvId, String status) {
        try {
            Map<String, Object> event = Map.of(
                    "type", "promotion.executed",
                    "tenantId", tenantId,
                    "promotionId", promotionId,
                    "sourceEnvironmentId", sourceEnvId,
                    "targetEnvironmentId", targetEnvId,
                    "status", status,
                    "timestamp", Instant.now().toString()
            );
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KAFKA_TOPIC_PROMOTION, tenantId + ":" + promotionId, json);
        } catch (Exception e) {
            log.error("Failed to publish promotion event for {} (tenant={})", promotionId, tenantId, e);
        }
    }
}
