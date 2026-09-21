package com.xperience.hero.invitation;

/**
 * Thrown when an Invitation cannot be created because one already exists
 * for the same normalized email on the same Event (DESIGN.md Q8 / I13).
 * Mapped to HTTP 409 by the global exception handler.
 *
 * This is deliberately a distinct application exception — a raw
 * DataIntegrityViolationException (from a UNIQUE constraint violation
 * under concurrent requests) must be translated into this, never exposed
 * directly to a caller.
 */
public class DuplicateInvitationException extends RuntimeException {

    public DuplicateInvitationException(String message) {
        super(message);
    }
}
