package io.kelta.worker.service.push;

import tools.jackson.databind.JsonNode;

/**
 * Per-tenant push notification configuration extracted from the tenant.settings JSONB column.
 *
 * <p>Tenants can override the platform FCM and/or APNs defaults by storing push settings
 * in their tenant.settings JSON under the {@code "push"} key:
 * <pre>{@code
 * {
 *   "push": {
 *     "fcm": {
 *       "projectId": "my-project",
 *       "clientEmail": "firebase@my-project.iam.gserviceaccount.com",
 *       "privateKey": "-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----"
 *     },
 *     "apns": {
 *       "teamId": "TEAM123456",
 *       "keyId": "KEY7890AB",
 *       "bundleId": "io.tenant.app",
 *       "authKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Any field left unset falls back to platform defaults. Private keys are deliberately
 * excluded from {@link #toString()} to prevent credential leakage in logs.
 *
 * @since 1.0.0
 */
public record TenantPushSettings(
        String fcmProjectId,
        String fcmClientEmail,
        String fcmPrivateKey,
        String apnsTeamId,
        String apnsKeyId,
        String apnsBundleId,
        String apnsAuthKey
) {

    /** Back-compat convenience constructor for FCM-only settings. */
    public TenantPushSettings(String fcmProjectId, String fcmClientEmail, String fcmPrivateKey) {
        this(fcmProjectId, fcmClientEmail, fcmPrivateKey, null, null, null, null);
    }

    /**
     * @return true if this settings object contains a complete FCM override
     */
    public boolean hasFcmOverride() {
        return notBlank(fcmProjectId) && notBlank(fcmClientEmail) && notBlank(fcmPrivateKey);
    }

    /**
     * @return true if this settings object contains a complete APNs override
     */
    public boolean hasApnsOverride() {
        return notBlank(apnsTeamId) && notBlank(apnsKeyId)
                && notBlank(apnsBundleId) && notBlank(apnsAuthKey);
    }

    /**
     * Parses tenant push settings from the tenant.settings JSONB.
     *
     * @param settingsJson the root settings JSON node from the tenant table
     * @return parsed settings, or {@code null} if no push overrides exist
     */
    public static TenantPushSettings fromJsonNode(JsonNode settingsJson) {
        if (settingsJson == null || !settingsJson.has("push")) {
            return null;
        }

        JsonNode push = settingsJson.get("push");
        JsonNode fcm = push.has("fcm") ? push.get("fcm") : null;
        JsonNode apns = push.has("apns") ? push.get("apns") : null;

        if (fcm == null && apns == null) {
            return null;
        }

        return new TenantPushSettings(
                fcm != null ? textOrNull(fcm, "projectId") : null,
                fcm != null ? textOrNull(fcm, "clientEmail") : null,
                fcm != null ? textOrNull(fcm, "privateKey") : null,
                apns != null ? textOrNull(apns, "teamId") : null,
                apns != null ? textOrNull(apns, "keyId") : null,
                apns != null ? textOrNull(apns, "bundleId") : null,
                apns != null ? textOrNull(apns, "authKey") : null
        );
    }

    /**
     * Masks private keys to prevent credential leakage in logs, error messages, and toString.
     */
    @Override
    public String toString() {
        return "TenantPushSettings[" +
                "fcmProjectId=" + fcmProjectId +
                ", fcmClientEmail=" + fcmClientEmail +
                ", fcmPrivateKey=****" +
                ", apnsTeamId=" + apnsTeamId +
                ", apnsKeyId=" + apnsKeyId +
                ", apnsBundleId=" + apnsBundleId +
                ", apnsAuthKey=****" +
                ']';
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
