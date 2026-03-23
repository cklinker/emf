package io.kelta.worker.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Push notification service: device management and notification routing.
 *
 * @since 1.0.0
 */
@Service
public class DefaultPushService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPushService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final Set<String> VALID_PLATFORMS = Set.of("ios", "android", "web");
    private static final int MAX_TOKEN_LENGTH = 500;

    private final PushProvider pushProvider;
    private final JdbcTemplate jdbcTemplate;

    public DefaultPushService(PushProvider pushProvider, JdbcTemplate jdbcTemplate) {
        this.pushProvider = pushProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String registerDevice(String userId, String tenantId, String platform,
                                  String deviceToken, String deviceName) {
        // Validate
        if (deviceToken == null || deviceToken.isBlank() || deviceToken.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid device token");
        }
        if (!VALID_PLATFORMS.contains(platform)) {
            throw new IllegalArgumentException("Platform must be one of: " + VALID_PLATFORMS);
        }

        // Upsert (unique on tenant_id + device_token)
        var existing = jdbcTemplate.queryForList(
                "SELECT id FROM push_device WHERE tenant_id = ? AND device_token = ?",
                tenantId, deviceToken);

        String deviceId;
        if (existing.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            jdbcTemplate.update(
                    "INSERT INTO push_device (id, user_id, tenant_id, platform, device_token, device_name, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    deviceId, userId, tenantId, platform, deviceToken, deviceName);
        } else {
            deviceId = (String) existing.get(0).get("id");
            jdbcTemplate.update(
                    "UPDATE push_device SET user_id = ?, platform = ?, device_name = ?, updated_at = NOW() WHERE id = ?",
                    userId, platform, deviceName, deviceId);
        }

        log.info("Device registered: id={} user={} platform={}", deviceId, userId, platform);
        return deviceId;
    }

    public void removeDevice(String deviceId, String tenantId) {
        jdbcTemplate.update("DELETE FROM push_device WHERE id = ? AND tenant_id = ?", deviceId, tenantId);
    }

    public List<Map<String, Object>> listDevices(String userId, String tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT id, platform, device_name, created_at, updated_at FROM push_device " +
                        "WHERE user_id = ? AND tenant_id = ? ORDER BY updated_at DESC",
                userId, tenantId);
    }

    public int sendToUser(String userId, String tenantId, String title, String body, Map<String, String> data) {
        var devices = jdbcTemplate.queryForList(
                "SELECT id, device_token, platform FROM push_device WHERE user_id = ? AND tenant_id = ?",
                userId, tenantId);
        return sendToDevices(devices, title, body, data);
    }

    public int sendToTenant(String tenantId, String title, String body, Map<String, String> data) {
        var devices = jdbcTemplate.queryForList(
                "SELECT id, device_token, platform FROM push_device WHERE tenant_id = ?", tenantId);

        securityLog.info("security_event=PUSH_BROADCAST tenant={} devices={} title={}",
                tenantId, devices.size(), title);
        return sendToDevices(devices, title, body, data);
    }

    private int sendToDevices(List<Map<String, Object>> devices, String title, String body,
                               Map<String, String> data) {
        int delivered = 0;
        for (var device : devices) {
            String token = (String) device.get("device_token");
            String platform = (String) device.get("platform");
            String deviceId = (String) device.get("id");

            try {
                pushProvider.send(new PushMessage(token, platform, title, body, data));
                delivered++;
            } catch (PushDeliveryException e) {
                if (e.isInvalidToken()) {
                    log.info("Removing stale device token: id={}", deviceId);
                    jdbcTemplate.update("DELETE FROM push_device WHERE id = ?", deviceId);
                } else {
                    log.warn("Push delivery failed for device {}: {}", deviceId, e.getMessage());
                }
            }
        }
        return delivered;
    }
}
