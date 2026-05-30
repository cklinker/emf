package io.kelta.worker.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tier-based default quotas for tenants. The {@code tenant.edition} column
 * (FREE / PROFESSIONAL / ENTERPRISE / UNLIMITED) drives per-tier defaults that
 * a customer-specific {@code tenant.limits} JSONB override can raise or lower
 * on a per-tenant basis.
 *
 * <p>Defaults intentionally diverge between tiers so a FREE tenant cannot
 * silently consume an ENTERPRISE-sized resource budget on a shared cluster.
 * Override semantics: every key present in the tenant's {@code limits} JSONB
 * replaces the tier default for that key.
 */
public final class TenantTierQuotas {

    /** Recognized tenant edition values; matches the V8 check constraint. */
    public enum Tier {
        FREE, PROFESSIONAL, ENTERPRISE, UNLIMITED;

        public static Tier fromEdition(String edition) {
            if (edition == null || edition.isBlank()) {
                return PROFESSIONAL;
            }
            try {
                return Tier.valueOf(edition.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return PROFESSIONAL;
            }
        }
    }

    public static final String KEY_API_CALLS_PER_DAY = "apiCallsPerDay";
    public static final String KEY_STORAGE_GB = "storageGb";
    public static final String KEY_MAX_USERS = "maxUsers";
    public static final String KEY_MAX_COLLECTIONS = "maxCollections";
    public static final String KEY_MAX_FIELDS_PER_COLLECTION = "maxFieldsPerCollection";
    public static final String KEY_MAX_WORKFLOWS = "maxWorkflows";
    public static final String KEY_MAX_REPORTS = "maxReports";
    public static final String KEY_AI_TOKENS_PER_MONTH = "aiTokensPerMonth";
    public static final String KEY_AI_ENABLED = "aiEnabled";

    private TenantTierQuotas() {}

    /** Defaults for the given tier. Returns a fresh mutable map each call. */
    public static Map<String, Object> defaultsFor(Tier tier) {
        Map<String, Object> q = new LinkedHashMap<>();
        switch (tier) {
            case FREE -> {
                q.put(KEY_API_CALLS_PER_DAY, 10_000);
                q.put(KEY_STORAGE_GB, 1);
                q.put(KEY_MAX_USERS, 5);
                q.put(KEY_MAX_COLLECTIONS, 10);
                q.put(KEY_MAX_FIELDS_PER_COLLECTION, 50);
                q.put(KEY_MAX_WORKFLOWS, 5);
                q.put(KEY_MAX_REPORTS, 10);
                q.put(KEY_AI_TOKENS_PER_MONTH, 100_000L);
                q.put(KEY_AI_ENABLED, false);
            }
            case PROFESSIONAL -> {
                q.put(KEY_API_CALLS_PER_DAY, 100_000);
                q.put(KEY_STORAGE_GB, 10);
                q.put(KEY_MAX_USERS, 100);
                q.put(KEY_MAX_COLLECTIONS, 200);
                q.put(KEY_MAX_FIELDS_PER_COLLECTION, 500);
                q.put(KEY_MAX_WORKFLOWS, 50);
                q.put(KEY_MAX_REPORTS, 200);
                q.put(KEY_AI_TOKENS_PER_MONTH, 1_000_000L);
                q.put(KEY_AI_ENABLED, true);
            }
            case ENTERPRISE -> {
                q.put(KEY_API_CALLS_PER_DAY, 1_000_000);
                q.put(KEY_STORAGE_GB, 100);
                q.put(KEY_MAX_USERS, 1_000);
                q.put(KEY_MAX_COLLECTIONS, 2_000);
                q.put(KEY_MAX_FIELDS_PER_COLLECTION, 1_000);
                q.put(KEY_MAX_WORKFLOWS, 500);
                q.put(KEY_MAX_REPORTS, 2_000);
                q.put(KEY_AI_TOKENS_PER_MONTH, 10_000_000L);
                q.put(KEY_AI_ENABLED, true);
            }
            case UNLIMITED -> {
                q.put(KEY_API_CALLS_PER_DAY, Integer.MAX_VALUE);
                q.put(KEY_STORAGE_GB, Integer.MAX_VALUE);
                q.put(KEY_MAX_USERS, Integer.MAX_VALUE);
                q.put(KEY_MAX_COLLECTIONS, Integer.MAX_VALUE);
                q.put(KEY_MAX_FIELDS_PER_COLLECTION, Integer.MAX_VALUE);
                q.put(KEY_MAX_WORKFLOWS, Integer.MAX_VALUE);
                q.put(KEY_MAX_REPORTS, Integer.MAX_VALUE);
                q.put(KEY_AI_TOKENS_PER_MONTH, Long.MAX_VALUE);
                q.put(KEY_AI_ENABLED, true);
            }
        }
        return q;
    }

    /**
     * Merge tier defaults with the tenant's customer-specific overrides.
     * Override keys win; keys absent in the override fall back to the tier
     * default.
     */
    public static Map<String, Object> mergeOverrides(Tier tier, Map<String, Object> overrides) {
        Map<String, Object> merged = defaultsFor(tier);
        if (overrides != null) {
            overrides.forEach((k, v) -> {
                if (v != null) {
                    merged.put(k, v);
                }
            });
        }
        return merged;
    }
}
