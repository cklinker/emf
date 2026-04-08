package io.kelta.worker.repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for push device registration and tenant push settings.
 *
 * @since 1.0.0
 */
@Repository
public class PushRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PushRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<String> findDeviceIdByToken(String tenantId, String deviceToken) {
        var results = jdbcTemplate.queryForList(
                "SELECT id FROM push_device WHERE tenant_id = ? AND device_token = ?",
                tenantId, deviceToken);
        if (results.isEmpty()) return Optional.empty();
        return Optional.of((String) results.get(0).get("id"));
    }

    public String insertDevice(String userId, String tenantId, String platform,
                               String deviceToken, String deviceName) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO push_device (id, user_id, tenant_id, platform, device_token, device_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, userId, tenantId, platform, deviceToken, deviceName);
        return id;
    }

    public void updateDevice(String deviceId, String userId, String platform, String deviceName) {
        jdbcTemplate.update(
                "UPDATE push_device SET user_id = ?, platform = ?, device_name = ?, updated_at = NOW() WHERE id = ?",
                userId, platform, deviceName, deviceId);
    }

    public void deleteDevice(String deviceId, String tenantId) {
        jdbcTemplate.update("DELETE FROM push_device WHERE id = ? AND tenant_id = ?", deviceId, tenantId);
    }

    public void deleteDeviceById(String deviceId) {
        jdbcTemplate.update("DELETE FROM push_device WHERE id = ?", deviceId);
    }

    public List<Map<String, Object>> findDevicesByUser(String userId, String tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT id, platform, device_name, created_at, updated_at FROM push_device "
                        + "WHERE user_id = ? AND tenant_id = ? ORDER BY updated_at DESC",
                userId, tenantId);
    }

    public List<Map<String, Object>> findDevicesForUser(String userId, String tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT id, device_token, platform FROM push_device WHERE user_id = ? AND tenant_id = ?",
                userId, tenantId);
    }

    public List<Map<String, Object>> findDevicesForTenant(String tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT id, device_token, platform FROM push_device WHERE tenant_id = ?", tenantId);
    }

    /**
     * Retrieves the tenant settings JSONB, or {@code null} if not found.
     */
    public JsonNode getTenantSettings(String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT settings FROM tenant WHERE id = ?", tenantId);
        if (results.isEmpty()) return null;
        Object settings = results.get(0).get("settings");
        if (settings == null) return null;
        try {
            if (settings instanceof String s) {
                return objectMapper.readTree(s);
            }
            return objectMapper.valueToTree(settings);
        } catch (Exception e) {
            return null;
        }
    }
}
