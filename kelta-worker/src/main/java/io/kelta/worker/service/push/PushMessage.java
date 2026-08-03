package io.kelta.worker.service.push;

import java.util.Map;

/**
 * @param deviceToken device push token
 * @param platform    ios, android, or web
 * @param title       notification title
 * @param body        notification body
 * @param data         optional key-value payload
 * @param subscription raw Web Push subscription JSON ({@code endpoint}, {@code keys.p256dh},
 *                     {@code keys.auth}); null for native platforms. Web Push cannot be
 *                     addressed by token alone — {@code deviceToken} is only a hash of the
 *                     endpoint, kept so the unique constraint and 500-char column still fit.
 * @since 1.0.0
 */
public record PushMessage(
        String deviceToken,
        String platform,
        String title,
        String body,
        Map<String, String> data,
        String subscription
) {

    /** Native-platform message: no Web Push subscription. */
    public PushMessage(String deviceToken, String platform, String title, String body,
                       Map<String, String> data) {
        this(deviceToken, platform, title, body, data, null);
    }
}
