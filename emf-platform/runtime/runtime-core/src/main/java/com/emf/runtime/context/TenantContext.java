package com.emf.runtime.context;

/**
 * Thread-local holder for the current tenant ID.
 * Set by the DynamicCollectionRouter before calling query engine methods.
 * Used by DefaultQueryEngine to include accurate tenant ID in record change events.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
