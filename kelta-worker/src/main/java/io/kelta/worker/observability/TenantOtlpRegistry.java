package io.kelta.worker.observability;

import java.util.Optional;

/**
 * Resolves a tenant's OTLP export destination (Rec 7 — per-tenant observability).
 *
 * <p>Backed by configuration today ({@code PropertiesTenantOtlpRegistry}); a later
 * slice swaps in a DB + NATS-refreshed source for self-service per-tenant config.
 * Returns empty when a tenant has no enabled export target, in which case its spans
 * are only sent to the platform's default collector.
 */
public interface TenantOtlpRegistry {

    /**
     * @param tenantId the tenant id (the {@code kelta.tenant.id} span attribute value)
     * @return that tenant's export target, or empty if none is configured/enabled
     */
    Optional<OtlpTarget> targetFor(String tenantId);
}
