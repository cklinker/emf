package io.kelta.worker.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of a per-tenant configuration-health scan: an overall score (0–100), the findings,
 * and per-severity counts.
 *
 * @param score    100 minus the summed severity penalties, floored at 0
 * @param findings every finding produced by the rules
 * @param rulesRun how many rules were evaluated
 * @since 1.0.0
 */
public record ConfigHealthReport(int score, List<HealthFinding> findings, int rulesRun) {

    /** Builds a report from findings, computing the score from severity penalties. */
    public static ConfigHealthReport from(List<HealthFinding> findings, int rulesRun) {
        int penalty = findings.stream().mapToInt(f -> f.severity().penalty()).sum();
        int score = Math.max(0, 100 - penalty);
        return new ConfigHealthReport(score, findings, rulesRun);
    }

    public Map<String, Object> summary() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (HealthSeverity severity : HealthSeverity.values()) {
            counts.put(severity.name().toLowerCase(),
                    findings.stream().filter(f -> f.severity() == severity).count());
        }
        return counts;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("score", score);
        map.put("rulesRun", rulesRun);
        map.put("findingCount", findings.size());
        map.put("summary", summary());
        map.put("findings", findings.stream().map(HealthFinding::toMap).toList());
        return map;
    }
}
