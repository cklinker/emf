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
 * <p>
 * <b>Platform scope:</b> A small number of internal entry points — Flyway migrations,
 * bootstrap controllers, and scheduled cross-tenant jobs — legitimately need to bypass
 * tenant isolation. These paths use {@link #runAsPlatform} / {@link #callAsPlatform},
 * which binds the reserved {@value #PLATFORM_SENTINEL} value. The matching RLS policy
 * grants full access only when this sentinel is bound; any blank/unset context is
 * rejected by {@code TenantAwareDataSource} before a query can run.
 */
public final class TenantContext {

    /**
     * Reserved tenant-ID value that identifies platform-internal execution. The
     * database RLS policies explicitly match against this sentinel — an empty or
     * null {@code current_tenant_id} does <em>not</em> grant access.
     */
    public static final String PLATFORM_SENTINEL = "__platform__";

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

    /**
     * Returns true if the current scope is running under the reserved platform sentinel.
     */
    public static boolean isPlatform() {
        return PLATFORM_SENTINEL.equals(get());
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

    /**
     * Executes {@code operation} under the reserved platform sentinel, granting
     * cross-tenant access via the {@code platform_bypass} RLS policy. Reserve this
     * for Flyway, bootstrap, and scheduled jobs that genuinely operate across
     * all tenants — never for code paths that receive a real tenant ID.
     */
    public static void runAsPlatform(Runnable operation) {
        ScopedValue.where(CURRENT_TENANT, PLATFORM_SENTINEL).run(operation);
    }

    /**
     * Callable variant of {@link #runAsPlatform}.
     */
    public static <T> T callAsPlatform(ScopedValue.CallableOp<T, RuntimeException> operation) {
        return ScopedValue.where(CURRENT_TENANT, PLATFORM_SENTINEL).call(operation);
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
