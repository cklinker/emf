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
 * Flags profiles granted the broad {@code VIEW_ALL_DATA} / {@code MODIFY_ALL_DATA} system
 * permissions, which bypass record- and field-level security. Worth reviewing against least
 * privilege.
 *
 * @since 1.0.0
 */
@Component
public class OverpermissiveProfileRule implements HealthRule {

    private static final String SQL = """
            SELECT p.id, p.name, psp.permission_name AS permission_key
            FROM profile_system_permission psp
            JOIN profile p ON psp.profile_id = p.id
            WHERE p.tenant_id = ? AND psp.granted = true
              AND psp.permission_name IN ('VIEW_ALL_DATA', 'MODIFY_ALL_DATA')
            """;

    @Override
    public String key() {
        return "OVERPERMISSIVE_PROFILE";
    }

    @Override
    public List<HealthFinding> evaluate(HealthContext context) {
        List<Map<String, Object>> rows = context.jdbcTemplate().queryForList(SQL, context.tenantId());
        return rows.stream().map(row -> HealthFinding.of(
                key(), HealthSeverity.WARNING,
                "Profile bypasses data security",
                "Profile '" + row.get("name") + "' is granted '" + row.get("permission_key") + "',"
                        + " which bypasses record- and field-level security. Confirm this is intended.",
                MetadataType.PROFILE, (String) row.get("id"), (String) row.get("name"))).toList();
    }
}
