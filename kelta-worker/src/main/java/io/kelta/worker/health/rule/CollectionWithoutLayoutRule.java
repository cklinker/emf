package io.kelta.worker.health.rule;

import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.health.HealthContext;
import io.kelta.worker.health.HealthFinding;
import io.kelta.worker.health.HealthRule;
import io.kelta.worker.health.HealthSeverity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Flags active user collections that have no page layout — records fall back to a default
 * rendering, usually an oversight.
 *
 * @since 1.0.0
 */
@Component
public class CollectionWithoutLayoutRule implements HealthRule {

    private static final String SQL = """
            SELECT c.id, c.name FROM collection c
            WHERE c.tenant_id = ? AND c.system_collection = false AND c.active = true
              AND NOT EXISTS (SELECT 1 FROM page_layout pl WHERE pl.collection_id = c.id)
            """;

    @Override
    public String key() {
        return "COLLECTION_WITHOUT_LAYOUT";
    }

    @Override
    public List<HealthFinding> evaluate(HealthContext context) {
        List<Map<String, Object>> rows = context.jdbcTemplate().queryForList(SQL, context.tenantId());
        return rows.stream().map(row -> HealthFinding.of(
                key(), HealthSeverity.WARNING,
                "Collection has no page layout",
                "Collection '" + row.get("name") + "' has no page layout; records render with a"
                        + " default layout. Create a layout to control field placement.",
                MetadataType.COLLECTION, (String) row.get("id"), (String) row.get("name"))).toList();
    }
}
