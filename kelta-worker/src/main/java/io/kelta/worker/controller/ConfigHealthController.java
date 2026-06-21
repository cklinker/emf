package io.kelta.worker.controller;

import io.kelta.worker.health.ConfigHealthAnalyzer;
import io.kelta.worker.health.ConfigHealthReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration-health endpoint: scans a tenant's metadata for anti-patterns and returns a health
 * score + findings (Kelta's AI-Mentor-System analogue).
 *
 * <p>The response uses a {@code { "data": { ... } }} envelope with no {@code attributes} key, so
 * the read-side field-security advice treats it as a non-record payload and leaves it untouched.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/config-health")
public class ConfigHealthController {

    private final ConfigHealthAnalyzer analyzer;

    public ConfigHealthController(ConfigHealthAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Runs a configuration-health scan for the tenant and returns the scored report.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health(@RequestHeader("X-Tenant-ID") String tenantId) {
        ConfigHealthReport report = analyzer.analyze(tenantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", report.toMap());
        return ResponseEntity.ok(body);
    }
}
