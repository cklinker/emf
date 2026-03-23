package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.worker.service.sms.DefaultSmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal endpoint for SMS operations, called by the auth service.
 * Protected by internal token (same pattern as InternalEmailController).
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/internal/sms")
public class InternalSmsController {

    private final DefaultSmsService smsService;

    public InternalSmsController(DefaultSmsService smsService) {
        this.smsService = smsService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, String> body) {
        String phoneNumber = body.get("phoneNumber");
        String tenantId = body.getOrDefault("tenantId", TenantContext.get());

        if (phoneNumber == null || tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber and tenantId required"));
        }

        boolean sent = smsService.sendVerificationCode(phoneNumber, tenantId);
        if (!sent) {
            return ResponseEntity.status(429).body(Map.of("error", "Rate limited or invalid phone"));
        }

        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body) {
        String phoneNumber = body.get("phoneNumber");
        String code = body.get("code");
        String tenantId = body.getOrDefault("tenantId", TenantContext.get());

        if (phoneNumber == null || code == null || tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "phoneNumber, code, and tenantId required"));
        }

        boolean verified = smsService.verifyCode(phoneNumber, code, tenantId);
        return ResponseEntity.ok(Map.of("verified", verified));
    }
}
