package com.emf.controlplane.dto;

import com.emf.controlplane.entity.WebhookDelivery;

import java.time.Instant;

public class WebhookDeliveryDto {

    private String id;
    private String webhookId;
    private String eventType;
    private String payload;
    private Integer responseStatus;
    private String responseBody;
    private int attemptCount;
    private String status;
    private Instant nextRetryAt;
    private Instant deliveredAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static WebhookDeliveryDto fromEntity(WebhookDelivery entity) {
        WebhookDeliveryDto dto = new WebhookDeliveryDto();
        dto.setId(entity.getId());
        dto.setWebhookId(entity.getWebhook().getId());
        dto.setEventType(entity.getEventType());
        dto.setPayload(entity.getPayload());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setResponseBody(entity.getResponseBody());
        dto.setAttemptCount(entity.getAttemptCount());
        dto.setStatus(entity.getStatus());
        dto.setNextRetryAt(entity.getNextRetryAt());
        dto.setDeliveredAt(entity.getDeliveredAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWebhookId() { return webhookId; }
    public void setWebhookId(String webhookId) { this.webhookId = webhookId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
