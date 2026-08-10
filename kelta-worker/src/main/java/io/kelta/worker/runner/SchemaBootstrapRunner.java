package io.kelta.worker.runner;

import io.kelta.worker.service.CollectionLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies every active collection's physical schema, once, from the migrate Job.
 *
 * <p>Runs only under the {@code migrate} profile — the ArgoCD PreSync hook Job that also runs
 * Flyway. Two things follow from that placement:
 *
 * <ul>
 *   <li><b>No race.</b> Previously the worker pods did this on {@code ApplicationReadyEvent},
 *       so every replica issued {@code CREATE TABLE} / {@code reconcileSchema} for every
 *       collection concurrently against the same database. The storage adapter can recover
 *       from the resulting {@code pg_type} unique violations, but only after the fact. Here a
 *       single process owns the DDL and the pods that follow just read the finished schema.</li>
 *   <li><b>No half-deployed schema.</b> A failure throws, which fails the Job
 *       ({@code backoffLimit: 0}), which fails the PreSync hook, which stops the sync before
 *       the worker Deployment is touched. The previous behaviour logged the error and started
 *       serving traffic against a table that was never created.</li>
 * </ul>
 *
 * <p>Ordered after {@code SystemCollectionSeeder} (@Order(5)) so seeded system collections are
 * present, and well before {@link MigrateShutdownRunner} (@Order(Integer.MAX_VALUE)), whose
 * {@code System.exit(0)} would otherwise cut this short.
 *
 * <p>Collections created at runtime — after the deploy — are unaffected: they get their table
 * from the NATS {@code collection-changed} path, which still applies DDL on the pod.
 *
 * @since 1.0.0
 */
@Component
@Profile("migrate")
@Order(10)
public class SchemaBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaBootstrapRunner.class);

    private static final String SELECT_ACTIVE_COLLECTIONS =
            "SELECT id, name FROM collection WHERE active = true";

    private final JdbcTemplate jdbcTemplate;
    private final CollectionLifecycleManager lifecycleManager;

    public SchemaBootstrapRunner(JdbcTemplate jdbcTemplate,
                                 CollectionLifecycleManager lifecycleManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.lifecycleManager = lifecycleManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> collections = jdbcTemplate.queryForList(SELECT_ACTIVE_COLLECTIONS);
        log.info("Schema bootstrap: applying schema for {} active collections", collections.size());

        // Collect every failure before giving up, so one deploy surfaces the full list of
        // broken collections instead of leaking them one Job run at a time.
        List<String> failures = new ArrayList<>();

        for (Map<String, Object> collection : collections) {
            String collectionId = (String) collection.get("id");
            String collectionName = (String) collection.get("name");
            if (collectionId == null) {
                continue;
            }
            try {
                lifecycleManager.initializeCollectionOrThrow(collectionId, true);
            } catch (Exception e) {
                log.error("Schema bootstrap failed for collection '{}' (id={})",
                        collectionName, collectionId, e);
                failures.add(collectionName + " (id=" + collectionId + "): " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "Schema bootstrap failed for " + failures.size() + " of " + collections.size()
                            + " collections; aborting the migration Job so the worker rollout does "
                            + "not proceed against an incomplete schema. Failures: " + failures);
        }

        log.info("Schema bootstrap complete: {} collections applied", collections.size());
    }
}
