package io.kelta.worker.health;

import io.kelta.worker.dependency.MetadataDependencyGraph;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared inputs handed to every {@link HealthRule} for one tenant scan: the tenant id, the
 * pre-built metadata dependency graph (reused from Rec 5, for cycle detection), and a
 * {@link JdbcTemplate} for rules that query metadata tables directly.
 *
 * @since 1.0.0
 */
public record HealthContext(String tenantId, MetadataDependencyGraph graph, JdbcTemplate jdbcTemplate) {
}
