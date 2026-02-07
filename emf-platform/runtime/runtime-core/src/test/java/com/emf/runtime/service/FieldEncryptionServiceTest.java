package com.emf.runtime.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FieldEncryptionServiceTest {

    private FieldEncryptionService service;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String base64Key = Base64.getEncoder().encodeToString(key);
        service = new FieldEncryptionService(base64Key);
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plaintext = "sensitive data";
        String tenantId = "tenant-123";

        byte[] encrypted = service.encrypt(plaintext, tenantId);
        String decrypted = service.decrypt(encrypted, tenantId);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void differentTenantsProduceDifferentCiphertexts() {
        String plaintext = "same data";
        byte[] encrypted1 = service.encrypt(plaintext, "tenant-1");
        byte[] encrypted2 = service.encrypt(plaintext, "tenant-2");

        assertNotEquals(Base64.getEncoder().encodeToString(encrypted1),
                        Base64.getEncoder().encodeToString(encrypted2));
    }

    @Test
    void wrongTenantCannotDecrypt() {
        String plaintext = "secret";
        byte[] encrypted = service.encrypt(plaintext, "tenant-1");

        assertThrows(RuntimeException.class, () -> service.decrypt(encrypted, "tenant-2"));
    }

    @Test
    void developmentModeWorksWithoutKey() {
        FieldEncryptionService devService = new FieldEncryptionService(null);
        String plaintext = "test data";
        byte[] encrypted = devService.encrypt(plaintext, "tenant-1");
        String decrypted = devService.decrypt(encrypted, "tenant-1");
        assertEquals(plaintext, decrypted);
    }
}
