package io.kelta.worker.service.email;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Per-tenant email configuration. Sourced (in order of preference) from:
 * <ol>
 *   <li>A {@code smtp}-typed row in the credential vault (encrypted at rest), referenced
 *       by {@code tenant.email_smtp_credential_id}.</li>
 *   <li>The legacy {@code tenant.settings.email.smtp} JSONB blob (plaintext — deprecated;
 *       read-only, no new writes go here).</li>
 *   <li>Null — meaning the platform default applies.</li>
 * </ol>
 *
 * <p>From-address and From-name come from typed columns ({@code tenant.email_from_address},
 * {@code tenant.email_from_name}) and override the platform default when set.
 *
 * <p>{@code tenantId} is carried alongside so the SMTP provider can key its sender
 * cache per-tenant and invalidate cleanly on config change.
 *
 * <p>{@link #toString()} masks {@code smtpPassword} to prevent credential leakage in logs.
 *
 * @since 1.0.0
 */
public record TenantEmailSettings(
        String tenantId,
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
     * Parses tenant email settings from the legacy tenant.settings JSONB blob.
     * Prefer {@link #fromCredential(String, Map, Map, String, String)} for new credential-backed config.
     *
     * @param tenantId     the tenant id (carried for cache keying)
     * @param settingsJson the root settings JSON node from the tenant table
     * @return parsed settings, or {@code null} if no email section exists
     */
    public static TenantEmailSettings fromJsonNode(String tenantId, JsonNode settingsJson) {
        if (settingsJson == null || !settingsJson.has("email")) {
            return null;
        }

        JsonNode email = settingsJson.get("email");
        JsonNode smtp = email.has("smtp") ? email.get("smtp") : null;

        return new TenantEmailSettings(
                tenantId,
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
     * Builds tenant email settings from a credential-vault SMTP record plus optional
     * From overrides from typed tenant columns.
     *
     * <p>The SMTP type stores {@code host/port/useStartTls/useTls/fromAddress} in
     * metadata (plaintext) and {@code username/password} in the encrypted secret blob.
     *
     * @param tenantId            the tenant id (carried for cache keying)
     * @param secrets             decrypted secret fields (username, password)
     * @param metadata            plaintext credential metadata (host, port, useStartTls, ...)
     * @param fromAddressOverride tenant-level override for the From address (may be null)
     * @param fromNameOverride    tenant-level override for the From display name (may be null)
     */
    public static TenantEmailSettings fromCredential(String tenantId,
                                                     Map<String, Object> secrets,
                                                     Map<String, Object> metadata,
                                                     String fromAddressOverride,
                                                     String fromNameOverride) {
        if (metadata == null) metadata = Map.of();
        if (secrets == null)  secrets  = Map.of();

        Object hostObj   = metadata.get("host");
        Object portObj   = metadata.get("port");
        Object tlsObj    = metadata.getOrDefault("useStartTls", Boolean.TRUE);
        Object fromMeta  = metadata.get("fromAddress");

        String host = hostObj == null ? null : hostObj.toString();
        int port = 587;
        if (portObj instanceof Number n) port = n.intValue();
        else if (portObj != null) {
            try { port = Integer.parseInt(portObj.toString()); } catch (NumberFormatException ignored) {}
        }
        boolean startTls = !(tlsObj instanceof Boolean b) || b;

        String username = secrets.get("username") == null ? null : secrets.get("username").toString();
        String password = secrets.get("password") == null ? null : secrets.get("password").toString();

        String fromAddress = fromAddressOverride != null && !fromAddressOverride.isBlank()
                ? fromAddressOverride
                : (fromMeta == null ? null : fromMeta.toString());

        return new TenantEmailSettings(tenantId, host, port, username, password, startTls,
                fromAddress, fromNameOverride);
    }

    /**
     * Masks smtpPassword to prevent credential leakage in logs, error messages, and toString output.
     */
    @Override
    public String toString() {
        return "TenantEmailSettings[" +
                "tenantId=" + tenantId +
                ", smtpHost=" + smtpHost +
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
