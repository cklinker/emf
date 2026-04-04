package io.kelta.worker.repository;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for scheduled job persistence and execution tracking.
 *
 * <p>Uses {@code SELECT FOR UPDATE SKIP LOCKED} for leader election in
 * multi-worker deployments — only one worker picks up each due job.
 *
 * @since 1.0.0
 */
@Repository
public class ScheduledJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public ScheduledJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Finds due jobs that are active and past their next_run_at.
     * Uses SKIP LOCKED for leader election — concurrent workers won't pick up the same job.
     */
    public List<Map<String, Object>> findDueJobs() {
        return jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, job_type, job_reference_id, cron_expression, timezone " +
                        "FROM scheduled_job " +
                        "WHERE active = true AND next_run_at <= NOW() " +
                        "ORDER BY next_run_at " +
                        "LIMIT 50 " +
                        "FOR UPDATE SKIP LOCKED"
        );
    }

    /**
     * Loads the flow definition for a scheduled job.
     */
    public Optional<Map<String, Object>> findFlowById(String flowId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, definition, trigger_config, active FROM flow WHERE id = ?",
                flowId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Updates the scheduled job after execution.
     */
    public void updateAfterExecution(String jobId, String lastStatus, Instant lastRunAt, Instant nextRunAt) {
        jdbcTemplate.update(
                "UPDATE scheduled_job SET last_run_at = ?, last_status = ?, next_run_at = ?, updated_at = ? " +
                        "WHERE id = ?",
                Timestamp.from(lastRunAt), lastStatus, nextRunAt != null ? Timestamp.from(nextRunAt) : null,
                Timestamp.from(Instant.now()), jobId
        );
    }

    /**
     * Inserts an execution history record into job_execution_log.
     */
    public String insertExecutionLog(String jobId, String status, String errorMessage,
                                     Instant startedAt, Instant completedAt, long durationMs) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO job_execution_log (id, job_id, status, error_message, started_at, completed_at, duration_ms) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, jobId, status, errorMessage,
                Timestamp.from(startedAt),
                completedAt != null ? Timestamp.from(completedAt) : null,
                (int) durationMs
        );
        return id;
    }

    /**
     * Calculates the next run time from NOW for a given cron expression and timezone.
     * Always calculates from NOW to prevent burst-fire after downtime.
     */
    public static Instant calculateNextRunAt(String cronExpression, String timezone) {
        CronExpression cron = CronExpression.parse(cronExpression);
        ZoneId zone = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.of("UTC");
        ZonedDateTime next = cron.next(ZonedDateTime.now(zone));
        return next != null ? next.toInstant() : null;
    }

    /**
     * Updates next_run_at for a job (used on create and resume).
     */
    public void updateNextRunAt(String jobId, Instant nextRunAt) {
        jdbcTemplate.update(
                "UPDATE scheduled_job SET next_run_at = ?, updated_at = ? WHERE id = ?",
                nextRunAt != null ? Timestamp.from(nextRunAt) : null, Timestamp.from(Instant.now()), jobId
        );
    }

    /**
     * Pauses a job — sets active=false and clears next_run_at.
     */
    public int pause(String jobId, String tenantId) {
        return jdbcTemplate.update(
                "UPDATE scheduled_job SET active = false, next_run_at = NULL, updated_at = ? " +
                        "WHERE id = ? AND tenant_id = ?",
                Timestamp.from(Instant.now()), jobId, tenantId
        );
    }

    /**
     * Resumes a job — sets active=true and recalculates next_run_at from NOW.
     */
    public int resume(String jobId, String tenantId, Instant nextRunAt) {
        return jdbcTemplate.update(
                "UPDATE scheduled_job SET active = true, next_run_at = ?, updated_at = ? " +
                        "WHERE id = ? AND tenant_id = ?",
                Timestamp.from(nextRunAt), Timestamp.from(Instant.now()), jobId, tenantId
        );
    }

    /**
     * Loads a script by ID for scheduled execution.
     */
    public Optional<Map<String, Object>> findScriptById(String scriptId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, source_code, active, language FROM script WHERE id = ?",
                scriptId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Loads a report configuration by ID for scheduled export.
     */
    public Optional<Map<String, Object>> findReportById(String reportId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, report_type, primary_collection_id AS \"primaryCollectionId\", " +
                        "columns, filters, sort_order AS \"sortOrder\", " +
                        "sort_by AS \"sortBy\", sort_direction AS \"sortDirection\", " +
                        "group_by AS \"groupBy\" " +
                        "FROM report WHERE id = ?",
                reportId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds a job by ID and tenant (tenant-scoped access).
     */
    public Optional<Map<String, Object>> findByIdAndTenant(String jobId, String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, name, cron_expression, timezone, active FROM scheduled_job " +
                        "WHERE id = ? AND tenant_id = ?",
                jobId, tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
