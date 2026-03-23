package io.kelta.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for password policy CRUD operations.
 *
 * @since 1.0.0
 */
@Repository
public class PasswordPolicyRepository {

    private final JdbcTemplate jdbcTemplate;

    public PasswordPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Map<String, Object>> findByTenantId(String tenantId) {
        var results = jdbcTemplate.queryForList(
                "SELECT * FROM password_policy WHERE tenant_id = ?", tenantId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void upsert(String tenantId, Map<String, Object> policy) {
        var existing = findByTenantId(tenantId);
        if (existing.isPresent()) {
            jdbcTemplate.update(
                    "UPDATE password_policy SET min_length = ?, max_length = ?, require_uppercase = ?, " +
                            "require_lowercase = ?, require_digit = ?, require_special = ?, history_count = ?, " +
                            "dictionary_check = ?, personal_data_check = ?, lockout_threshold = ?, " +
                            "lockout_duration_minutes = ?, max_age_days = ?, updated_at = ? " +
                            "WHERE tenant_id = ?",
                    policy.get("minLength"), policy.get("maxLength"),
                    policy.get("requireUppercase"), policy.get("requireLowercase"),
                    policy.get("requireDigit"), policy.get("requireSpecial"),
                    policy.get("historyCount"), policy.get("dictionaryCheck"),
                    policy.get("personalDataCheck"), policy.get("lockoutThreshold"),
                    policy.get("lockoutDurationMinutes"), policy.get("maxAgeDays"),
                    Timestamp.from(Instant.now()), tenantId
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO password_policy (id, tenant_id, min_length, max_length, require_uppercase, " +
                            "require_lowercase, require_digit, require_special, history_count, dictionary_check, " +
                            "personal_data_check, lockout_threshold, lockout_duration_minutes, max_age_days) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), tenantId,
                    policy.get("minLength"), policy.get("maxLength"),
                    policy.get("requireUppercase"), policy.get("requireLowercase"),
                    policy.get("requireDigit"), policy.get("requireSpecial"),
                    policy.get("historyCount"), policy.get("dictionaryCheck"),
                    policy.get("personalDataCheck"), policy.get("lockoutThreshold"),
                    policy.get("lockoutDurationMinutes"), policy.get("maxAgeDays")
            );
        }
    }
}
