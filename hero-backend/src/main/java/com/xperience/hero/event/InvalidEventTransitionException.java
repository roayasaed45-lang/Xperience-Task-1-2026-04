package com.xperience.hero.event;

/**
 * Thrown when a requested Event lifecycle transition is not allowed
 * (DESIGN.md Section 4, Q6 resolved): the only valid transitions are
 * OPEN -> CLOSED and OPEN -> CANCELLED; there is no reopen, and an event
 * already CLOSED or CANCELLED cannot be closed/cancelled again or switched
 * to the other status. Mapped to HTTP 409 by the global exception handler.
 */
public class InvalidEventTransitionException extends RuntimeException {

    public InvalidEventTransitionException(EventStatus currentStatus, EventStatus requestedStatus) {
        super("Cannot transition event from " + currentStatus + " to " + requestedStatus);
    }
}
