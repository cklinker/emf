package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "webhook")
public class Webhook extends TenantScopedEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "events", columnDefinition = "jsonb")
    private String events;

    @Column(name = "collection_id", length = 36)
    private String collectionId;

    @Column(name = "filter_formula", columnDefinition = "TEXT")
    private String filterFormula;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private String headers;

    @Column(name = "secret", length = 200)
    private String secret;

    @Column(name = "active")
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy", columnDefinition = "jsonb")
    private String retryPolicy;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @OneToMany(mappedBy = "webhook", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<WebhookDelivery> deliveries = new ArrayList<>();

    public Webhook() { super(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getEvents() { return events; }
    public void setEvents(String events) { this.events = events; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getFilterFormula() { return filterFormula; }
    public void setFilterFormula(String filterFormula) { this.filterFormula = filterFormula; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(String retryPolicy) { this.retryPolicy = retryPolicy; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public List<WebhookDelivery> getDeliveries() { return deliveries; }
    public void setDeliveries(List<WebhookDelivery> deliveries) { this.deliveries = deliveries; }
}
