package com.emf.runtime.context;

/**
 * Thread-local holder for the current tenant ID and slug.
 * Set by the DynamicCollectionRouter before calling query engine methods.
 * Used by DefaultQueryEngine to include accurate tenant ID in record change events,
 * and by PhysicalTableStorageAdapter to resolve tenant-specific database schemas.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_SLUG = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String get() {
        return CURRENT_TENANT.get();
    }

    public static void setSlug(String slug) {
        CURRENT_TENANT_SLUG.set(slug);
    }

    public static String getSlug() {
        return CURRENT_TENANT_SLUG.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_TENANT_SLUG.remove();
    }
}
