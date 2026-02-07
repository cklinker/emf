package com.emf.controlplane.tenant;

/**
 * Thread-local storage for current tenant context.
 * Set on every request by TenantResolutionFilter, read by all services and repositories,
 * cleared in the filter's finally block.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    /**
     * Immutable tenant context carrying tenant ID and slug.
     */
    public record TenantContext(String tenantId, String tenantSlug) {}

    private TenantContextHolder() {
        // utility class
    }

    /**
     * Sets the tenant context for the current thread.
     */
    public static void set(String tenantId, String tenantSlug) {
        CONTEXT.set(new TenantContext(tenantId, tenantSlug));
    }

    /**
     * Returns the current tenant context, or null if not set.
     */
    public static TenantContext get() {
        return CONTEXT.get();
    }

    /**
     * Returns the current tenant ID, or null if not set.
     */
    public static String getTenantId() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.tenantId() : null;
    }

    /**
     * Returns the current tenant slug, or null if not set.
     */
    public static String getTenantSlug() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx.tenantSlug() : null;
    }

    /**
     * Returns the current tenant ID, throwing if not set.
     * @throws IllegalStateException if no tenant context is set
     */
    public static String requireTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context set");
        }
        return tenantId;
    }

    /**
     * Clears the tenant context for the current thread.
     * Must be called in a finally block to prevent ThreadLocal leaks.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Returns true if a tenant context is set for the current thread.
     */
    public static boolean isSet() {
        return CONTEXT.get() != null;
    }
}
