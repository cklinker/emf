package io.kelta.worker.service.billing;

/**
 * A non-2xx response from the payment processor.
 *
 * <p>Carries the processor's own {@code type}/{@code code} so callers can branch
 * (e.g. a card decline vs a bad key) without re-parsing the body. The message is
 * for logs and support, <b>not</b> for returning to an end user verbatim — it can
 * name account internals.
 */
public class StripeApiException extends RuntimeException {

    private final int status;
    private final String errorType;
    private final String errorCode;

    public StripeApiException(int status, String errorType, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /** True when the processor rejected our credentials rather than the request. */
    public boolean isAuthFailure() {
        return status == 401;
    }
}
