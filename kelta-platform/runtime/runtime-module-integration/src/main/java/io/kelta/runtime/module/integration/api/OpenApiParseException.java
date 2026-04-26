package io.kelta.runtime.module.integration.api;

/**
 * Thrown by {@link OpenApiSpecParser} when the supplied document is not a
 * valid OpenAPI 3.x specification. The message is safe to surface in API
 * responses — it contains parser diagnostics, never raw secrets.
 */
public class OpenApiParseException extends RuntimeException {

    public OpenApiParseException(String message) {
        super(message);
    }

    public OpenApiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
