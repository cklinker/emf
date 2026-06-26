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
 * Flags active user collections that have no active fields — an empty collection that can only
 * hold system audit columns, usually unfinished setup.
 *
 * @since 1.0.0
 */
@Component
public class CollectionWithoutFieldsRule implements HealthRule {

    private static final String SQL = """
            SELECT c.id, c.name FROM collection c
            WHERE c.tenant_id = ? AND c.system_collection = false AND c.active = true
              AND NOT EXISTS (SELECT 1 FROM field f WHERE f.collection_id = c.id AND f.active = true)
            """;

    @Override
    public String key() {
        return "COLLECTION_WITHOUT_FIELDS";
    }

    @Override
    public List<HealthFinding> evaluate(HealthContext context) {
        List<Map<String, Object>> rows = context.jdbcTemplate().queryForList(SQL, context.tenantId());
        return rows.stream().map(row -> HealthFinding.of(
                key(), HealthSeverity.WARNING,
                "Collection has no fields",
                "Collection '" + row.get("name") + "' has no active fields and can only store system"
                        + " audit columns. Add fields or remove the collection.",
                MetadataType.COLLECTION, (String) row.get("id"), (String) row.get("name"))).toList();
    }
}
