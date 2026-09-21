package com.xperience.hero.rsvp;

/**
 * Thrown when any RSVP create/change is attempted on a CANCELLED Event
 * (DESIGN.md Section 4 — Cancel behavior, resolved). Mapped to HTTP 409 by
 * the global exception handler.
 *
 * Existing RSVP/Attendance Outcome data remains readable via the dashboard —
 * this exception blocks writes only, never reads.
 */
public class EventCancelledException extends RuntimeException {

    public EventCancelledException() {
        super("This event has been cancelled; no RSVP may be created or changed");
    }
}
