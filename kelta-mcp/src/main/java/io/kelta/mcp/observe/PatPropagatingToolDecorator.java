package io.kelta.mcp.observe;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import io.kelta.mcp.auth.RequestPatHolder;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.springframework.stereotype.Component;

/**
 * Outermost decorator: copies the PAT from the SDK's transport context
 * (set by {@link KeltaTransportContextExtractor} during HTTP intake)
 * into {@link RequestPatHolder} for the duration of the tool handler.
 *
 * <p>Why this exists: the MCP SDK dispatches sync tool handlers on
 * Reactor scheduler threads, not the servlet thread. The
 * {@code ThreadLocal} that the auth filter populates on the inbound
 * thread is therefore invisible to the handler. The transport context
 * is the SDK-blessed mechanism for thread-safe propagation; this
 * decorator bridges it back to the same {@code RequestPatHolder} the
 * downstream HTTP client already reads from.
 *
 * <p>Wraps OUTSIDE the rate limiter (which keys its bucket on the PAT)
 * and the observation decorator.
 */
@Component
public class PatPropagatingToolDecorator {

    public SyncToolSpecification decorate(SyncToolSpecification original) {
        var inner = original.callHandler();
        return SyncToolSpecification.builder()
                .tool(original.tool())
                .callHandler((exchange, request) -> {
                    String pat = null;
                    if (exchange != null && exchange.transportContext() != null) {
                        Object value = exchange.transportContext().get(KeltaTransportContextExtractor.PAT_KEY);
                        if (value instanceof String s) pat = s;
                    }
                    if (pat != null) {
                        RequestPatHolder.set(pat);
                    }
                    try {
                        return inner.apply(exchange, request);
                    } finally {
                        RequestPatHolder.clear();
                    }
                })
                .build();
    }
}
