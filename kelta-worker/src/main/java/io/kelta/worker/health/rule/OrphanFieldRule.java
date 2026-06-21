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
 * Flags active non-relationship fields that are not placed on any page layout and back no unique
 * constraint — likely unused. Advisory (INFO): the field may still be read via the API, so this is
 * a hint to review, not an error.
 *
 * @since 1.0.0
 */
@Component
public class OrphanFieldRule implements HealthRule {

    private static final String SQL = """
            SELECT f.id, f.name, c.name AS collection_name
            FROM field f
            JOIN collection c ON f.collection_id = c.id
            WHERE c.tenant_id = ? AND c.system_collection = false AND c.active = true AND f.active = true
              AND f.relationship_type IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM layout_field lf
                  JOIN layout_section ls ON lf.section_id = ls.id
                  JOIN page_layout pl ON ls.layout_id = pl.id
                  WHERE lf.field_id = f.id)
              AND NOT EXISTS (SELECT 1 FROM unique_constraint uc WHERE uc.field_id = f.id)
            """;

    @Override
    public String key() {
        return "ORPHAN_FIELD";
    }

    @Override
    public List<HealthFinding> evaluate(HealthContext context) {
        List<Map<String, Object>> rows = context.jdbcTemplate().queryForList(SQL, context.tenantId());
        return rows.stream().map(row -> HealthFinding.of(
                key(), HealthSeverity.INFO,
                "Field is not used on any layout",
                "Field '" + row.get("collection_name") + "." + row.get("name") + "' is not placed on"
                        + " any page layout and backs no unique constraint. Review whether it is still needed.",
                MetadataType.FIELD, (String) row.get("id"), (String) row.get("name"))).toList();
    }
}
