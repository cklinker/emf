package io.kelta.worker.service.push;

/**
 * SPI for push notification providers.
 *
 * <p>Ships with {@link LogOnlyPushProvider} as the default. Users can implement
 * this interface for FCM, APNs, or any push service and register as a Spring bean.
 *
 * @since 1.0.0
 */
public interface PushProvider {
    void send(PushMessage message) throws PushDeliveryException;
}
