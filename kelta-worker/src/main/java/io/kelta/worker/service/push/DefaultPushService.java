package io.kelta.worker.service.push;

import io.kelta.worker.repository.PushRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Push notification service: device management and notification routing.
 *
 * <p>Loads per-tenant push settings from the tenant.settings JSONB column
 * and passes them to the active {@link PushProvider} for credential resolution.
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
    private final PushRepository pushRepository;

    public DefaultPushService(PushProvider pushProvider, PushRepository pushRepository) {
        this.pushProvider = pushProvider;
        this.pushRepository = pushRepository;
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
        var existingId = pushRepository.findDeviceIdByToken(tenantId, deviceToken);

        String deviceId;
        if (existingId.isEmpty()) {
            deviceId = pushRepository.insertDevice(userId, tenantId, platform, deviceToken, deviceName);
        } else {
            deviceId = existingId.get();
            pushRepository.updateDevice(deviceId, userId, platform, deviceName);
        }

        log.info("Device registered: id={} user={} platform={}", deviceId, userId, platform);
        return deviceId;
    }

    public void removeDevice(String deviceId, String tenantId) {
        pushRepository.deleteDevice(deviceId, tenantId);
    }

    public List<Map<String, Object>> listDevices(String userId, String tenantId) {
        return pushRepository.findDevicesByUser(userId, tenantId);
    }

    public int sendToUser(String userId, String tenantId, String title, String body, Map<String, String> data) {
        var devices = pushRepository.findDevicesForUser(userId, tenantId);
        TenantPushSettings tenantSettings = loadTenantPushSettings(tenantId);
        return sendToDevices(devices, title, body, data, tenantSettings);
    }

    public int sendToTenant(String tenantId, String title, String body, Map<String, String> data) {
        var devices = pushRepository.findDevicesForTenant(tenantId);

        securityLog.info("security_event=PUSH_BROADCAST tenant={} devices={} title={}",
                tenantId, devices.size(), title);
        TenantPushSettings tenantSettings = loadTenantPushSettings(tenantId);
        return sendToDevices(devices, title, body, data, tenantSettings);
    }

    private int sendToDevices(List<Map<String, Object>> devices, String title, String body,
                               Map<String, String> data, TenantPushSettings tenantSettings) {
        int delivered = 0;
        for (var device : devices) {
            String token = (String) device.get("device_token");
            String platform = (String) device.get("platform");
            String deviceId = (String) device.get("id");

            try {
                pushProvider.send(new PushMessage(token, platform, title, body, data), tenantSettings);
                delivered++;
            } catch (PushDeliveryException e) {
                if (e.isInvalidToken()) {
                    log.info("Removing stale device token: id={}", deviceId);
                    pushRepository.deleteDeviceById(deviceId);
                } else {
                    log.warn("Push delivery failed for device {}: {}", deviceId, e.getMessage());
                }
            }
        }
        return delivered;
    }

    private TenantPushSettings loadTenantPushSettings(String tenantId) {
        try {
            var settings = pushRepository.getTenantSettings(tenantId);
            return settings != null ? TenantPushSettings.fromJsonNode(settings) : null;
        } catch (Exception e) {
            log.debug("Could not load tenant push settings for {}: {}", tenantId, e.getMessage());
            return null;
        }
    }
}
