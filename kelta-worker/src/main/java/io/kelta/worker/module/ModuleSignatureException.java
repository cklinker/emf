package io.kelta.worker.module;

/**
 * Thrown when a module JAR fails signature verification — either no signature was
 * supplied while verification is enforced, the signature is malformed, or it does
 * not verify against the configured trusted publisher key.
 */
public class ModuleSignatureException extends RuntimeException {

    public ModuleSignatureException(String message) {
        super(message);
    }

    public ModuleSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
