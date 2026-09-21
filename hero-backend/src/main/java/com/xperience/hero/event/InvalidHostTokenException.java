package com.xperience.hero.event;

/**
 * Thrown when a host-only operation (invite, dashboard, close, cancel, ...)
 * is attempted with a missing or incorrect host management token
 * (DESIGN.md Q3 / I9). Mapped to HTTP 401 by the global exception handler.
 *
 * Deliberately generic and reusable across any future host-only operation —
 * not specific to Invitation.
 */
public class InvalidHostTokenException extends RuntimeException {

    public InvalidHostTokenException() {
        super("Invalid or missing host management token");
    }
}
