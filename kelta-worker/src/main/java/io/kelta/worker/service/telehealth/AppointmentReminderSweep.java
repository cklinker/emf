package io.kelta.worker.service.telehealth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reminder sweep (telehealth slice 4): every poll it atomically CLAIMS due
 * appointments — CONFIRMED, unreminded, starting within the reminder offset —
 * by stamping {@code reminder_sent_at} in the same UPDATE that selects them
 * (multi-pod safe without leader election; a claimed row is never re-claimed),
 * then sends the {@code appointment.reminder} template per row.
 *
 * <p>Deliberately platform-owned rather than seeded per-tenant flows (the
 * spec's original idea): deterministic, zero per-tenant setup, and tenants can
 * still layer NATS/record-triggered flows on top. The claim-then-send order
 * means a pod crash between claim and send drops that reminder rather than
 * double-sending — the preferable failure for a courtesy email.
 */
@Service
public class AppointmentReminderSweep {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderSweep.class);

    private final JdbcTemplate jdbcTemplate;
    private final AppointmentService appointmentService;
    private final boolean enabled;
    private final int offsetMinutes;

    public AppointmentReminderSweep(JdbcTemplate jdbcTemplate,
                                    AppointmentService appointmentService,
                                    @Value("${kelta.telehealth.reminders.enabled:true}") boolean enabled,
                                    @Value("${kelta.telehealth.reminders.offset-minutes:60}") int offsetMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.appointmentService = appointmentService;
        this.enabled = enabled;
        this.offsetMinutes = offsetMinutes;
    }

    @Scheduled(fixedDelayString = "${kelta.telehealth.reminders.poll-interval-ms:60000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            List<Map<String, Object>> claimed = claimDue();
            for (Map<String, Object> appointment : claimed) {
                String tenantId = String.valueOf(appointment.get("tenantId"));
                try {
                    appointmentService.sendTemplate(tenantId, appointment,
                            "appointment.reminder", "APPOINTMENT_REMINDER");
                } catch (Exception e) {
                    log.warn("Reminder send failed for appointment {}: {}",
                            appointment.get("id"), e.getMessage());
                }
            }
            if (!claimed.isEmpty()) {
                log.info("Sent {} appointment reminder(s)", claimed.size());
            }
        } catch (Exception e) {
            log.error("Appointment reminder sweep failed: {}", e.getMessage(), e);
        }
    }

    /** Atomic claim: the UPDATE both selects and marks, so each row fires once fleet-wide. */
    List<Map<String, Object>> claimDue() {
        return jdbcTemplate.query(
                """
                UPDATE telehealth_appointment
                SET reminder_sent_at = NOW(), updated_at = NOW()
                WHERE status = 'CONFIRMED'
                  AND reminder_sent_at IS NULL
                  AND scheduled_start > NOW()
                  AND scheduled_start <= NOW() + make_interval(mins => ?)
                RETURNING id, tenant_id, provider_id, portal_user_id,
                          scheduled_start, scheduled_end, visit_type
                """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("tenantId", rs.getString("tenant_id"));
                    row.put("providerId", rs.getString("provider_id"));
                    row.put("portalUserId", rs.getString("portal_user_id"));
                    row.put("scheduledStart", rs.getTimestamp("scheduled_start").toInstant().toString());
                    row.put("scheduledEnd", rs.getTimestamp("scheduled_end").toInstant().toString());
                    row.put("visitType", rs.getString("visit_type"));
                    return row;
                },
                offsetMinutes);
    }
}
