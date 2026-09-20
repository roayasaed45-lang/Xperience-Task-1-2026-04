package com.xperience.hero.invitation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests InvitationTokenService in isolation — token generation, hashing, and
 * verification. These are properties of the token mechanism itself and are
 * entirely unrelated to DESIGN.md Q8 (duplicate-invitation policy); no
 * invitation-creation workflow is involved or required here.
 */
class InvitationTokenServiceTest {

    private final InvitationTokenService tokenService = new InvitationTokenService();

    @Test
    void generatesNonEmptyToken() {
        String token = tokenService.generateRawToken();

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generatesDifferentTokensEachTime() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            tokens.add(tokenService.generateRawToken());
        }

        assertEquals(20, tokens.size());
    }

    @Test
    void hashDiffersFromRawToken() {
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hashToken(rawToken);

        assertNotEquals(rawToken, hash);
        assertFalse(hash.isBlank());
    }

    @Test
    void correctTokenVerifiesAgainstItsOwnHash() {
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hashToken(rawToken);

        assertTrue(tokenService.verify(rawToken, hash));
    }

    @Test
    void incorrectTokenFailsVerification() {
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hashToken(rawToken);

        assertFalse(tokenService.verify("not-the-real-token", hash));
    }
}
