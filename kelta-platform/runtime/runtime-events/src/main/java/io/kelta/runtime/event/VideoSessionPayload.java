package io.kelta.runtime.event;

/**
 * Payload for video session lifecycle events (telehealth slice 5). Published
 * to {@code kelta.video.session.<tenantId>.<sessionId>} inside a
 * {@link PlatformEvent} envelope — ids and state only, never media/PHI.
 * NATS_TRIGGERED flows key automations off these (post-visit follow-up,
 * no-show detection).
 *
 * @since 1.0.0
 */
public class VideoSessionPayload {

    private String sessionId;
    private String appointmentId;
    private String conversationId;
    private String status;
    private Integer durationSeconds;

    public VideoSessionPayload() {
    }

    public VideoSessionPayload(String sessionId, String appointmentId, String conversationId,
                               String status, Integer durationSeconds) {
        this.sessionId = sessionId;
        this.appointmentId = appointmentId;
        this.conversationId = conversationId;
        this.status = status;
        this.durationSeconds = durationSeconds;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
}
