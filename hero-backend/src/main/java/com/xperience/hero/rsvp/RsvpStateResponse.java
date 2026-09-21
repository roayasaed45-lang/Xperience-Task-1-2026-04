package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcomeValue;

/**
 * The invitee-facing result of an RSVP submission/change: their own current
 * Response and Attendance Outcome. Contains no token, hash, or any other
 * invitee/host identity material.
 */
public record RsvpStateResponse(
        Long invitationId,
        Long eventId,
        RsvpResponseValue response,
        AttendanceOutcomeValue attendanceOutcome) {
}
