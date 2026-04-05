package io.kelta.worker.service.push;

import tools.jackson.databind.JsonNode;

/**
 * Per-tenant push notification configuration extracted from the tenant.settings JSONB column.
 *
 * <p>Tenants can override the platform FCM defaults by storing push settings
 * in their tenant.settings JSON under the {@code "push"} key:
 * <pre>{@code
 * {
 *   "push": {
 *     "fcm": {
 *       "projectId": "my-project",
 *       "clientEmail": "firebase@my-project.iam.gserviceaccount.com",
 *       "privateKey": "-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Any field left unset falls back to platform defaults. The {@code privateKey}
 * is deliberately excluded from {@link #toString()} to prevent credential leakage in logs.
 *
 * @since 1.0.0
 */
public record TenantPushSettings(
        String fcmProjectId,
        String fcmClientEmail,
        String fcmPrivateKey
) {

    /**
     * @return true if this settings object contains FCM overrides
     */
    public boolean hasFcmOverride() {
        return fcmProjectId != null && !fcmProjectId.isBlank()
                && fcmClientEmail != null && !fcmClientEmail.isBlank()
                && fcmPrivateKey != null && !fcmPrivateKey.isBlank();
    }

    /**
     * Parses tenant push settings from the tenant.settings JSONB.
     *
     * @param settingsJson the root settings JSON node from the tenant table
     * @return parsed settings, or {@code null} if no push section exists
     */
    public static TenantPushSettings fromJsonNode(JsonNode settingsJson) {
        if (settingsJson == null || !settingsJson.has("push")) {
            return null;
        }

        JsonNode push = settingsJson.get("push");
        JsonNode fcm = push.has("fcm") ? push.get("fcm") : null;

        if (fcm == null) {
            return null;
        }

        return new TenantPushSettings(
                textOrNull(fcm, "projectId"),
                textOrNull(fcm, "clientEmail"),
                textOrNull(fcm, "privateKey")
        );
    }

    /**
     * Masks privateKey to prevent credential leakage in logs, error messages, and toString output.
     */
    @Override
    public String toString() {
        return "TenantPushSettings[" +
                "fcmProjectId=" + fcmProjectId +
                ", fcmClientEmail=" + fcmClientEmail +
                ", fcmPrivateKey=****" +
                ']';
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
