package io.kelta.runtime.context;

/**
 * Holder for the current tenant ID and slug.
 * <p>
 * Supports both the modern {@link ScopedValue}-based API (preferred) and the
 * legacy {@link ThreadLocal}-based API for backward compatibility during migration.
 * <p>
 * <b>Preferred usage:</b> Use {@link #runWithTenant} or {@link #callWithTenant} to
 * establish a structured tenant scope that works correctly with virtual threads.
 * <p>
 * <b>Legacy usage:</b> {@link #set}/{@link #clear} still work via ThreadLocal
 * but are deprecated and will be removed in a future release.
 */
public final class TenantContext {

    // ScopedValue-based (preferred, virtual-thread safe)
    public static final ScopedValue<String> CURRENT_TENANT = ScopedValue.newInstance();
    public static final ScopedValue<String> CURRENT_TENANT_SLUG = ScopedValue.newInstance();

    // ThreadLocal fallback (deprecated, for backward compatibility)
    private static final ThreadLocal<String> LEGACY_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> LEGACY_TENANT_SLUG = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * Returns the current tenant ID from either ScopedValue or ThreadLocal.
     */
    public static String get() {
        return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : LEGACY_TENANT.get();
    }

    /**
     * Returns the current tenant slug from either ScopedValue or ThreadLocal.
     */
    public static String getSlug() {
        return CURRENT_TENANT_SLUG.isBound() ? CURRENT_TENANT_SLUG.get() : LEGACY_TENANT_SLUG.get();
    }

    // ── Modern ScopedValue API (preferred) ──────────────────────────────

    /**
     * Executes the given operation within a tenant scope (ID only).
     */
    public static void runWithTenant(String tenantId, Runnable operation) {
        ScopedValue.where(CURRENT_TENANT, tenantId).run(operation);
    }

    /**
     * Executes the given operation within a tenant scope with both ID and slug.
     */
    public static void runWithTenant(String tenantId, String tenantSlug, Runnable operation) {
        ScopedValue.where(CURRENT_TENANT, tenantId)
                   .where(CURRENT_TENANT_SLUG, tenantSlug)
                   .run(operation);
    }

    /**
     * Executes the given operation within a tenant scope and returns a result.
     */
    public static <T> T callWithTenant(String tenantId, ScopedValue.CallableOp<T, RuntimeException> operation) {
        return ScopedValue.where(CURRENT_TENANT, tenantId).call(operation);
    }

    /**
     * Executes the given operation within a tenant scope with both ID and slug,
     * and returns a result.
     */
    public static <T> T callWithTenant(String tenantId, String tenantSlug, ScopedValue.CallableOp<T, RuntimeException> operation) {
        return ScopedValue.where(CURRENT_TENANT, tenantId)
                          .where(CURRENT_TENANT_SLUG, tenantSlug)
                          .call(operation);
    }

    // ── Legacy ThreadLocal API (deprecated) ─────────────────────────────

    /**
     * @deprecated Use {@link #runWithTenant(String, Runnable)} instead.
     */
    @Deprecated(forRemoval = true)
    public static void set(String tenantId) {
        LEGACY_TENANT.set(tenantId);
    }

    /**
     * @deprecated Use {@link #runWithTenant(String, String, Runnable)} instead.
     */
    @Deprecated(forRemoval = true)
    public static void setSlug(String slug) {
        LEGACY_TENANT_SLUG.set(slug);
    }

    /**
     * @deprecated No longer needed with ScopedValue — scope is automatically bounded.
     */
    @Deprecated(forRemoval = true)
    public static void clear() {
        LEGACY_TENANT.remove();
        LEGACY_TENANT_SLUG.remove();
    }
}
