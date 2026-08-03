package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Current known availability per {@code (tenant, target, slot)} — state, not
 * history. Raw observations are deliberately never stored; only the derived
 * transition is.
 *
 * <p><b>Episodes are the anti-spam mechanism.</b> A fresh {@code episode_id} is
 * minted on every CLOSED→OPEN transition, and the alert dedupe key includes it.
 * So a slot that stays open across a hundred polls keeps one episode and alerts a
 * member once; a slot that closes and genuinely reopens is a new episode and
 * alerts again. Without this, either every poll spams or a reopening is missed.
 */
@Repository
public class AvailabilityStateRepository {

    /** Result of applying an observation: what changed, if anything. */
    public record Transition(boolean opened, String episodeId, String previousStatus) {
        /** True when this observation is a genuine opening worth alerting on. */
        public boolean isAlertable() {
            return opened;
        }
    }

    private static final String COLUMNS = """
            tenant_id, target_id, slot_key, status, episode_id,
            window_start, window_end, quantity, last_seen_at, last_change_at
            """;

    private static final RowMapper<AvailabilityState> MAPPER =
            (rs, rowNum) -> new AvailabilityState(
                    rs.getString("tenant_id"),
                    rs.getString("target_id"),
                    rs.getString("slot_key"),
                    rs.getString("status"),
                    rs.getString("episode_id"),
                    instant(rs.getTimestamp("window_start")),
                    instant(rs.getTimestamp("window_end")),
                    (Integer) rs.getObject("quantity"),
                    instant(rs.getTimestamp("last_seen_at")),
                    instant(rs.getTimestamp("last_change_at")));

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private final JdbcTemplate jdbc;

    public AvailabilityStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AvailabilityState> find(String tenantId, String targetId, String slotKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM availability_state "
                        + "WHERE tenant_id = ? AND target_id = ? AND slot_key = ?",
                        MAPPER, tenantId, targetId, slotKey)
                .stream().findFirst();
    }

    /**
     * Records an observation and reports whether it was a genuine opening.
     *
     * <p>Done as a single {@code INSERT … ON CONFLICT DO UPDATE … RETURNING} so
     * the read-decide-write is atomic: two pods processing the same event
     * concurrently cannot both conclude "this just opened" and both alert. The
     * episode is minted by the statement, and {@code last_change_at} only moves
     * when the status actually changed.
     *
     * @return the transition, with {@code opened=true} only on CLOSED→OPEN (or a
     *         first sighting that is already open)
     */
    public Transition record(String tenantId, String targetId, String slotKey, String status,
                             Instant windowStart, Instant windowEnd, Integer quantity,
                             Instant observedAt) {
        String newEpisode = UUID.randomUUID().toString();
        Timestamp seenAt = Timestamp.from(observedAt == null ? Instant.now() : observedAt);

        return jdbc.queryForObject("""
                        INSERT INTO availability_state
                            (tenant_id, target_id, slot_key, status, episode_id,
                             window_start, window_end, quantity, last_seen_at, last_change_at)
                        VALUES (?, ?, ?, ?, CASE WHEN ? = 'OPEN' THEN ? ELSE NULL END,
                                ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, target_id, slot_key) DO UPDATE
                           SET status = EXCLUDED.status,
                               -- Mint a new episode ONLY on a real CLOSED -> OPEN
                               -- edge; an already-open slot keeps its episode so
                               -- repeat polls do not re-alert.
                               episode_id = CASE
                                   WHEN availability_state.status <> 'OPEN' AND EXCLUDED.status = 'OPEN'
                                       THEN EXCLUDED.episode_id
                                   WHEN EXCLUDED.status = 'OPEN'
                                       THEN availability_state.episode_id
                                   ELSE NULL END,
                               window_start = EXCLUDED.window_start,
                               window_end = EXCLUDED.window_end,
                               quantity = EXCLUDED.quantity,
                               last_seen_at = EXCLUDED.last_seen_at,
                               last_change_at = CASE
                                   WHEN availability_state.status <> EXCLUDED.status
                                       THEN EXCLUDED.last_seen_at
                                   ELSE availability_state.last_change_at END
                     RETURNING episode_id, status
                        """,
                (rs, i) -> {
                    String episodeId = rs.getString("episode_id");
                    // The episode we minted only survives on a genuine CLOSED->OPEN
                    // edge (or a first sighting that is already open) — an
                    // already-open slot keeps its previous episode, so this is
                    // false for repeat polls. That single comparison is the whole
                    // "alert once per opening" rule.
                    boolean opened = newEpisode.equals(episodeId);
                    return new Transition(opened, episodeId, rs.getString("status"));
                },
                tenantId, targetId, slotKey, status,
                status, newEpisode,
                windowStart == null ? null : Timestamp.from(windowStart),
                windowEnd == null ? null : Timestamp.from(windowEnd),
                quantity, seenAt, seenAt);
    }

    /** Current availability row shape. */
    public record AvailabilityState(
            String tenantId,
            String targetId,
            String slotKey,
            String status,
            String episodeId,
            Instant windowStart,
            Instant windowEnd,
            Integer quantity,
            Instant lastSeenAt,
            Instant lastChangeAt) {
    }
}
