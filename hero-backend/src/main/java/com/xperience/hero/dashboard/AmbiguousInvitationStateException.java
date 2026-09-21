package com.xperience.hero.dashboard;

/**
 * Thrown when an Invitation has more than one RsvpResponse or
 * AttendanceOutcome row, and no deterministic "current row" rule is
 * settled to resolve which one is authoritative.
 *
 * DESIGN.md Section 8 explicitly leaves this undecided: whether one row
 * per Invitation is DB-enforced, and how updates/history would be
 * represented, "is not decided." Rather than silently picking one (first,
 * last, highest id — all arbitrary), the dashboard refuses to guess and
 * surfaces this instead.
 *
 * Not reachable via any currently implemented write path — no RSVP
 * submission service and no Attendance Outcome creation service exist yet,
 * so no Invitation can have more than one row of either today. This is a
 * defensive guard for if/when those are built without also resolving the
 * schema-level question, not an expected runtime case.
 */
public class AmbiguousInvitationStateException extends RuntimeException {

    public AmbiguousInvitationStateException(Long invitationId, String concept, int rowCount) {
        super("Invitation " + invitationId + " has " + rowCount + " " + concept
                + " rows; no deterministic \"current row\" rule is settled (DESIGN.md Section 8) to resolve this");
    }
}
