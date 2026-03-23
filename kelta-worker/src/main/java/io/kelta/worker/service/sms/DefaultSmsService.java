package io.kelta.worker.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SMS verification service: OTP generation, rate limiting, and code verification.
 *
 * <p>Codes are SHA-256 hashed before storage. Comparison uses {@code MessageDigest.isEqual()}
 * for constant-time verification (prevents timing attacks).
 *
 * @since 1.0.0
 */
@Service
public class DefaultSmsService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSmsService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 5;
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final SmsProvider smsProvider;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom;

    public DefaultSmsService(SmsProvider smsProvider, JdbcTemplate jdbcTemplate) {
        this.smsProvider = smsProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Sends a verification code to the given phone number.
     *
     * @return true if sent, false if rate limited or invalid
     */
    public boolean sendVerificationCode(String phoneNumber, String tenantId) {
        if (!isValidE164(phoneNumber)) {
            log.warn("Invalid phone number format: {}", maskPhone(phoneNumber));
            return false;
        }

        // Rate limit check
        if (isRateLimited(phoneNumber, tenantId)) {
            securityLog.warn("security_event=SMS_RATE_LIMITED phone={} tenant={}", maskPhone(phoneNumber), tenantId);
            return false;
        }

        // Clean up expired/used codes for this phone
        jdbcTemplate.update(
                "DELETE FROM sms_verification WHERE phone = ? AND tenant_id = ? AND (expires_at < NOW() OR used = true)",
                phoneNumber, tenantId);

        // Generate code
        String code = generateCode();
        String codeHash = sha256(code);
        Instant expiresAt = Instant.now().plus(CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        // Store hashed code
        jdbcTemplate.update(
                "INSERT INTO sms_verification (id, phone, code_hash, tenant_id, expires_at, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID().toString(), phoneNumber, codeHash, tenantId, Timestamp.from(expiresAt));

        // Send via provider
        try {
            smsProvider.send(new SmsMessage(phoneNumber, "Your Kelta verification code is: " + code));
            securityLog.info("security_event=SMS_SENT phone={} tenant={}", maskPhone(phoneNumber), tenantId);
            return true;
        } catch (SmsDeliveryException e) {
            log.error("Failed to send SMS to {}: {}", maskPhone(phoneNumber), e.getMessage());
            securityLog.warn("security_event=SMS_SEND_FAILED phone={} tenant={}", maskPhone(phoneNumber), tenantId);
            return false;
        }
    }

    /**
     * Verifies a code against the most recent unexpired verification.
     *
     * @return true if code matches, false otherwise
     */
    public boolean verifyCode(String phoneNumber, String code, String tenantId) {
        if (code == null || code.length() != CODE_LENGTH) return false;

        var results = jdbcTemplate.queryForList(
                "SELECT id, code_hash, attempts FROM sms_verification " +
                        "WHERE phone = ? AND tenant_id = ? AND expires_at > NOW() AND used = false " +
                        "ORDER BY created_at DESC LIMIT 1",
                phoneNumber, tenantId);

        if (results.isEmpty()) {
            securityLog.warn("security_event=SMS_VERIFY_FAILED phone={} tenant={} detail=no_active_code",
                    maskPhone(phoneNumber), tenantId);
            return false;
        }

        Map<String, Object> row = results.get(0);
        String storedHash = (String) row.get("code_hash");
        int attempts = ((Number) row.get("attempts")).intValue();
        String verificationId = (String) row.get("id");

        if (attempts >= MAX_VERIFY_ATTEMPTS) {
            securityLog.warn("security_event=SMS_VERIFY_FAILED phone={} tenant={} detail=max_attempts",
                    maskPhone(phoneNumber), tenantId);
            return false;
        }

        // Constant-time comparison
        byte[] submittedHash = sha256Bytes(code);
        byte[] expectedHash = HexFormat.of().parseHex(storedHash);
        boolean match = MessageDigest.isEqual(submittedHash, expectedHash);

        if (match) {
            jdbcTemplate.update("UPDATE sms_verification SET used = true WHERE id = ?", verificationId);
            securityLog.info("security_event=SMS_VERIFIED phone={} tenant={}", maskPhone(phoneNumber), tenantId);
            return true;
        }

        // Increment attempts
        jdbcTemplate.update("UPDATE sms_verification SET attempts = attempts + 1 WHERE id = ?", verificationId);
        securityLog.warn("security_event=SMS_VERIFY_FAILED phone={} tenant={} detail=wrong_code attempts={}",
                maskPhone(phoneNumber), tenantId, attempts + 1);
        return false;
    }

    private boolean isRateLimited(String phoneNumber, String tenantId) {
        Instant windowStart = Instant.now().minus(CODE_EXPIRY_MINUTES, ChronoUnit.MINUTES);
        var result = jdbcTemplate.queryForList(
                "SELECT COUNT(*) AS cnt FROM sms_verification WHERE phone = ? AND tenant_id = ? AND created_at > ?",
                phoneNumber, tenantId, Timestamp.from(windowStart));
        if (result.isEmpty()) return false;
        return ((Number) result.get(0).get("cnt")).intValue() >= MAX_SENDS_PER_WINDOW;
    }

    String generateCode() {
        int code = secureRandom.nextInt(900000) + 100000; // 100000-999999
        return String.valueOf(code);
    }

    static boolean isValidE164(String phone) {
        return phone != null && E164_PATTERN.matcher(phone).matches();
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "***" + phone.substring(phone.length() - 4);
    }

    private String sha256(String input) {
        return HexFormat.of().formatHex(sha256Bytes(input));
    }

    private byte[] sha256Bytes(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
