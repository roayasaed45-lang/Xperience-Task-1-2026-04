package com.xperience.hero.rsvp;

/**
 * Thrown when a brand-new RSVP submission is attempted for an Event whose
 * status is CLOSED (DESIGN.md I4 — settled fact: "new RSVP submissions not
 * accepted once closed"). Mapped to HTTP 409 by the global exception
 * handler.
 *
 * Deliberately NOT thrown for: a change to an existing RSVP on a closed
 * event (DESIGN.md Q14 — unresolved whether Close blocks changes too, not
 * decided here); or any check related to a CANCELLED event (DESIGN.md
 * I5/W13 — fully unresolved, no control asserted). Both gaps are
 * intentional, not oversights.
 */
public class EventClosedException extends RuntimeException {

    public EventClosedException() {
        super("This event is closed to new RSVP responses");
    }
}
