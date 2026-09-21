package com.xperience.hero.rsvp;

/**
 * Thrown when an RSVP create/change is attempted at or after the Event's
 * eventDateTime (DESIGN.md I2, resolved first-pass rule — Section 4).
 * Mapped to HTTP 409 by the global exception handler.
 */
public class RsvpLockedException extends RuntimeException {

    public RsvpLockedException() {
        super("RSVP changes are locked: the event has already started");
    }
}
