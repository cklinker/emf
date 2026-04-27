package io.kelta.mcp.auth;

/**
 * Thread-local holder for the per-request Personal Access Token.
 *
 * <p>Set by {@link McpAuthFilter} on each /mcp/** request from the
 * Authorization header, cleared in a {@code finally} block. Read by
 * the gateway HTTP client when forwarding tool calls.
 *
 * <p>The MCP server runs in synchronous mode so the tool handler executes
 * on the same thread that processed the inbound HTTP request — the PAT
 * captured at filter time is the right PAT for any outbound calls made
 * during that handler.
 */
public final class RequestPatHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestPatHolder() {}

    public static void set(String pat) {
        CURRENT.set(pat);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
