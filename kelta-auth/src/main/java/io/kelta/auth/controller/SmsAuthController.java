package io.kelta.auth.controller;

import io.kelta.auth.service.SmsVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SMS authentication endpoints for OTP-based verification.
 *
 * <p>Supports SMS as an MFA factor (alternative to TOTP).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/auth/sms")
public class SmsAuthController {

    private static final Logger log = LoggerFactory.getLogger(SmsAuthController.class);

    private final SmsVerificationService smsService;

    public SmsAuthController(SmsVerificationService smsService) {
        this.smsService = smsService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, String> body,
                                       @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        String phoneNumber = body.get("phoneNumber");
        String effectiveTenantId = body.getOrDefault("tenantId", tenantId);

        if (phoneNumber == null || effectiveTenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber and tenantId required"));
        }

        // Always return 200 to prevent phone enumeration
        smsService.sendCode(phoneNumber, effectiveTenantId);
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body,
                                         @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        String phoneNumber = body.get("phoneNumber");
        String code = body.get("code");
        String effectiveTenantId = body.getOrDefault("tenantId", tenantId);

        if (phoneNumber == null || code == null || effectiveTenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber, code, and tenantId required"));
        }

        boolean verified = smsService.verifyCode(phoneNumber, code, effectiveTenantId);
        if (verified) {
            return ResponseEntity.ok(Map.of("status", "verified"));
        }
        return ResponseEntity.ok(Map.of("status", "failed", "error", "Invalid or expired code"));
    }
}
