package io.kelta.ai.controller;

import io.kelta.ai.service.ProposalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/proposals")
public class ProposalController {

    private static final Logger log = LoggerFactory.getLogger(ProposalController.class);

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<Map<String, Object>> applyProposal(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable UUID id) {

        log.info("Applying proposal {} for tenant {}", id, tenantId);

        try {
            Map<String, Object> result = proposalService.applyProposal(
                    id, tenantId, userId);
            return ResponseEntity.ok(Map.of("data", result, "status", "applied"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", java.util.List.of(Map.of(
                            "status", "400",
                            "code", "INVALID_PROPOSAL",
                            "title", e.getMessage()
                    ))));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", java.util.List.of(Map.of(
                            "status", "400",
                            "code", "PROPOSAL_ALREADY_APPLIED",
                            "title", e.getMessage()
                    ))));
        } catch (Exception e) {
            log.error("Failed to apply proposal {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "errors", java.util.List.of(Map.of(
                            "status", "500",
                            "code", "APPLY_FAILED",
                            "title", "Failed to apply proposed changes",
                            "detail", e.getMessage()
                    ))));
        }
    }
}
