package io.kelta.worker.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the one predicate in {@link MailboxEscalationRepository#claimDue} that silently disabled
 * every breach notification in production.
 *
 * <p>The sweep ran {@code settleStates()} and then {@code claimDue(...)} in the same tick.
 * {@code settleStates} rewrites {@code sla_first_response_state} from {@code PENDING} to
 * {@code BREACHED} the instant a thread passes its due time; {@code claimDue} filtered on
 * {@code = 'PENDING'}. So the only threads the claim could match were the ones that were not yet
 * late — which have nothing to escalate. WARN still fired, because its offset is negative and it
 * runs before the settle. BREACH, BREACH_2 and BREACH_3 could never fire at all.
 *
 * <p>Nothing detected it. The console showed a correct BREACHED badge, the sweep logged no error,
 * and "zero rows claimed" reads exactly the same as "nothing is late". It was found only by seeding
 * a past-due thread in production and watching no escalation appear.
 *
 * <p>This is a test of the SQL <i>text</i>, which is a real limitation — it cannot prove Postgres
 * agrees. It is here because the harness sets {@code SCHEDULER_ENABLED=false}, so no scenario test
 * can drive the sweep, and because the specific mistake was a predicate that reads perfectly and is
 * wrong. Asserting on the string is worth more than asserting on a mock that returns whatever it
 * was told to.
 */
@DisplayName("MailboxEscalationRepository claim SQL")
class MailboxEscalationClaimSqlTest {

    private JdbcTemplate jdbcTemplate;
    private MailboxEscalationRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new MailboxEscalationRepository(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
    }

    private String capturedSql(String clock, String level, int offset) {
        repository.claimDue(clock, level, offset, 50);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        return sql.getValue();
    }

    @Test
    @DisplayName("A BREACHED thread is still claimable — settleStates runs in the same sweep")
    void claimsBreachedThreadsToo() {
        String sql = capturedSql("FIRST_RESPONSE", "BREACH", 0);

        assertThat(sql)
                .as("claimDue must not exclude the state settleStates() moves threads into")
                .doesNotContain("sla_first_response_state = 'PENDING'");
        assertThat(sql).contains("sla_first_response_state IN ('PENDING', 'BREACHED')");
    }

    @Test
    @DisplayName("The resolution clock has the same exposure and the same fix")
    void resolutionClockClaimsBreachedToo() {
        String sql = capturedSql("RESOLUTION", "BREACH", 0);

        assertThat(sql).doesNotContain("sla_resolution_state = 'PENDING'");
        assertThat(sql).contains("sla_resolution_state IN ('PENDING', 'BREACHED')");
    }

    @Test
    @DisplayName("Once-per-level delivery comes from ON CONFLICT, not from the state column")
    void oncePerLevelIsEnforcedByTheUniqueConstraint() {
        // This is why widening the state filter is safe: the insert, not the state, is what stops
        // a thread being escalated twice at the same level on every subsequent sweep.
        assertThat(capturedSql("FIRST_RESPONSE", "BREACH", 0))
                .contains("ON CONFLICT (tenant_id, thread_id, clock, level) DO NOTHING");
    }

    @Test
    @DisplayName("An answered or terminal thread is still excluded")
    void answeredAndTerminalThreadsStayExcluded() {
        // Widening the state filter must not have widened these: a resolved thread and an already
        // answered one have no breach to report.
        String firstResponse = capturedSql("FIRST_RESPONSE", "BREACH", 0);
        assertThat(firstResponse).contains("AND t.first_response_at IS NULL");
        assertThat(firstResponse).contains("'RESOLVED','CLOSED','SPAM','ARCHIVED'");
        assertThat(firstResponse).contains("t.sla_paused_at IS NULL");
    }
}
