package com.emf.controlplane.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a policy applied to a collection route/operation.
 * Links a collection operation (e.g., CREATE, READ, UPDATE, DELETE) to a policy.
 */
@Entity
@Table(name = "route_policy")
@EntityListeners(AuditingEntityListener.class)
public class RoutePolicy {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RoutePolicy() {
        this.id = UUID.randomUUID().toString();
    }

    public RoutePolicy(Collection collection, String operation, Policy policy) {
        this();
        this.collection = collection;
        this.operation = operation;
        this.policy = policy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutePolicy that = (RoutePolicy) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoutePolicy{" +
                "id='" + id + '\'' +
                ", operation='" + operation + '\'' +
                '}';
    }
}
