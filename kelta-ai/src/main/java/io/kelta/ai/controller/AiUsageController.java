package io.kelta.ai.controller;

import io.kelta.ai.repository.TokenUsageRepository;
import io.kelta.ai.service.TokenTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI usage reporting endpoints.
 */
@RestController
@RequestMapping("/api/ai/usage")
public class AiUsageController {

    private final TokenTrackingService tokenTrackingService;
    private final TokenUsageRepository tokenUsageRepository;

    public AiUsageController(TokenTrackingService tokenTrackingService,
                              TokenUsageRepository tokenUsageRepository) {
        this.tokenTrackingService = tokenTrackingService;
        this.tokenUsageRepository = tokenUsageRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUsage(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        long tid = Long.parseLong(tenantId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentMonthUsage", tokenTrackingService.getCurrentMonthUsage(tid));
        data.put("tokenLimit", tokenTrackingService.getTokenLimit(tid));
        data.put("aiEnabled", tokenTrackingService.isAiEnabled(tid));
        data.put("history", tokenUsageRepository.getUsageHistory(tid, 12));

        return ResponseEntity.ok(Map.of("data", data));
    }
}
