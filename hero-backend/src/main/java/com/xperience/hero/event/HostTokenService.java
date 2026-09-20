package com.xperience.hero.event;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and verifies the host management token (DESIGN.md Q3 —
 * "Resolved Decisions" / Host Management Token).
 *
 * The raw token is a high-entropy, backend-generated random value. It is
 * returned to the client exactly once, at event creation, and is never
 * stored directly — only a SHA-256 hash of it is persisted
 * (Event.hostTokenHash). The client can never supply or choose the token.
 *
 * First-pass mechanism only: standard JDK APIs (SecureRandom, MessageDigest)
 * exclusively — no external crypto library, no User entity, no session/JWT.
 */
@Component
public class HostTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    /** Generates a fresh, high-entropy raw token. Never called with client input. */
    public String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** One-way SHA-256 hash of a raw token, for storage. */
    public String hashToken(String rawToken) {
        return Base64.getEncoder().encodeToString(digest(rawToken));
    }

    /**
     * Timing-safe comparison of a caller-supplied raw token against a stored
     * hash. Used to verify host-only operations later; not exposed as a
     * public endpoint in this slice.
     */
    public boolean verify(String rawToken, String storedHash) {
        if (rawToken == null || storedHash == null) {
            return false;
        }
        byte[] computed = digest(rawToken);
        byte[] stored;
        try {
            stored = Base64.getDecoder().decode(storedHash);
        } catch (IllegalArgumentException malformedStoredHash) {
            return false;
        }
        return MessageDigest.isEqual(computed, stored);
    }

    private byte[] digest(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            return messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " is not available on this JVM", e);
        }
    }
}
