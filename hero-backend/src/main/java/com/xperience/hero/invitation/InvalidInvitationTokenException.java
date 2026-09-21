package com.xperience.hero.invitation;

/**
 * Thrown when an RSVP-scoped operation is attempted with a missing or
 * incorrect Invitation token (DESIGN.md Q2 / I10). Mapped to HTTP 401 by
 * the global exception handler.
 *
 * Deliberately generic: does not reveal whether the token was malformed,
 * unrecognized, or simply absent — all collapse to the same outcome, per
 * the same principle already applied to InvalidHostTokenException.
 */
public class InvalidInvitationTokenException extends RuntimeException {

    public InvalidInvitationTokenException() {
        super("Invalid or missing invitation token");
    }
}
