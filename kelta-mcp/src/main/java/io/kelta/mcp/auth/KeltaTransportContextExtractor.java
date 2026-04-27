package io.kelta.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts per-request data from the inbound HTTP request and packs
 * it into an {@link McpTransportContext} that the SDK propagates to
 * tool handlers — even though the handler runs on a Reactor scheduler
 * thread, not the servlet thread.
 *
 * <p>{@code "pat"} carries the PAT (without the {@code Bearer} prefix).
 * {@code "tenant_slug"} carries the tenant slug that
 * {@link McpAuthFilter} extracted from the URL path. Tool decorators
 * read both back via {@code exchange.transportContext().get(...)}.
 *
 * <p>{@link McpAuthFilter} runs first and rejects any request without
 * a well-formed URL or a {@code Bearer klt_*} header — by the time
 * this extractor sees the request both fields are guaranteed. We
 * defensively re-check so a future routing change that bypasses the
 * filter can't silently leave the context empty.
 */
public final class KeltaTransportContextExtractor
        implements McpTransportContextExtractor<HttpServletRequest> {

    public static final String PAT_KEY = "pat";
    public static final String TENANT_SLUG_KEY = "tenant_slug";

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public McpTransportContext extract(HttpServletRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();

        String header = request.getHeader(AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            if (!token.isEmpty()) {
                values.put(PAT_KEY, token);
            }
        }

        Object slug = request.getAttribute(McpAuthFilter.SLUG_ATTRIBUTE);
        if (slug instanceof String s && !s.isEmpty()) {
            values.put(TENANT_SLUG_KEY, s);
        }

        return values.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(values);
    }
}
