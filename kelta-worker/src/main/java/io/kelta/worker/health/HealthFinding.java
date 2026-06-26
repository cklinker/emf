package io.kelta.worker.health;

import io.kelta.worker.dependency.MetadataType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One configuration-health issue found by a {@link HealthRule}.
 *
 * @param ruleKey    the originating rule's key (e.g. {@code CIRCULAR_MASTER_DETAIL})
 * @param severity   how serious the issue is
 * @param title      short human-readable summary
 * @param detail     explanation + suggested remediation
 * @param targetType the kind of metadata the finding is about (may be null for tenant-wide)
 * @param targetId   the metadata object's id (may be null)
 * @param targetName a display label for the target (may be null)
 * @since 1.0.0
 */
public record HealthFinding(
        String ruleKey,
        HealthSeverity severity,
        String title,
        String detail,
        MetadataType targetType,
        String targetId,
        String targetName) {

    public static HealthFinding of(String ruleKey, HealthSeverity severity, String title, String detail,
                                   MetadataType targetType, String targetId, String targetName) {
        return new HealthFinding(ruleKey, severity, title, detail, targetType, targetId, targetName);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleKey", ruleKey);
        map.put("severity", severity.name());
        map.put("title", title);
        map.put("detail", detail);
        map.put("targetType", targetType == null ? null : targetType.name());
        map.put("targetId", targetId);
        map.put("targetName", targetName);
        return map;
    }
}
