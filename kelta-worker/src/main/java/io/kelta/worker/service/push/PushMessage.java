package io.kelta.worker.service.push;

import java.util.Map;

/**
 * @param deviceToken device push token
 * @param platform    ios, android, or web
 * @param title       notification title
 * @param body        notification body
 * @param data        optional key-value payload
 * @since 1.0.0
 */
public record PushMessage(
        String deviceToken,
        String platform,
        String title,
        String body,
        Map<String, String> data
) {}
