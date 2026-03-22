package io.kelta.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private String testKey;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        testKey = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
        encryptionService = new EncryptionService(testKey);
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        String plaintext = "my-secret-client-secret-12345";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals(plaintext, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_producesUniqueIVs() {
        String plaintext = "same-value";
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        assertNotEquals(encrypted1, encrypted2, "Each encryption should use a unique IV");

        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }

    @Test
    void encrypt_nullReturnsNull() {
        assertNull(encryptionService.encrypt(null));
    }

    @Test
    void decrypt_nullReturnsNull() {
        assertNull(encryptionService.decrypt(null));
    }

    @Test
    void decrypt_nonEncryptedValueThrows() {
        assertThrows(EncryptionException.class, () ->
                encryptionService.decrypt("not-encrypted"));
    }

    @Test
    void decrypt_tamperedCiphertextThrows() {
        String encrypted = encryptionService.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "XX";

        assertThrows(EncryptionException.class, () ->
                encryptionService.decrypt(tampered));
    }

    @Test
    void decrypt_wrongKeyThrows() throws NoSuchAlgorithmException {
        String encrypted = encryptionService.encrypt("secret");

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        String differentKey = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
        EncryptionService otherService = new EncryptionService(differentKey);

        assertThrows(EncryptionException.class, () ->
                otherService.decrypt(encrypted));
    }

    @Test
    void constructor_rejectsNullKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new EncryptionService(null));
    }

    @Test
    void constructor_rejectsBlankKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new EncryptionService(""));
    }

    @Test
    void constructor_rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class, () ->
                new EncryptionService(shortKey));
    }

    @Test
    void isEncrypted_detectsEncryptedValues() {
        assertTrue(EncryptionService.isEncrypted("enc:v1:abc:def"));
        assertFalse(EncryptionService.isEncrypted("plaintext"));
        assertFalse(EncryptionService.isEncrypted(null));
        assertFalse(EncryptionService.isEncrypted(""));
    }

    @Test
    void encryptAndDecrypt_handlesEmptyString() {
        String encrypted = encryptionService.encrypt("");
        assertEquals("", encryptionService.decrypt(encrypted));
    }

    @Test
    void encryptAndDecrypt_handlesSpecialCharacters() {
        String plaintext = "p@$$w0rd!#%^&*()_+-={}[]|\\:\";<>?,./~`";
        String encrypted = encryptionService.encrypt(plaintext);
        assertEquals(plaintext, encryptionService.decrypt(encrypted));
    }

    @Test
    void encryptAndDecrypt_handlesUnicode() {
        String plaintext = "密码🔐пароль";
        String encrypted = encryptionService.encrypt(plaintext);
        assertEquals(plaintext, encryptionService.decrypt(encrypted));
    }
}
