package io.kelta.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * Helpers for safely mutating gateway response status and headers.
 *
 * <p>If an upstream filter has already committed the response, the headers
 * become {@code ReadOnlyHttpHeaders} and any mutation throws
 * {@link UnsupportedOperationException}. That surfaces as a 500 instead of
 * the intended 4xx. These helpers short-circuit when the response is already
 * finalized so the chain completes cleanly.
 */
public final class ResponseHelpers {

    private static final Logger log = LoggerFactory.getLogger(ResponseHelpers.class);

    private ResponseHelpers() {}

    /**
     * Sets status and {@code application/json} content type on the response.
     * Returns {@code true} if the caller should proceed to write the body,
     * {@code false} if the response was already finalized upstream and the
     * caller should abandon the write (return {@code Mono.empty()}).
     */
    public static boolean prepareJsonResponse(ServerHttpResponse response, HttpStatus status) {
        if (response.isCommitted()) {
            log.debug("Response already committed; skipping JSON error body");
            return false;
        }
        try {
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return true;
        } catch (UnsupportedOperationException e) {
            log.debug("Response headers read-only; skipping JSON error body: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Best-effort header mutation: applies the action only if the response is
     * still writable. Swallows {@link UnsupportedOperationException} so a
     * read-only headers race does not break the chain.
     */
    public static void applyHeaderIfWritable(ServerHttpResponse response, Runnable mutation) {
        if (response.isCommitted()) {
            return;
        }
        try {
            mutation.run();
        } catch (UnsupportedOperationException e) {
            log.debug("Skipped header mutation — response already finalized: {}", e.getMessage());
        }
    }
}
