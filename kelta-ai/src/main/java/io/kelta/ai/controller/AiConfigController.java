package io.kelta.ai.controller;

import io.kelta.ai.repository.AiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI configuration endpoints. Requires platform_admin role (enforced via Cerbos at gateway).
 */
@RestController
@RequestMapping("/api/ai/config")
public class AiConfigController {

    private static final Logger log = LoggerFactory.getLogger(AiConfigController.class);

    private final AiConfigRepository configRepository;

    public AiConfigController(AiConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig(
            @RequestHeader("X-Tenant-ID") String tenantId) {

        Map<String, String> config = configRepository.getAllConfig(tenantId);

        // Also include global defaults (tenant 0) for any missing keys
        Map<String, String> globalConfig = configRepository.getAllConfig("0");
        globalConfig.forEach(config::putIfAbsent);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", config.getOrDefault("anthropic.model", "claude-sonnet-4-20250514"));
        data.put("maxTokens", config.getOrDefault("anthropic.maxTokens", "4096"));
        data.put("temperature", config.getOrDefault("anthropic.temperature", "0.7"));
        data.put("aiTokensPerMonth", config.getOrDefault("aiTokensPerMonth", "1000000"));
        data.put("aiEnabled", config.getOrDefault("aiEnabled", "true"));

        return ResponseEntity.ok(Map.of("data", data));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {

        log.info("Updating AI config for tenant {}: {}", tenantId, body.keySet());

        // Map UI field names to config keys
        Map<String, String> keyMapping = Map.of(
                "model", "anthropic.model",
                "maxTokens", "anthropic.maxTokens",
                "temperature", "anthropic.temperature",
                "aiTokensPerMonth", "aiTokensPerMonth",
                "aiEnabled", "aiEnabled"
        );

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String configKey = keyMapping.getOrDefault(entry.getKey(), entry.getKey());
            configRepository.setConfig(tenantId, configKey, String.valueOf(entry.getValue()));
        }

        return getConfig(tenantId);
    }
}
