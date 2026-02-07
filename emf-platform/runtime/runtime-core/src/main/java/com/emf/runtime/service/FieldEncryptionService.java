package com.emf.runtime.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Application-layer encryption for ENCRYPTED field type using AES-256-GCM
 * with per-tenant key derivation (HKDF-like via HMAC-SHA256).
 */
@Service
public class FieldEncryptionService {

    private final SecretKey masterKey;

    public FieldEncryptionService(
            @Value("${emf.encryption.master-key:#{null}}") String masterKeyBase64) {
        if (masterKeyBase64 != null && !masterKeyBase64.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
        } else {
            // Generate a random key for development/testing (not for production)
            byte[] keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    /**
     * Derives a per-tenant encryption key using HMAC-SHA256.
     */
    private SecretKey deriveTenantKey(String tenantId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(masterKey);
            byte[] derived = mac.doFinal(tenantId.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive tenant key", e);
        }
    }

    /**
     * Encrypts a plaintext value for storage.
     *
     * @return byte array containing IV + ciphertext
     */
    public byte[] encrypt(String plaintext, String tenantId) {
        try {
            SecretKey tenantKey = deriveTenantKey(tenantId);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, tenantKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a stored value.
     */
    public String decrypt(byte[] encryptedData, String tenantId) {
        try {
            SecretKey tenantKey = deriveTenantKey(tenantId);
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(encryptedData, 12, encryptedData.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, tenantKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
