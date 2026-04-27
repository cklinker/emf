package io.kelta.mcp.observe;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Wraps a {@link SyncToolSpecification} so every tool call emits a
 * {@code mcp_tool_call_duration_seconds} histogram and a
 * {@code mcp_tool_calls_total} counter — both tagged with
 * {@code tool}, {@code profile}, and {@code status}
 * ({@code success | error | exception}).
 *
 * <p>Also pushes {@code mcpTool} and {@code mcpProfile} into MDC for
 * the duration of the call so structured logs and any spans the OTel
 * agent emits during the call carry the same identifier.
 */
@Component
public class ObservedToolDecorator {

    private static final String DURATION_METRIC = "mcp.tool.call.duration";
    private static final String COUNTER_METRIC = "mcp.tool.calls";

    private final MeterRegistry registry;

    public ObservedToolDecorator(MeterRegistry registry) {
        this.registry = registry;
    }

    public SyncToolSpecification decorate(SyncToolSpecification original, String profile) {
        String toolName = original.tool().name();
        var originalHandler = original.callHandler();

        return SyncToolSpecification.builder()
                .tool(original.tool())
                .callHandler((exchange, request) -> {
                    long startNs = System.nanoTime();
                    String status = "success";
                    MDC.put("mcpTool", toolName);
                    MDC.put("mcpProfile", profile);
                    try {
                        CallToolResult result = originalHandler.apply(exchange, request);
                        if (Boolean.TRUE.equals(result.isError())) {
                            status = "error";
                        }
                        return result;
                    } catch (RuntimeException e) {
                        status = "exception";
                        throw e;
                    } finally {
                        Tags tags = Tags.of(
                                "tool", toolName,
                                "profile", profile,
                                "status", status);
                        Timer.builder(DURATION_METRIC)
                                .tags(tags)
                                .publishPercentileHistogram()
                                .register(registry)
                                .record(Duration.ofNanos(System.nanoTime() - startNs));
                        registry.counter(COUNTER_METRIC, tags).increment();
                        MDC.remove("mcpTool");
                        MDC.remove("mcpProfile");
                    }
                })
                .build();
    }
}
