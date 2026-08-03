package io.kelta.worker.service.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;

/**
 * Message encryption for Web Push — RFC 8291 (Message Encryption for Web Push)
 * over RFC 8188 (aes128gcm content encoding).
 *
 * <p><b>Why this is hand-rolled.</b> The obvious libraries pull BouncyCastle,
 * whose reflective surface has to be enumerated in {@code reflect-config.json}
 * for the native worker image or it fails only in production. Everything needed
 * here — P-256 ECDH, HMAC-SHA-256, AES-128-GCM — is in the JDK; the only piece
 * not built in is HKDF (RFC 5869), which is a dozen lines of HMAC.
 *
 * <p>This is implementing a specification, not inventing cryptography, and the
 * distinction only holds if it is checked against the specification's own test
 * vectors — {@code WebPushCryptoTest} does exactly that, using the RFC 8291 §5
 * example. Do not change this class without re-running that test: an encryption
 * bug here does not throw, it just produces payloads every browser silently
 * discards.
 */
final class WebPushCrypto {

    /** RFC 8188 content encoding. */
    private static final byte[] CEK_INFO = infoBytes("Content-Encoding: aes128gcm");
    private static final byte[] NONCE_INFO = infoBytes("Content-Encoding: nonce");
    /** RFC 8291 §3.4 key derivation label. */
    private static final byte[] WEB_PUSH_INFO = infoBytes("WebPush: info");

    private static final int KEY_LENGTH = 16;    // AES-128
    private static final int NONCE_LENGTH = 12;  // GCM
    private static final int SALT_LENGTH = 16;
    private static final int TAG_BITS = 128;
    /** Uncompressed P-256 point: 0x04 || X(32) || Y(32). */
    private static final int P256_POINT_LENGTH = 65;

    private WebPushCrypto() {
    }

    /** A P-256 key pair plus its uncompressed public point. */
    record EphemeralKeys(KeyPair keyPair, byte[] publicPoint) {
    }

    static EphemeralKeys generateEphemeralKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        return new EphemeralKeys(pair, encodePoint((ECPublicKey) pair.getPublic()));
    }

    /**
     * Encrypts a push payload for one subscription.
     *
     * @param plaintext     the message body
     * @param uaPublicKey   the subscription's {@code p256dh}, an uncompressed P-256 point
     * @param authSecret    the subscription's {@code auth}, 16 bytes
     * @param salt          16 random bytes (a parameter so tests can pin the RFC vector)
     * @param ephemeral     the server's ephemeral key pair for this message
     * @return the complete aes128gcm body: salt ‖ rs ‖ idlen ‖ as_public ‖ ciphertext
     */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublicKey, byte[] authSecret,
                          byte[] salt, EphemeralKeys ephemeral, int recordSize) throws Exception {
        if (uaPublicKey == null || uaPublicKey.length != P256_POINT_LENGTH) {
            throw new IllegalArgumentException("p256dh must be a 65-byte uncompressed P-256 point");
        }
        if (authSecret == null || authSecret.length != 16) {
            throw new IllegalArgumentException("auth secret must be 16 bytes");
        }
        if (salt == null || salt.length != SALT_LENGTH) {
            throw new IllegalArgumentException("salt must be 16 bytes");
        }

        byte[] sharedSecret = ecdh(ephemeral.keyPair().getPrivate(), uaPublicKey);

        // RFC 8291 §3.4: the IKM binds both public keys, so a payload encrypted
        // for one subscription cannot be replayed against another.
        byte[] keyInfo = concat(WEB_PUSH_INFO, uaPublicKey, ephemeral.publicPoint());
        byte[] ikm = hkdf(authSecret, sharedSecret, keyInfo, 32);

        byte[] cek = hkdf(salt, ikm, CEK_INFO, KEY_LENGTH);
        byte[] nonce = hkdf(salt, ikm, NONCE_INFO, NONCE_LENGTH);

        // RFC 8188 §2: a single record, so the padding delimiter is 0x02.
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = 0x02;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(salt);
        body.write(ByteBuffer.allocate(4).putInt(recordSize).array());
        body.write(ephemeral.publicPoint().length);
        body.write(ephemeral.publicPoint());
        body.write(ciphertext);
        return body.toByteArray();
    }

    /** Raw ECDH shared secret (the 32-byte X coordinate), per RFC 8291. */
    private static byte[] ecdh(PrivateKey privateKey, byte[] peerPoint) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(decodePoint(peerPoint), true);
        return agreement.generateSecret();
    }

    /**
     * HKDF (RFC 5869) with SHA-256. Not in the JDK as a usable API here, and the
     * whole reason a third-party crypto dependency would otherwise be needed.
     */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");

        // Extract
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);

        // Expand — one round is always enough here (length <= 32).
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        byte[] okm = mac.doFinal();
        return Arrays.copyOf(okm, length);
    }

    /** Encodes a public key as an uncompressed point: 0x04 ‖ X(32) ‖ Y(32). */
    static byte[] encodePoint(ECPublicKey key) {
        byte[] point = new byte[P256_POINT_LENGTH];
        point[0] = 0x04;
        // Left-pad to exactly 32 bytes: BigInteger drops leading zeros and may add
        // a sign byte, either of which corrupts the point.
        copyFixed(key.getW().getAffineX(), point, 1);
        copyFixed(key.getW().getAffineY(), point, 33);
        return point;
    }

    private static void copyFixed(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        int from = bytes.length - length;
        System.arraycopy(bytes, from, target, offset + (32 - length), length);
    }

    /** Decodes an uncompressed P-256 point into a public key. */
    static PublicKey decodePoint(byte[] point) throws Exception {
        if (point.length != P256_POINT_LENGTH || point[0] != 0x04) {
            throw new IllegalArgumentException("expected a 65-byte uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        return KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256Params()));
    }

    /** The P-256 domain parameters, obtained from the JDK rather than hardcoded. */
    static ECParameterSpec p256Params() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return ((ECPublicKey) generator.generateKeyPair().getPublic()).getParams();
    }

    /** RFC 8188 info strings are the label followed by a single zero byte. */
    private static byte[] infoBytes(String label) {
        byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
        return Arrays.copyOf(bytes, bytes.length + 1);
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
