package io.kelta.mcp.observe;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.auth.RequestSlugHolder;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.springframework.stereotype.Component;

/**
 * Outermost decorator: copies per-request data from the SDK's
 * transport context (set by {@link KeltaTransportContextExtractor}
 * during HTTP intake) into thread-local holders for the duration
 * of the tool handler.
 *
 * <p>Why this exists: the MCP SDK dispatches sync tool handlers on
 * Reactor scheduler threads, not the servlet thread. The
 * {@code ThreadLocal} the auth filter populates on the inbound
 * thread is therefore invisible to the handler. The transport
 * context is the SDK-blessed mechanism for thread-safe propagation;
 * this decorator bridges it back into the holders the downstream
 * HTTP client reads from.
 *
 * <p>Two pieces of data ride along:
 * <ul>
 *   <li>{@link RequestPatHolder} — PAT for the {@code Authorization}
 *       header on outbound calls.</li>
 *   <li>{@link RequestSlugHolder} — tenant slug, parsed from the
 *       MCP URL ({@code /{tenantSlug}/mcp/(user|admin)}), used as
 *       the path prefix on every gateway call.</li>
 * </ul>
 *
 * <p>Wraps OUTSIDE the rate limiter (which keys its bucket on the
 * PAT) and the observation decorator.
 */
@Component
public class PatPropagatingToolDecorator {

    public SyncToolSpecification decorate(SyncToolSpecification original) {
        var inner = original.callHandler();
        return SyncToolSpecification.builder()
                .tool(original.tool())
                .callHandler((exchange, request) -> {
                    String pat = null;
                    String slug = null;
                    if (exchange != null && exchange.transportContext() != null) {
                        Object patValue = exchange.transportContext().get(KeltaTransportContextExtractor.PAT_KEY);
                        if (patValue instanceof String s) pat = s;
                        Object slugValue = exchange.transportContext().get(KeltaTransportContextExtractor.TENANT_SLUG_KEY);
                        if (slugValue instanceof String s) slug = s;
                    }
                    if (pat != null) RequestPatHolder.set(pat);
                    if (slug != null) RequestSlugHolder.set(slug);
                    try {
                        return inner.apply(exchange, request);
                    } finally {
                        RequestSlugHolder.clear();
                        RequestPatHolder.clear();
                    }
                })
                .build();
    }
}
