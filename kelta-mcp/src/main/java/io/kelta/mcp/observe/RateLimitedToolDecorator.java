package io.kelta.mcp.observe;

import io.kelta.mcp.auth.RequestPatHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outer decorator that gates each tool call through the per-PAT
 * token bucket. Rate-limited calls return an MCP error result without
 * invoking the wrapped handler — so they don't show up in the regular
 * mcp.tool.calls counter or the duration histogram, only in a
 * dedicated mcp.tool.rate_limited counter.
 *
 * <p>This sits OUTSIDE the {@link ObservedToolDecorator} in the
 * registration chain: rate-limited calls are not "tool executions",
 * just rejected requests.
 */
@Component
public class RateLimitedToolDecorator {

    private static final String RATE_LIMITED_COUNTER = "mcp.tool.rate_limited";

    private final RateLimiter limiter;
    private final MeterRegistry registry;

    public RateLimitedToolDecorator(RateLimiter limiter, MeterRegistry registry) {
        this.limiter = limiter;
        this.registry = registry;
    }

    public SyncToolSpecification decorate(SyncToolSpecification original, String profile) {
        String toolName = original.tool().name();
        var inner = original.callHandler();

        return SyncToolSpecification.builder()
                .tool(original.tool())
                .callHandler((context, request) -> {
                    String pat = RequestPatHolder.get();
                    String bucketKey = pat == null ? "anonymous" : pat;
                    if (!limiter.tryAcquire(bucketKey)) {
                        Counter.builder(RATE_LIMITED_COUNTER)
                                .tag("tool", toolName)
                                .tag("profile", profile)
                                .register(registry)
                                .increment();
                        return CallToolResult.builder()
                                .isError(true)
                                .content(List.of(new TextContent(
                                        "Rate limit exceeded for tool \"" + toolName + "\". "
                                        + "Slow down and retry shortly.")))
                                .build();
                    }
                    return inner.apply(context, request);
                })
                .build();
    }
}
