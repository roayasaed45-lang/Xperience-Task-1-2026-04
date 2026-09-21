package com.xperience.hero.rsvp;

/**
 * Thrown when any RSVP create or change is attempted for an Event whose
 * status is CLOSED (DESIGN.md Section 4, Q11/Q14 resolved: CLOSED means
 * RSVP activity is fully closed — both new submissions and changes to
 * existing RSVPs are blocked). Mapped to HTTP 409 by the global exception
 * handler.
 *
 * Existing RSVP/Attendance Outcome data remains readable via the dashboard —
 * this exception blocks writes only, never reads. See EventCancelledException
 * for the equivalent CANCELLED-event case.
 */
public class EventClosedException extends RuntimeException {

    public EventClosedException() {
        super("This event is closed; no RSVP may be created or changed");
    }
}
