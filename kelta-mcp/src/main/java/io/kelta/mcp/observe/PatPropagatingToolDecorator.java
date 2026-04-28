package io.kelta.mcp.observe;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.auth.RequestSlugHolder;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import org.springframework.stereotype.Component;

/**
 * Outermost decorator: copies per-request data from the SDK's
 * transport context (set by {@link KeltaTransportContextExtractor}
 * during HTTP intake) into thread-local holders for the duration
 * of the tool handler.
 *
 * <p>Why this exists: the gateway HTTP client reads PAT/slug from
 * thread-local holders rather than threading the context through
 * every method signature. The transport context is the SDK-blessed
 * mechanism for thread-safe propagation; this decorator bridges it
 * back into the holders the gateway client reads from.
 *
 * <p>In stateless mode the {@code McpTransportContext} flows directly
 * to the tool handler as the first BiFunction argument — no
 * {@code exchange.transportContext()} hop needed (and no Reactor
 * scheduler thread switch in the way), so this decorator is now a
 * straight set-then-clear around the inner handler.
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
                .callHandler((context, request) -> {
                    String pat = null;
                    String slug = null;
                    if (context != null) {
                        Object patValue = context.get(KeltaTransportContextExtractor.PAT_KEY);
                        if (patValue instanceof String s) pat = s;
                        Object slugValue = context.get(KeltaTransportContextExtractor.TENANT_SLUG_KEY);
                        if (slugValue instanceof String s) slug = s;
                    }
                    if (pat != null) RequestPatHolder.set(pat);
                    if (slug != null) RequestSlugHolder.set(slug);
                    try {
                        return inner.apply(context, request);
                    } finally {
                        RequestSlugHolder.clear();
                        RequestPatHolder.clear();
                    }
                })
                .build();
    }
}
