package io.kelta.worker.health;

import io.kelta.worker.service.MetadataDependencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scans a tenant's configuration for anti-patterns and produces a {@link ConfigHealthReport}
 * (health score + findings) — Kelta's answer to the OutSystems AI Mentor System.
 *
 * <p>Reuses the Rec 5 metadata dependency graph for structural checks (notably circular
 * master-detail) and runs the registered {@link HealthRule} beans. Each rule is isolated: a
 * failure is logged and skipped so one broken rule never fails the whole scan. The scan is
 * on-demand and read-only.
 *
 * @since 1.0.0
 */
@Service
public class ConfigHealthAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ConfigHealthAnalyzer.class);

    private final MetadataDependencyService dependencyService;
    private final JdbcTemplate jdbcTemplate;
    private final List<HealthRule> rules;

    public ConfigHealthAnalyzer(MetadataDependencyService dependencyService,
                                JdbcTemplate jdbcTemplate,
                                List<HealthRule> rules) {
        this.dependencyService = dependencyService;
        this.jdbcTemplate = jdbcTemplate;
        this.rules = rules;
    }

    /**
     * Runs every health rule for a tenant and returns the scored report.
     */
    public ConfigHealthReport analyze(String tenantId) {
        HealthContext context = new HealthContext(
                tenantId, dependencyService.buildGraph(tenantId), jdbcTemplate);

        List<HealthFinding> findings = new ArrayList<>();
        for (HealthRule rule : rules) {
            try {
                findings.addAll(rule.evaluate(context));
            } catch (Exception e) {
                log.warn("Config-health rule '{}' failed for tenant {}: {}",
                        rule.key(), tenantId, e.getMessage());
            }
        }

        // Most severe first, then by rule for a stable order.
        findings.sort(Comparator.comparingInt((HealthFinding f) -> f.severity().ordinal())
                .thenComparing(HealthFinding::ruleKey));

        return ConfigHealthReport.from(findings, rules.size());
    }
}
