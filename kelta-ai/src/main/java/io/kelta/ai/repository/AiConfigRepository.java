package io.kelta.ai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class AiConfigRepository {

    private final JdbcTemplate jdbc;

    public AiConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> getConfig(long tenantId, String key) {
        var results = jdbc.queryForList(
                "SELECT config_value FROM ai_config WHERE tenant_id = ? AND config_key = ?",
                String.class, tenantId, key);
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.getFirst());
    }

    public Map<String, String> getAllConfig(long tenantId) {
        Map<String, String> config = new LinkedHashMap<>();
        jdbc.query("SELECT config_key, config_value FROM ai_config WHERE tenant_id = ?",
                (rs, rowNum) -> {
                    config.put(rs.getString("config_key"), rs.getString("config_value"));
                    return null;
                }, tenantId);
        return config;
    }

    public void setConfig(long tenantId, String key, String value) {
        jdbc.update("""
                INSERT INTO ai_config (id, tenant_id, config_key, config_value, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, NOW())
                ON CONFLICT (tenant_id, config_key) DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = NOW()
                """, tenantId, key, value);
    }

    public void deleteConfig(long tenantId, String key) {
        jdbc.update("DELETE FROM ai_config WHERE tenant_id = ? AND config_key = ?", tenantId, key);
    }
}
