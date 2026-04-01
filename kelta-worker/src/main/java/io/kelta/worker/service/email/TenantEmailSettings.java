package io.kelta.worker.service.email;

import tools.jackson.databind.JsonNode;

/**
 * Per-tenant email configuration extracted from the tenant.settings JSONB column.
 *
 * <p>Tenants can override the platform SMTP defaults by storing email settings
 * in their tenant.settings JSON under the {@code "email"} key:
 * <pre>{@code
 * {
 *   "email": {
 *     "smtp": {
 *       "host": "smtp.tenant.com",
 *       "port": 587,
 *       "username": "user",
 *       "password": "secret",
 *       "startTls": true
 *     },
 *     "fromAddress": "no-reply@tenant.com",
 *     "fromName": "Tenant Co"
 *   }
 * }
 * }</pre>
 *
 * <p>Any field left unset falls back to platform defaults. The {@code smtpPassword}
 * is deliberately excluded from {@link #toString()} to prevent credential leakage in logs.
 *
 * @since 1.0.0
 */
public record TenantEmailSettings(
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        boolean smtpStartTls,
        String fromAddress,
        String fromName
) {

    /**
     * @return true if this settings object contains SMTP server overrides
     */
    public boolean hasSmtpOverride() {
        return smtpHost != null && !smtpHost.isBlank();
    }

    /**
     * @return true if this settings object contains a from-address override
     */
    public boolean hasFromOverride() {
        return fromAddress != null && !fromAddress.isBlank();
    }

    /**
     * Parses tenant email settings from the tenant.settings JSONB.
     *
     * @param settingsJson the root settings JSON node from the tenant table
     * @return parsed settings, or {@code null} if no email section exists
     */
    public static TenantEmailSettings fromJsonNode(JsonNode settingsJson) {
        if (settingsJson == null || !settingsJson.has("email")) {
            return null;
        }

        JsonNode email = settingsJson.get("email");
        JsonNode smtp = email.has("smtp") ? email.get("smtp") : null;

        return new TenantEmailSettings(
                smtp != null ? textOrNull(smtp, "host") : null,
                smtp != null ? intOrDefault(smtp, "port", 587) : 587,
                smtp != null ? textOrNull(smtp, "username") : null,
                smtp != null ? textOrNull(smtp, "password") : null,
                smtp != null && boolOrDefault(smtp, "startTls", true),
                textOrNull(email, "fromAddress"),
                textOrNull(email, "fromName")
        );
    }

    /**
     * Masks smtpPassword to prevent credential leakage in logs, error messages, and toString output.
     */
    @Override
    public String toString() {
        return "TenantEmailSettings[" +
                "smtpHost=" + smtpHost +
                ", smtpPort=" + smtpPort +
                ", smtpUsername=" + smtpUsername +
                ", smtpPassword=****" +
                ", smtpStartTls=" + smtpStartTls +
                ", fromAddress=" + fromAddress +
                ", fromName=" + fromName +
                ']';
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private static int intOrDefault(JsonNode node, String field, int defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt(defaultValue) : defaultValue;
    }

    private static boolean boolOrDefault(JsonNode node, String field, boolean defaultValue) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean(defaultValue) : defaultValue;
    }
}
