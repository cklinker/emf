package io.kelta.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Extracts per-request data from the inbound HTTP request and packs
 * it into an {@link McpTransportContext} that the SDK propagates to
 * tool handlers — even though the handler runs on a Reactor scheduler
 * thread, not the servlet thread.
 *
 * <p>The {@code "pat"} key carries the PAT (without the {@code Bearer}
 * prefix). Tool decorators read it back via
 * {@code exchange.transportContext().get("pat")}.
 *
 * <p>{@link McpAuthFilter} still runs first and rejects any request
 * without a valid {@code Bearer klt_*} header — by the time this
 * extractor sees the request, presence is already guaranteed. We
 * defensively re-check anyway so a future routing change that bypasses
 * the filter can't silently leave the context empty.
 */
public final class KeltaTransportContextExtractor
        implements McpTransportContextExtractor<HttpServletRequest> {

    public static final String PAT_KEY = "pat";

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public McpTransportContext extract(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return McpTransportContext.EMPTY;
        }
        String token = header.substring(BEARER_PREFIX.length());
        if (token.isEmpty()) {
            return McpTransportContext.EMPTY;
        }
        return McpTransportContext.create(Map.of(PAT_KEY, token));
    }
}
