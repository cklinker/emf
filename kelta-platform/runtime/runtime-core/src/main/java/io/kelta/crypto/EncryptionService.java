package io.kelta.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides AES-256-GCM envelope encryption for sensitive values stored in the database.
 *
 * <p>Encrypted values are stored in the format: {@code enc:v1:<iv>:<ciphertext>}
 * where iv and ciphertext are Base64-encoded. The version prefix ({@code v1}) supports
 * future key rotation by allowing different decryption strategies per version.
 *
 * <p>The master key is provided externally (e.g., from a K8s Secret or environment variable)
 * and must be a 32-byte (256-bit) key encoded as Base64.
 */
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String PREFIX = "enc:v1:";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionService(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be null or blank");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Encryption key must be exactly 32 bytes (256 bits), got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts a plaintext value using AES-256-GCM.
     *
     * @param plaintext the value to encrypt
     * @return encrypted value in format {@code enc:v1:<iv>:<ciphertext>}
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts a value previously encrypted by {@link #encrypt(String)}.
     *
     * @param encryptedValue the encrypted value in format {@code enc:v1:<iv>:<ciphertext>}
     * @return the original plaintext
     * @throws EncryptionException if decryption fails or the format is invalid
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        if (!isEncrypted(encryptedValue)) {
            throw new EncryptionException("Value is not in encrypted format: expected prefix '" + PREFIX + "'");
        }

        try {
            String payload = encryptedValue.substring(PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                throw new EncryptionException("Invalid encrypted value format: expected <iv>:<ciphertext>");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }

    /**
     * Checks whether a value is in encrypted format.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
