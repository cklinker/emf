package io.kelta.runtime.event;

/**
 * Payload for chat conversation lifecycle events (telehealth slice 2).
 * Published to {@code kelta.chat.conversation.<tenantId>.<conversationId>}
 * inside a {@link PlatformEvent} envelope. Ids and state only — no content.
 *
 * @since 1.0.0
 */
public class ChatConversationPayload {

    private String conversationId;
    private String status;
    private String queueId;
    private String assignedTo;

    public ChatConversationPayload() {
    }

    public ChatConversationPayload(String conversationId, String status,
                                   String queueId, String assignedTo) {
        this.conversationId = conversationId;
        this.status = status;
        this.queueId = queueId;
        this.assignedTo = assignedTo;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getQueueId() { return queueId; }
    public void setQueueId(String queueId) { this.queueId = queueId; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
}
