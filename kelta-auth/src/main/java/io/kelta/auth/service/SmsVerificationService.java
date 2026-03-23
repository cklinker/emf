package io.kelta.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Delegates SMS verification to the worker service via WorkerClient.
 *
 * @since 1.0.0
 */
@Service
public class SmsVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SmsVerificationService.class);

    private final WorkerClient workerClient;

    public SmsVerificationService(WorkerClient workerClient) {
        this.workerClient = workerClient;
    }

    /**
     * Sends a verification code to the given phone number.
     *
     * @return true if sent, false if rate limited or error
     */
    public boolean sendCode(String phoneNumber, String tenantId) {
        try {
            return workerClient.sendSmsCode(phoneNumber, tenantId);
        } catch (Exception e) {
            log.error("Failed to send SMS code: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies a code against the worker's SMS verification store.
     *
     * @return true if code matches
     */
    public boolean verifyCode(String phoneNumber, String code, String tenantId) {
        try {
            return workerClient.verifySmsCode(phoneNumber, code, tenantId);
        } catch (Exception e) {
            log.error("Failed to verify SMS code: {}", e.getMessage());
            return false;
        }
    }
}
