package com.emf.controlplane.entity;

/**
 * Marker interface for entities that carry a tenant_id.
 *
 * <p>Implemented by {@link TenantScopedEntity} (which covers most entities)
 * and by standalone entities like {@link SetupAuditTrail} and {@link LoginHistory}
 * whose tables lack the {@code created_at}/{@code updated_at} columns required
 * by {@link BaseEntity}.
 *
 * <p>{@link com.emf.controlplane.tenant.TenantWriteGuard} uses this interface
 * to validate that writes go to the correct tenant.
 */
public interface TenantAware {
    String getTenantId();
}
