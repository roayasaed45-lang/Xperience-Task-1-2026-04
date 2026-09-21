package com.xperience.hero.dashboard;

import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.rsvp.RsvpResponseValue;

/**
 * One invitee's row on the host dashboard. Contains only currently
 * persisted data — no invitation token, token hash, or other secret.
 * rsvpResponse and attendanceOutcome are null when no row exists yet for
 * that Invitation (no RSVP submission workflow exists in this slice).
 */
public record InvitationDashboardRow(
        Long invitationId,
        String inviteeEmail,
        RsvpResponseValue rsvpResponse,
        AttendanceOutcomeValue attendanceOutcome
) {
}
