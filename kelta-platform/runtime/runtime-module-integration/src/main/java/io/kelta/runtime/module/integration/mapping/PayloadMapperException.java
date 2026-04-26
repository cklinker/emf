package io.kelta.runtime.module.integration.mapping;

/**
 * Thrown when a payload mapping cannot be resolved — bad JSONata, type
 * mismatch, or unexpected template shape. Surfaces in flow execution as a
 * named error code (e.g., {@code Mapper.Failure}) so users can write Catch
 * policies against it.
 */
public class PayloadMapperException extends RuntimeException {

    public PayloadMapperException(String message) {
        super(message);
    }

    public PayloadMapperException(String message, Throwable cause) {
        super(message, cause);
    }
}
