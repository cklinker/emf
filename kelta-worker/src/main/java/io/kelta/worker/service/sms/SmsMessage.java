package io.kelta.worker.service.sms;

/**
 * SMS message to be sent via {@link SmsProvider}.
 *
 * @param to   recipient phone number in E.164 format (e.g., +14155551234)
 * @param body message text
 * @since 1.0.0
 */
public record SmsMessage(String to, String body) {}
