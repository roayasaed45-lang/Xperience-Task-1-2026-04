package com.xperience.hero.rsvp;

/**
 * The invitee's stated RSVP choice (DESIGN.md I6, Section 8).
 *
 * This represents intention only — it is NOT the Attendance Outcome
 * (Confirmed/Waitlisted/None), which is a separate, system-derived concept
 * not implemented in this slice. Do not add CONFIRMED/WAITLISTED here.
 */
public enum RsvpResponseValue {
    YES,
    NO,
    MAYBE
}
