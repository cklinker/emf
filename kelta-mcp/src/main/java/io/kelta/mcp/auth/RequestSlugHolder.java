package io.kelta.mcp.auth;

/**
 * Thread-local holder for the per-request tenant slug, the analogue
 * of {@link RequestPatHolder} for the slug.
 *
 * <p>The slug travels in the MCP URL itself
 * ({@code /mcp/{tenantSlug}/(user|admin)}) so a single deployment can
 * serve PATs from any number of tenants — each Claude Code config has
 * a distinct URL bound to the right slug. {@link McpAuthFilter}
 * extracts the slug from the URL on intake; the propagating decorator
 * pushes it into this holder for the duration of the tool handler.
 */
public final class RequestSlugHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestSlugHolder() {}

    public static void set(String slug) {
        CURRENT.set(slug);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
