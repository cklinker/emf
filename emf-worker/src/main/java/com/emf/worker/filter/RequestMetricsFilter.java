package com.emf.worker.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that automatically records Prometheus-compatible request metrics
 * for all collection API calls routed through the DynamicCollectionRouter.
 *
 * <p>Records the following metrics:
 * <ul>
 *   <li>{@code emf_worker_request_total} (counter) - total requests by collection, method, status</li>
 *   <li>{@code emf_worker_request_duration_seconds} (timer/histogram) - request latency by collection</li>
 *   <li>{@code emf_worker_error_total} (counter) - errors by collection and error type</li>
 * </ul>
 *
 * <p>Only instruments requests matching the {@code /api/collections/{collectionName}} path pattern.
 * Non-collection requests (actuator, health, etc.) are passed through without instrumentation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestMetricsFilter.class);

    /**
     * Pattern to extract collection name from the request URI.
     * Matches /api/collections/{collectionName} with optional additional path segments.
     */
    private static final Pattern COLLECTION_PATH_PATTERN =
            Pattern.compile("^/api/collections/([^/]+)(?:/.*)?$");

    private final MeterRegistry meterRegistry;

    public RequestMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        Matcher matcher = COLLECTION_PATH_PATTERN.matcher(uri);

        if (!matcher.matches()) {
            // Not a collection API call — pass through without instrumentation
            filterChain.doFilter(request, response);
            return;
        }

        String collectionName = matcher.group(1);
        String method = request.getMethod();
        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // Record error metric
            recordError(collectionName, e.getClass().getSimpleName());
            throw e;
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            int statusCode = response.getStatus();
            String statusGroup = getStatusGroup(statusCode);

            // Record request count
            Counter.builder("emf_worker_request_total")
                    .description("Total requests by collection, method, and status")
                    .tag("collection", collectionName)
                    .tag("method", method)
                    .tag("status", String.valueOf(statusCode))
                    .tag("status_group", statusGroup)
                    .register(meterRegistry)
                    .increment();

            // Record request duration
            Timer.builder("emf_worker_request_duration_seconds")
                    .description("Request latency by collection")
                    .tag("collection", collectionName)
                    .tag("method", method)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            // Record error metric for 4xx/5xx responses
            if (statusCode >= 400) {
                recordError(collectionName, "HTTP_" + statusCode);
            }

            log.debug("Metrics recorded: collection={}, method={}, status={}, duration={}ms",
                    collectionName, method, statusCode,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos));
        }
    }

    /**
     * Records an error metric for a collection.
     *
     * @param collectionName the collection name
     * @param errorType the error type (exception class name or HTTP status)
     */
    private void recordError(String collectionName, String errorType) {
        Counter.builder("emf_worker_error_total")
                .description("Errors by collection and error type")
                .tag("collection", collectionName)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Returns a human-readable status group for the given HTTP status code.
     *
     * @param statusCode the HTTP status code
     * @return the status group (e.g., "2xx", "4xx", "5xx")
     */
    private String getStatusGroup(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return "2xx";
        } else if (statusCode >= 300 && statusCode < 400) {
            return "3xx";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "4xx";
        } else if (statusCode >= 500) {
            return "5xx";
        }
        return "unknown";
    }
}
