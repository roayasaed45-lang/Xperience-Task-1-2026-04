package com.xperience.hero.invitation;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and verifies the invitation token for an Invitation (DESIGN.md
 * I7 — Invitation Scope).
 *
 * This is a completely separate concept from the event's host management
 * token (Q3) and deliberately shares no code with HostTokenService, so the
 * two token types remain unmistakably distinct.
 *
 * The raw token is a high-entropy, backend-generated random value, intended
 * to eventually be delivered to the invitee (delivery is not implemented in
 * this slice) and never stored directly — only a SHA-256 hash is persisted
 * (Invitation.invitationTokenHash).
 *
 * Token expiry is intentionally not implemented — DESIGN.md Q9 (do
 * invitation links expire?) is unresolved.
 */
@Component
public class InvitationTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    /** Generates a fresh, high-entropy raw invitation token. */
    public String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /** One-way SHA-256 hash of a raw invitation token, for storage. */
    public String hashToken(String rawToken) {
        return Base64.getEncoder().encodeToString(digest(rawToken));
    }

    /**
     * Timing-safe comparison of a caller-supplied raw invitation token
     * against a stored hash.
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
