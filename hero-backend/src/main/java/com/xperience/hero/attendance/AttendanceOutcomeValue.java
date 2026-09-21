package com.xperience.hero.attendance;

/**
 * The system-derived attendance outcome for an invitee (DESIGN.md Section 8,
 * I1, I3, I6). This is NOT the RSVP Response (Yes/No/Maybe) — it is a
 * separate concept, computed from Response plus capacity state, and must
 * remain strictly distinct from it (I6).
 *
 * Deliberately not represented anywhere: waitlist position/ordering,
 * promotion timestamps, capacity counters, or any derived count — all of
 * that requires capacity/waitlist logic and depends on unresolved questions
 * (Q1, Q7, Q13), none of which are decided here.
 *
 * No persistence entity exists yet for this value — see the corresponding
 * slice report for why: DESIGN.md does not settle whether Attendance
 * Outcome is keyed to Invitation or to RsvpResponse, and that relationship
 * is intentionally not invented.
 */
public enum AttendanceOutcomeValue {
    CONFIRMED,
    WAITLISTED,
    NONE
}
