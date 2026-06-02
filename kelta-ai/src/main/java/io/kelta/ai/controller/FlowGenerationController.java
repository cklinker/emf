package io.kelta.ai.controller;

import io.kelta.ai.service.FlowGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Generates a flow definition from a plain-English description.
 *
 * <p>{@code POST /api/ai/flows/generate} — one-shot, no streaming.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/ai/flows")
public class FlowGenerationController {

    private static final Logger log = LoggerFactory.getLogger(FlowGenerationController.class);

    private final FlowGenerationService flowGenerationService;

    public FlowGenerationController(FlowGenerationService flowGenerationService) {
        this.flowGenerationService = flowGenerationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, String> body) {

        String description = body.get("description");
        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "description is required"));
        }

        try {
            Map<String, Object> definition = flowGenerationService.generateFlow(tenantId, description);
            return ResponseEntity.ok(Map.of("definition", definition));
        } catch (IllegalArgumentException e) {
            log.warn("Flow generation produced invalid output for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Flow generation failed for tenant {}", tenantId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Flow generation failed"));
        }
    }
}
