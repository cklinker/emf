package io.kelta.worker.service.push;

public class PushDeliveryException extends RuntimeException {
    private final boolean invalidToken;

    public PushDeliveryException(String message, boolean invalidToken) {
        super(message);
        this.invalidToken = invalidToken;
    }

    public boolean isInvalidToken() { return invalidToken; }
}
