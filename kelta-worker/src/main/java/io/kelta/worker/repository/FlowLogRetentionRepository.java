package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Age-based pruning of flow and job execution history.
 *
 * <p>Nothing else in the platform deletes these tables — {@code JdbcFlowStore}'s
 * only DELETE is {@code deletePendingResume} — so they grow without bound. A
 * high-frequency flow makes that growth the dominant table in the database.
 *
 * <p><b>Only terminal executions are eligible.</b> {@code RUNNING} and
 * {@code WAITING} rows are never touched regardless of age: a Wait state can
 * legitimately park for months, and deleting one would strand a live flow with
 * no execution row to resume into.
 *
 * <p><b>Age uses {@code COALESCE(completed_at, started_at)}.</b> {@code
 * completed_at} is nullable, and a terminal row that somehow never got one would
 * be invisible to a plain {@code completed_at < cutoff} predicate — i.e. it would
 * leak forever, which is precisely the bug this sweep exists to fix.
 *
 * <p>Deletes are batched and claim rows with {@code FOR UPDATE SKIP LOCKED}, so
 * concurrent pods take disjoint slices instead of deadlocking. Hand-written SQL
 * on {@link JdbcTemplate} — no JPA.
 */
@Repository
public class FlowLogRetentionRepository {

    /**
     * Statuses safe to prune. Deliberately enumerated rather than expressed as
     * "not RUNNING/WAITING", so a future status added to the CHECK constraint is
     * excluded until someone decides it is terminal.
     */
    static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");

    private static final String COUNT_DUE_EXECUTIONS = """
            SELECT COUNT(*) FROM flow_execution
             WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')
               AND COALESCE(completed_at, started_at) < ?
            """;

    /**
     * Cascades to {@code flow_step_log} and {@code flow_pending_resume} via their
     * {@code ON DELETE CASCADE} foreign keys — those are the only two tables
     * referencing {@code flow_execution}, both verified cascading, so no orphan
     * rows are left behind and no delete can fail on a dependent constraint.
     */
    private static final String DELETE_EXECUTIONS = """
            DELETE FROM flow_execution
             WHERE id IN (
                   SELECT id FROM flow_execution
                    WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')
                      AND COALESCE(completed_at, started_at) < ?
                    ORDER BY COALESCE(completed_at, started_at)
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
            """;

    private static final String COUNT_DUE_JOB_LOGS = """
            SELECT COUNT(*) FROM job_execution_log
             WHERE COALESCE(completed_at, started_at) < ?
            """;

    private static final String DELETE_JOB_LOGS = """
            DELETE FROM job_execution_log
             WHERE id IN (
                   SELECT id FROM job_execution_log
                    WHERE COALESCE(completed_at, started_at) < ?
                    ORDER BY COALESCE(completed_at, started_at)
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
            """;

    private final JdbcTemplate jdbc;

    public FlowLogRetentionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** How many terminal executions are older than {@code cutoff}. */
    public long countDueExecutions(Instant cutoff) {
        Long count = jdbc.queryForObject(COUNT_DUE_EXECUTIONS, Long.class, Timestamp.from(cutoff));
        return count == null ? 0L : count;
    }

    /** Deletes up to {@code batchSize} due executions; returns rows removed. */
    public int deleteExecutionBatch(Instant cutoff, int batchSize) {
        return jdbc.update(DELETE_EXECUTIONS, Timestamp.from(cutoff), batchSize);
    }

    /**
     * How many job-execution log rows are older than {@code cutoff}.
     *
     * <p>No status filter here, unlike executions: {@code job_execution_log} has
     * no resumable state to strand, and a row whose run started before the cutoff
     * and never completed is a dead scheduler run, not work in progress.
     */
    public long countDueJobLogs(Instant cutoff) {
        Long count = jdbc.queryForObject(COUNT_DUE_JOB_LOGS, Long.class, Timestamp.from(cutoff));
        return count == null ? 0L : count;
    }

    /** Deletes up to {@code batchSize} due job-log rows; returns rows removed. */
    public int deleteJobLogBatch(Instant cutoff, int batchSize) {
        return jdbc.update(DELETE_JOB_LOGS, Timestamp.from(cutoff), batchSize);
    }
}
