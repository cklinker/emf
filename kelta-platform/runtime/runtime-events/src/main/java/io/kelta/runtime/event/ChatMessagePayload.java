package io.kelta.runtime.event;

/**
 * Payload for chat message events (telehealth slice 2). Published to
 * {@code kelta.chat.message.<tenantId>.<conversationId>} inside a
 * {@link PlatformEvent} envelope.
 *
 * <p>Carries ids and metadata ONLY — never the message body. The gateway
 * bridge fans these to conversation-joined sockets as an invalidation signal;
 * clients fetch content over the authorized HTTP path.
 *
 * @since 1.0.0
 */
public class ChatMessagePayload {

    private String conversationId;
    private String messageId;
    private String senderId;
    private String senderType;
    private String kind;

    public ChatMessagePayload() {
    }

    public ChatMessagePayload(String conversationId, String messageId,
                              String senderId, String senderType, String kind) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderType = senderType;
        this.kind = kind;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
}
