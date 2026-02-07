package com.emf.controlplane.dto;

/**
 * Represents the governor limits for a tenant.
 * Parsed from the tenant's limits JSONB column.
 */
public record GovernorLimits(
    int apiCallsPerDay,
    int storageGb,
    int maxUsers,
    int maxCollections,
    int maxFieldsPerCollection,
    int maxWorkflows,
    int maxReports
) {
    /** Default governor limits for new tenants. */
    public static GovernorLimits defaults() {
        return new GovernorLimits(100_000, 10, 100, 200, 500, 50, 200);
    }
}
