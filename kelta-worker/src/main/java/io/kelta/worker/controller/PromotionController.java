package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.MetadataPromotionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private static final Logger log = LoggerFactory.getLogger(PromotionController.class);

    private final MetadataPromotionService promotionService;

    public PromotionController(MetadataPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public ResponseEntity<?> listPromotions(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        List<Map<String, Object>> promotions = promotionService.listPromotions(tenantId, limit, offset);
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("promotions", promotions));
    }

    @GetMapping("/{promotionId}")
    public ResponseEntity<?> getPromotion(@PathVariable String promotionId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        var promotion = promotionService.getPromotion(promotionId, tenantId);
        if (promotion.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(JsonApiResponseBuilder.single("promotions", extractId(promotion.get()), promotion.get()));
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createPromotion(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String sourceEnvId = (String) attrs.get("sourceEnvironmentId");
        String targetEnvId = (String) attrs.get("targetEnvironmentId");
        String promotionType = (String) attrs.getOrDefault("promotionType", "FULL");
        String promotedBy = (String) attrs.get("promotedBy");

        if (sourceEnvId == null || targetEnvId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both sourceEnvironmentId and targetEnvironmentId are required"));
        }

        List<String> itemIds = null;
        Object itemIdsObj = attrs.get("itemIds");
        if (itemIdsObj instanceof List<?> list) {
            itemIds = list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        try {
            Map<String, Object> promotion = promotionService.createPromotion(
                    tenantId, sourceEnvId, targetEnvId, promotionType, itemIds, promotedBy);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(JsonApiResponseBuilder.single("promotions", extractId(promotion), promotion));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<?> previewPromotion(@RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String sourceEnvId = (String) attrs.get("sourceEnvironmentId");
        String targetEnvId = (String) attrs.get("targetEnvironmentId");

        if (sourceEnvId == null || targetEnvId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both sourceEnvironmentId and targetEnvironmentId are required"));
        }

        try {
            Map<String, Object> preview = promotionService.previewPromotion(tenantId, sourceEnvId, targetEnvId);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{promotionId}/approve")
    public ResponseEntity<?> approvePromotion(@PathVariable String promotionId,
                                               @RequestBody Map<String, Object> body) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        Map<String, Object> attrs = unwrapJsonApiBody(body);
        String approvedBy = (String) attrs.get("approvedBy");

        try {
            Map<String, Object> promotion = promotionService.approvePromotion(promotionId, tenantId, approvedBy);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("promotions", extractId(promotion), promotion));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{promotionId}/execute")
    public ResponseEntity<?> executePromotion(@PathVariable String promotionId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        try {
            promotionService.executePromotion(promotionId, tenantId);
            return ResponseEntity.accepted().body(Map.of("status", "executing", "promotionId", promotionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{promotionId}/rollback")
    public ResponseEntity<?> rollbackPromotion(@PathVariable String promotionId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        try {
            Map<String, Object> promotion = promotionService.rollbackPromotion(promotionId, tenantId);
            return ResponseEntity.ok(JsonApiResponseBuilder.single("promotions", extractId(promotion), promotion));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{promotionId}/items")
    public ResponseEntity<?> getPromotionItems(@PathVariable String promotionId) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tenant context"));
        }

        // Verify promotion belongs to tenant
        var promotion = promotionService.getPromotion(promotionId, tenantId);
        if (promotion.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> items = promotionService.getPromotionItems(promotionId);
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("promotion-items", items));
    }

    private String extractId(Map<String, Object> data) {
        Object id = data.get("id");
        return id != null ? id.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapJsonApiBody(Map<String, Object> body) {
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object attrObj = data.get("attributes");
            if (attrObj instanceof Map<?, ?> attributes) {
                return new LinkedHashMap<>((Map<String, Object>) attributes);
            }
        }
        return body;
    }
}
