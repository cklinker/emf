package io.kelta.worker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Shared helpers for {@code portal_login_token} issuance (telehealth slices
 * 1/4): 256-bit URL-safe raw tokens and the SHA-256 hex hashing that is the
 * ONLY form ever stored. Kept tiny and static so every issuer (portal invites,
 * magic-link requests, visit links) produces identical token material.
 */
public final class PortalTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PortalTokens() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
