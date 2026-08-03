package io.kelta.worker.service.push;

/**
 * SPI for push notification providers.
 *
 * <p>Ships with {@link LogOnlyPushProvider} as the default. Users can implement
 * this interface for FCM, APNs, or any push service and register as a Spring bean.
 *
 * <p>Implementations receive tenant-specific settings that may override platform defaults
 * for FCM credentials and project configuration.
 *
 * @since 1.0.0
 */
public interface PushProvider {

    /**
     * Sends a push notification using the resolved provider configuration.
     *
     * @param message        the push content (device token, platform, title, body, data)
     * @param tenantSettings tenant-specific push settings, or {@code null} to use platform defaults
     * @throws PushDeliveryException if delivery fails
     */
    void send(PushMessage message, TenantPushSettings tenantSettings) throws PushDeliveryException;

    /**
     * Sends a push notification using platform defaults only.
     *
     * @param message the push content
     * @throws PushDeliveryException if delivery fails
     */
    default void send(PushMessage message) throws PushDeliveryException {
        send(message, null);
    }

    /**
     * Whether this provider handles the given device platform
     * ({@code ios}, {@code android}, {@code web}).
     *
     * <p>Defaults to true so existing single-provider deployments are unchanged:
     * before browser push there was exactly one provider and it took everything.
     * {@link WebPushProvider} narrows to {@code web} so it can run alongside the
     * property-selected mobile provider rather than instead of it.
     */
    default boolean supports(String platform) {
        return true;
    }
}
