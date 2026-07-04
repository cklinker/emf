package io.kelta.testharness.scenarios;

import io.kelta.testharness.ScenarioBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flow Wait-state park + scheduled resume through the real stack (gateway → worker →
 * FlowEngine → JdbcFlowStore → Postgres), against {@code flow_execution} and
 * {@code flow_pending_resume} (V71).
 *
 * <p>Regression guard for the persistence path Mockito worker tests can't reach: a Wait
 * with {@code Seconds} above the 10s inline threshold must park the execution WAITING and
 * write a real {@code flow_pending_resume} row with {@code resume_at ≈ now + seconds};
 * {@code FlowResumePollerConfig} (10s poll) must then claim it via
 * {@code JdbcFlowStore.claimPendingResumes} ({@code UPDATE … FOR UPDATE SKIP LOCKED
 * RETURNING}) and {@code FlowEngine.resumeExecution} must delete the row and complete the
 * flow. (See the DB-constraint-test-gap lesson — a mocked JdbcTemplate would pass even if
 * the claim SQL or the FK to flow_execution were broken.)
 */
@DisplayName("Flow Wait Resume Scenario")
class FlowWaitResumeScenarioTest extends ScenarioBase {

    private static final long WAITING_TIMEOUT_MS  = 15_000;
    /** 11s wait + up to 10s poller interval + resume execution, with CI headroom. */
    private static final long COMPLETED_TIMEOUT_MS = 60_000;

    @Test
    @DisplayName("parks a Seconds=11 Wait as WAITING with a pending-resume row, then the poller claims it once and completes the flow")
    @SuppressWarnings("unchecked")
    void parksWaitingAndResumesViaPoller() throws Exception {
        String token = auth.loginAsAdmin();
        String tenantId = auth.extractTenantId(token);
        String slug = tenants.slugForTenantId(tenantId);

        waitForStatus(gatewayClientWithToken(token), "/" + slug + "/api/flows", HttpStatus.OK, 20);

        // A minimal AUTOLAUNCHED flow: Wait 11s (> the 10s inline-sleep threshold, so the
        // engine must persist and park) → Succeed.
        String suffix = Long.toHexString(System.nanoTime());
        Map<String, Object> definition = Map.of(
                "Comment", "Harness wait-resume scenario",
                "StartAt", "WaitEleven",
                "States", Map.of(
                        "WaitEleven", Map.of("Type", "Wait", "Seconds", 11, "Next", "Finish"),
                        "Finish", Map.of("Type", "Succeed")));
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "type", "flows",
                        "attributes", Map.of(
                                "name", "wait-resume-harness-" + suffix,
                                "flowType", "AUTOLAUNCHED",
                                "active", true,
                                "definition", definition)));

        ResponseEntity<Map> created = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/flows")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().toEntity(Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("flow create should succeed").isTrue();
        String flowId = (String) ((Map<String, Object>) created.getBody().get("data")).get("id");
        assertThat(flowId).isNotBlank();

        // Manual invocation — the response returns immediately because an >10s Wait parks
        // instead of sleeping inline.
        ResponseEntity<Map> executed = gatewayClientWithToken(token)
                .post().uri("/" + slug + "/api/flows/" + flowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("input", Map.of()))
                .retrieve().toEntity(Map.class);
        assertThat(executed.getStatusCode().is2xxSuccessful())
                .as("flow execute should be accepted").isTrue();
        String executionId = (String) ((Map<String, Object>) executed.getBody().get("data")).get("id");
        assertThat(executionId).isNotBlank();

        // The execution parks WAITING at the Wait state.
        String status = pollStatusUntil(token, slug, executionId, "WAITING", WAITING_TIMEOUT_MS);
        assertThat(status).as("execution parks WAITING at the Wait state").isEqualTo("WAITING");

        // A real flow_pending_resume row exists: time-based (no resume_event), unclaimed,
        // due ~11s after creation. Compare resume_at against the row's own created_at so
        // the assertion is immune to clock skew between the test JVM and the DB. The row is
        // inserted right after the WAITING status update, so retry briefly to close the gap.
        try (Connection db = openDbConnection();
             PreparedStatement ps = db.prepareStatement("""
                     SELECT claimed_by, resume_event, tenant_id,
                            EXTRACT(EPOCH FROM (resume_at - created_at)) AS delay_seconds
                     FROM flow_pending_resume
                     WHERE execution_id = ?
                     """)) {
            ps.setString(1, executionId);
            waitForPendingResumeRow(ps);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("a flow_pending_resume row was written for the parked execution").isTrue();
                assertThat(rs.getString("resume_event"))
                        .as("a Seconds wait is time-based, not event-based").isNull();
                assertThat(rs.getString("claimed_by"))
                        .as("the row is unclaimed until resume_at is due").isNull();
                assertThat(rs.getString("tenant_id")).isEqualTo(tenantId);
                assertThat(rs.getDouble("delay_seconds"))
                        .as("resume_at is ~11s after the row was created")
                        .isBetween(9.0, 13.0);
                assertThat(rs.next())
                        .as("exactly one pending-resume row per parked execution").isFalse();
            }
        }

        // The poller claims the due row (SELECT FOR UPDATE SKIP LOCKED → exactly one
        // claimant) and resumeExecution completes the flow.
        status = pollStatusUntil(token, slug, executionId, "COMPLETED", COMPLETED_TIMEOUT_MS);
        assertThat(status)
                .as("the resume poller claimed the row and completed the flow")
                .isEqualTo("COMPLETED");

        // resumeExecution deletes the pending-resume row before advancing — a leftover row
        // would be re-claimed and double-resumed.
        try (Connection db = openDbConnection();
             PreparedStatement ps = db.prepareStatement(
                     "SELECT COUNT(*) FROM flow_pending_resume WHERE execution_id = ?")) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("the claimed pending-resume row is deleted on resume").isZero();
            }
        }
    }

    /**
     * Re-runs the prepared pending-resume query until it returns a row (max ~5s).
     * The row lands milliseconds after the WAITING status update; this only guards
     * against observing the tiny gap between the two writes on a slow CI runner.
     */
    private void waitForPendingResumeRow(PreparedStatement ps) throws Exception {
        for (int i = 0; i < 10; i++) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
            Thread.sleep(500);
        }
        // Fall through — the assertion on the next executeQuery() reports the failure.
    }

    /**
     * Polls the execution until it reaches {@code targetStatus} or a terminal status,
     * returning the last observed status.
     */
    @SuppressWarnings("unchecked")
    private String pollStatusUntil(String token, String slug, String executionId,
                                   String targetStatus, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<Map> resp = gatewayClientWithToken(token)
                    .get().uri("/" + slug + "/api/flows/executions/" + executionId)
                    .retrieve().toEntity(Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            status = (String) attrs.get("status");
            if (targetStatus.equals(status)) {
                return status;
            }
            // FAILED / CANCELLED will never transition further — bail out with the evidence.
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                throw new AssertionError("Execution " + executionId + " reached terminal status "
                        + status + " while waiting for " + targetStatus
                        + " (error: " + attrs.get("errorMessage") + ")");
            }
            Thread.sleep(500);
        }
        return status;
    }
}
